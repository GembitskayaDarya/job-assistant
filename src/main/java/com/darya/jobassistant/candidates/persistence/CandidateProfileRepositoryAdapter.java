package com.darya.jobassistant.candidates.persistence;

import com.darya.jobassistant.candidates.CandidateProfileConcurrentModificationException;
import com.darya.jobassistant.candidates.CandidateProfileRepositoryPort;
import com.darya.jobassistant.candidates.PersistedCandidateLanguage;
import com.darya.jobassistant.candidates.PersistedCandidateProfile;
import com.darya.jobassistant.candidates.PersistedCandidateSkill;
import com.darya.jobassistant.candidates.entity.CandidateProfileEntity;
import com.darya.jobassistant.candidates.entity.CandidateProfileLanguageEntity;
import com.darya.jobassistant.candidates.entity.CandidateProfileSkillEntity;
import com.darya.jobassistant.candidates.repository.CandidateProfileLanguageRepository;
import com.darya.jobassistant.candidates.repository.CandidateProfileRepository;
import com.darya.jobassistant.candidates.repository.CandidateProfileSkillRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL/JPA adapter for {@link CandidateProfileRepositoryPort} - Sprint 9 Step 2. Not wired
 * into any runtime workflow: {@code ConfigurationCandidateProfileProvider} remains the active
 * source AI vacancy analysis reads from (see {@code CandidateProfileProvider}). This bean exists
 * purely as persistence access; nothing currently injects {@link CandidateProfileRepositoryPort}.
 *
 * <p>Skills and languages are queried directly by {@code candidate_profile_id} rather than
 * through a collection on the parent - Step 1's entities are unidirectional {@code @ManyToOne}
 * (see {@code CandidateProfileEntity}'s javadoc), so there is no lazy parent-side collection that
 * could leak a proxy across this boundary.
 */
@Repository
@RequiredArgsConstructor
public class CandidateProfileRepositoryAdapter implements CandidateProfileRepositoryPort {

    private final CandidateProfileRepository candidateProfileRepository;
    private final CandidateProfileSkillRepository candidateProfileSkillRepository;
    private final CandidateProfileLanguageRepository candidateProfileLanguageRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<PersistedCandidateProfile> findByProfileKey(String profileKey) {
        return candidateProfileRepository.findByProfileKey(profileKey).map(this::loadComplete);
    }

    /**
     * Saves the parent first (flushing immediately - see {@link #saveParent}, so a stale version
     * is caught before any child row is touched, satisfying "a failed stale update does not
     * modify skills or languages"), then fully replaces the skill and language sets. Everything
     * runs inside this one transaction: a failure anywhere rolls back the parent update together
     * with any child mutation already attempted.
     */
    @Override
    @Transactional
    public PersistedCandidateProfile save(PersistedCandidateProfile profile) {
        CandidateProfileEntity savedParent = saveParent(profile);
        replaceSkills(savedParent, profile.skills());
        replaceLanguages(savedParent, profile.languages());
        return loadComplete(savedParent);
    }

    /**
     * A {@code null} {@link PersistedCandidateProfile#id()} means "not yet persisted" - creates a
     * new row via a plain insert. A non-null id means "update" - builds a detached entity
     * carrying the caller-supplied (possibly stale) {@link PersistedCandidateProfile#version()}
     * and merges it, so Hibernate's own {@code @Version} check runs against exactly the revision
     * the caller last observed rather than whatever is current at merge time; loading the current
     * row first and overwriting its version field would defeat the optimistic-lock check instead
     * of honoring it (see {@link CandidateProfilePersistenceMapper#toDetachedEntityForUpdate}).
     * Flushed immediately so a stale-version conflict surfaces here, not after child work.
     */
    private CandidateProfileEntity saveParent(PersistedCandidateProfile profile) {
        CandidateProfileEntity toSave = profile.id() == null
                ? CandidateProfilePersistenceMapper.toNewEntity(profile)
                : CandidateProfilePersistenceMapper.toDetachedEntityForUpdate(profile);
        try {
            CandidateProfileEntity saved = candidateProfileRepository.save(toSave);
            candidateProfileRepository.flush();
            return saved;
        } catch (OptimisticLockingFailureException e) {
            throw concurrentModification(profile, e);
        }
    }

    /**
     * Full replace: every existing skill row for this profile is deleted and flushed before the
     * caller's supplied set is inserted. The intermediate flush is required, not cosmetic -
     * without it, Hibernate's default flush ordering can execute the inserts before the deletes
     * within the same flush, which would self-collide on {@code
     * uk_candidate_profile_skill_profile_id_skill_name} for any skill name that is unchanged
     * across the replace. A diff-based sync is not attempted; see {@link
     * CandidateProfileRepositoryPort} - the expected collections are small enough that this is an
     * acceptable, explicit write-amplification trade-off.
     */
    private void replaceSkills(CandidateProfileEntity profile, List<PersistedCandidateSkill> skills) {
        candidateProfileSkillRepository.deleteAll(candidateProfileSkillRepository.findByCandidateProfileId(profile.getId()));
        candidateProfileSkillRepository.flush();
        List<CandidateProfileSkillEntity> toInsert = skills.stream()
                .map(skill -> CandidateProfilePersistenceMapper.toSkillEntity(skill, profile))
                .toList();
        candidateProfileSkillRepository.saveAll(toInsert);
    }

    /** Same delete-then-insert strategy and flush-ordering rationale as {@link #replaceSkills}. */
    private void replaceLanguages(CandidateProfileEntity profile, List<PersistedCandidateLanguage> languages) {
        candidateProfileLanguageRepository.deleteAll(
                candidateProfileLanguageRepository.findByCandidateProfileId(profile.getId()));
        candidateProfileLanguageRepository.flush();
        List<CandidateProfileLanguageEntity> toInsert = languages.stream()
                .map(language -> CandidateProfilePersistenceMapper.toLanguageEntity(language, profile))
                .toList();
        candidateProfileLanguageRepository.saveAll(toInsert);
    }

    private PersistedCandidateProfile loadComplete(CandidateProfileEntity entity) {
        List<CandidateProfileSkillEntity> skills = candidateProfileSkillRepository.findByCandidateProfileId(entity.getId());
        List<CandidateProfileLanguageEntity> languages = candidateProfileLanguageRepository.findByCandidateProfileId(entity.getId());
        return CandidateProfilePersistenceMapper.toDomain(entity, skills, languages);
    }

    private CandidateProfileConcurrentModificationException concurrentModification(
            PersistedCandidateProfile profile, OptimisticLockingFailureException cause) {
        return new CandidateProfileConcurrentModificationException(
                "Candidate profile '" + profile.profileKey() + "' (id=" + profile.id()
                        + ") was concurrently modified by another transaction",
                cause);
    }
}
