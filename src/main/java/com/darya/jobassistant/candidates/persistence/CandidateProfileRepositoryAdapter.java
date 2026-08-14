package com.darya.jobassistant.candidates.persistence;

import com.darya.jobassistant.candidates.aggregate.CandidateEducation;
import com.darya.jobassistant.candidates.aggregate.CandidateLanguage;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileAggregate;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileConcurrentModificationException;
import com.darya.jobassistant.candidates.aggregate.CandidateProfilePreference;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileRepositoryPort;
import com.darya.jobassistant.candidates.aggregate.CandidateSkill;
import com.darya.jobassistant.candidates.entity.CandidateProfileEducationEntity;
import com.darya.jobassistant.candidates.entity.CandidateProfileEntity;
import com.darya.jobassistant.candidates.entity.CandidateProfileLanguageEntity;
import com.darya.jobassistant.candidates.entity.CandidateProfilePreferenceEntity;
import com.darya.jobassistant.candidates.entity.CandidateProfileSkillEntity;
import com.darya.jobassistant.candidates.repository.CandidateProfileEducationRepository;
import com.darya.jobassistant.candidates.repository.CandidateProfileLanguageRepository;
import com.darya.jobassistant.candidates.repository.CandidateProfilePreferenceRepository;
import com.darya.jobassistant.candidates.repository.CandidateProfileRepository;
import com.darya.jobassistant.candidates.repository.CandidateProfileSkillRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL/JPA adapter for {@link CandidateProfileRepositoryPort} - Sprint 9 Step 2, extended by
 * Step 3 with preferences. Not wired into any runtime workflow: {@code
 * ConfigurationCandidateProfileProvider} remains the active source AI vacancy analysis reads from
 * (see {@code CandidateProfileProvider}). This bean exists purely as persistence access; nothing
 * currently injects {@link CandidateProfileRepositoryPort}.
 *
 * <p>Skills, languages, and preferences are queried directly by {@code candidate_profile_id}
 * rather than through a collection on the parent - Step 1's entities are unidirectional {@code
 * @ManyToOne} (see {@code CandidateProfileEntity}'s javadoc), so there is no lazy parent-side
 * collection that could leak a proxy across this boundary.
 *
 * <p><b>Aggregate-level optimistic locking (Sprint 9 Step 2 correction):</b> an update always
 * performs {@link CandidateProfileRepository#updateIfVersionMatches}, an explicit, unconditional
 * version-checked write of every parent scalar field, regardless of whether any of them actually
 * changed. Skills, languages, and preferences are all part of this aggregate, so a save that
 * changes only one of them must still increment {@code version} and still be rejected when stale -
 * relying on Hibernate's ordinary scalar-field dirty checking would not guarantee either.
 */
@Repository
@RequiredArgsConstructor
public class CandidateProfileRepositoryAdapter implements CandidateProfileRepositoryPort {

    private final CandidateProfileRepository candidateProfileRepository;
    private final CandidateProfileSkillRepository candidateProfileSkillRepository;
    private final CandidateProfileLanguageRepository candidateProfileLanguageRepository;
    private final CandidateProfilePreferenceRepository candidateProfilePreferenceRepository;
    private final CandidateProfileEducationRepository candidateProfileEducationRepository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public Optional<CandidateProfileAggregate> findByProfileKey(String profileKey) {
        return candidateProfileRepository.findByProfileKey(profileKey).map(this::loadComplete);
    }

    /**
     * Saves the parent first - see {@link #saveParent}; a {@code @Modifying} query executes
     * immediately when called, so a stale version is caught before any child row is touched,
     * satisfying "a failed stale update does not modify skills, languages, or preferences" - then
     * fully replaces the skill, language, and preference sets. Everything runs inside this one
     * transaction: a failure anywhere rolls back the parent update together with any child
     * mutation already attempted.
     */
    @Override
    @Transactional
    public CandidateProfileAggregate save(CandidateProfileAggregate profile) {
        CandidateProfileEntity savedParent = saveParent(profile);
        replaceSkills(savedParent, profile.skills());
        replaceLanguages(savedParent, profile.languages());
        replacePreferences(savedParent, profile.preferences());
        replaceEducation(savedParent, profile.education());
        return loadComplete(savedParent);
    }

    /**
     * A {@code null} {@link CandidateProfileAggregate#id()} means "not yet persisted" - creates a
     * new row via a plain insert. A non-null id means "update" - performs an explicit,
     * unconditional version-checked write of every parent scalar field via {@link
     * CandidateProfileRepository#updateIfVersionMatches}, using the caller-supplied {@link
     * CandidateProfileAggregate#version()} as the expected version. Zero rows updated means the
     * row either no longer exists or {@code expectedVersion} is stale - either way, translated to
     * {@link CandidateProfileConcurrentModificationException} before any child row is touched.
     */
    private CandidateProfileEntity saveParent(CandidateProfileAggregate profile) {
        if (profile.id() == null) {
            return candidateProfileRepository.save(CandidateProfilePersistenceMapper.toNewEntity(profile));
        }
        int updatedRows = candidateProfileRepository.updateIfVersionMatches(
                profile.id(),
                profile.profileKey(),
                profile.targetRole(),
                profile.seniority(),
                profile.experienceYears(),
                profile.preferredCompanyType(),
                profile.preferredLocation(),
                profile.employmentModel(),
                profile.remotePolicy(),
                profile.salaryCurrency(),
                profile.minimumSalary(),
                profile.currentCountry(),
                profile.relocationAllowed(),
                profile.salaryExpectationNote(),
                profile.email(),
                profile.phone(),
                profile.linkedinUrl(),
                profile.cvLocation(),
                profile.cvHeadline(),
                Instant.now(clock),
                profile.version());
        if (updatedRows == 0) {
            throw concurrentModification(profile);
        }
        // Re-fetched rather than reused from before the update: the modifying query above is a
        // bulk JPQL statement, so it never populates or refreshes any managed entity instance
        // itself - this is the first (and only) point where a real, current CandidateProfileEntity
        // for this id becomes available in this transaction.
        return candidateProfileRepository.findById(profile.id())
                .orElseThrow(() -> concurrentModification(profile));
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
    private void replaceSkills(CandidateProfileEntity profile, List<CandidateSkill> skills) {
        candidateProfileSkillRepository.deleteAll(candidateProfileSkillRepository.findByCandidateProfileId(profile.getId()));
        candidateProfileSkillRepository.flush();
        List<CandidateProfileSkillEntity> toInsert = skills.stream()
                .map(skill -> CandidateProfilePersistenceMapper.toSkillEntity(skill, profile))
                .toList();
        candidateProfileSkillRepository.saveAll(toInsert);
    }

    /** Same delete-then-insert strategy and flush-ordering rationale as {@link #replaceSkills}. */
    private void replaceLanguages(CandidateProfileEntity profile, List<CandidateLanguage> languages) {
        candidateProfileLanguageRepository.deleteAll(
                candidateProfileLanguageRepository.findByCandidateProfileId(profile.getId()));
        candidateProfileLanguageRepository.flush();
        List<CandidateProfileLanguageEntity> toInsert = languages.stream()
                .map(language -> CandidateProfilePersistenceMapper.toLanguageEntity(language, profile))
                .toList();
        candidateProfileLanguageRepository.saveAll(toInsert);
    }

    /** Same delete-then-insert strategy and flush-ordering rationale as {@link #replaceSkills}. */
    private void replacePreferences(CandidateProfileEntity profile, List<CandidateProfilePreference> preferences) {
        candidateProfilePreferenceRepository.deleteAll(
                candidateProfilePreferenceRepository.findByCandidateProfileId(profile.getId()));
        candidateProfilePreferenceRepository.flush();
        List<CandidateProfilePreferenceEntity> toInsert = preferences.stream()
                .map(preference -> CandidateProfilePersistenceMapper.toPreferenceEntity(preference, profile))
                .toList();
        candidateProfilePreferenceRepository.saveAll(toInsert);
    }

    /** Same delete-then-insert strategy and flush-ordering rationale as {@link #replaceSkills}. */
    private void replaceEducation(CandidateProfileEntity profile, List<CandidateEducation> education) {
        candidateProfileEducationRepository.deleteAll(
                candidateProfileEducationRepository.findByCandidateProfileId(profile.getId()));
        candidateProfileEducationRepository.flush();
        List<CandidateProfileEducationEntity> toInsert = education.stream()
                .map(entry -> CandidateProfilePersistenceMapper.toEducationEntity(entry, profile))
                .toList();
        candidateProfileEducationRepository.saveAll(toInsert);
    }

    private CandidateProfileAggregate loadComplete(CandidateProfileEntity entity) {
        List<CandidateProfileSkillEntity> skills = candidateProfileSkillRepository.findByCandidateProfileId(entity.getId());
        List<CandidateProfileLanguageEntity> languages =
                candidateProfileLanguageRepository.findByCandidateProfileIdOrderByDisplayOrderAscIdAsc(entity.getId());
        List<CandidateProfilePreferenceEntity> preferences =
                candidateProfilePreferenceRepository.findByCandidateProfileIdOrderByPriorityOrderAscIdAsc(entity.getId());
        List<CandidateProfileEducationEntity> education =
                candidateProfileEducationRepository.findByCandidateProfileIdOrderByDisplayOrderAscIdAsc(entity.getId());
        return CandidateProfilePersistenceMapper.toDomain(entity, skills, languages, preferences, education);
    }

    private CandidateProfileConcurrentModificationException concurrentModification(CandidateProfileAggregate profile) {
        return new CandidateProfileConcurrentModificationException(
                "Candidate profile '" + profile.profileKey() + "' (id=" + profile.id()
                        + ") was concurrently modified by another transaction - expected version "
                        + profile.version() + " no longer matches");
    }
}
