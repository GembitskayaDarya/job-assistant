package com.darya.jobassistant.candidates.persistence;

import com.darya.jobassistant.candidates.PersistedCandidateLanguage;
import com.darya.jobassistant.candidates.PersistedCandidateProfile;
import com.darya.jobassistant.candidates.PersistedCandidateSkill;
import com.darya.jobassistant.candidates.entity.CandidateProfileEntity;
import com.darya.jobassistant.candidates.entity.CandidateProfileLanguageEntity;
import com.darya.jobassistant.candidates.entity.CandidateProfileSkillEntity;
import java.util.List;

/**
 * Stateless entity &lt;-&gt; domain mapping for the Candidate Profile aggregate. No repository
 * calls, no transaction management - {@link CandidateProfileRepositoryAdapter} owns both.
 */
final class CandidateProfilePersistenceMapper {

    private CandidateProfilePersistenceMapper() {
    }

    static PersistedCandidateProfile toDomain(
            CandidateProfileEntity entity,
            List<CandidateProfileSkillEntity> skillEntities,
            List<CandidateProfileLanguageEntity> languageEntities) {
        return new PersistedCandidateProfile(
                entity.getId(),
                entity.getProfileKey(),
                entity.getTargetRole(),
                entity.getSeniority(),
                entity.getExperienceYears(),
                entity.getPreferredCompanyType(),
                entity.getPreferredLocation(),
                entity.getEmploymentModel(),
                entity.getRemotePolicy(),
                entity.getSalaryCurrency(),
                entity.getMinimumSalary(),
                skillEntities.stream().map(CandidateProfilePersistenceMapper::toDomainSkill).toList(),
                languageEntities.stream().map(CandidateProfilePersistenceMapper::toDomainLanguage).toList(),
                entity.getVersion());
    }

    private static PersistedCandidateSkill toDomainSkill(CandidateProfileSkillEntity entity) {
        return new PersistedCandidateSkill(entity.getSkillName(), entity.getCategory(), entity.getProficiency());
    }

    private static PersistedCandidateLanguage toDomainLanguage(CandidateProfileLanguageEntity entity) {
        return new PersistedCandidateLanguage(entity.getLanguageCode(), entity.getProficiency());
    }

    /** A brand-new, not-yet-persisted parent row - id left null so Hibernate generates one. */
    static CandidateProfileEntity toNewEntity(PersistedCandidateProfile profile) {
        CandidateProfileEntity entity = new CandidateProfileEntity();
        applyScalarFields(entity, profile);
        return entity;
    }

    /**
     * A detached instance representing the caller's desired state for an update, carrying {@code
     * profile.version()} - not the database's current value - so that merging it performs the
     * optimistic-lock check against exactly the version the caller last observed, rather than
     * against whatever happens to be current at merge time.
     */
    static CandidateProfileEntity toDetachedEntityForUpdate(PersistedCandidateProfile profile) {
        CandidateProfileEntity entity = new CandidateProfileEntity();
        entity.setId(profile.id());
        entity.setVersion(profile.version());
        applyScalarFields(entity, profile);
        return entity;
    }

    private static void applyScalarFields(CandidateProfileEntity entity, PersistedCandidateProfile profile) {
        entity.setProfileKey(profile.profileKey());
        entity.setTargetRole(profile.targetRole());
        entity.setSeniority(profile.seniority());
        entity.setExperienceYears(profile.experienceYears());
        entity.setPreferredCompanyType(profile.preferredCompanyType());
        entity.setPreferredLocation(profile.preferredLocation());
        entity.setEmploymentModel(profile.employmentModel());
        entity.setRemotePolicy(profile.remotePolicy());
        entity.setSalaryCurrency(profile.salaryCurrency());
        entity.setMinimumSalary(profile.minimumSalary());
    }

    static CandidateProfileSkillEntity toSkillEntity(PersistedCandidateSkill skill, CandidateProfileEntity profile) {
        return CandidateProfileSkillEntity.builder()
                .candidateProfile(profile)
                .skillName(skill.name())
                .category(skill.category())
                .proficiency(skill.proficiency())
                .build();
    }

    static CandidateProfileLanguageEntity toLanguageEntity(PersistedCandidateLanguage language, CandidateProfileEntity profile) {
        return CandidateProfileLanguageEntity.builder()
                .candidateProfile(profile)
                .languageCode(language.languageCode())
                .proficiency(language.proficiency())
                .build();
    }
}
