package com.darya.jobassistant.candidates.persistence;

import com.darya.jobassistant.candidates.aggregate.CandidateEducation;
import com.darya.jobassistant.candidates.aggregate.CandidateLanguage;
import com.darya.jobassistant.candidates.aggregate.CandidateProfileAggregate;
import com.darya.jobassistant.candidates.aggregate.CandidateProfilePreference;
import com.darya.jobassistant.candidates.aggregate.CandidateSkill;
import com.darya.jobassistant.candidates.entity.CandidateProfileEducationEntity;
import com.darya.jobassistant.candidates.entity.CandidateProfileEntity;
import com.darya.jobassistant.candidates.entity.CandidateProfileLanguageEntity;
import com.darya.jobassistant.candidates.entity.CandidateProfilePreferenceEntity;
import com.darya.jobassistant.candidates.entity.CandidateProfileSkillEntity;
import java.util.List;

/**
 * Stateless entity &lt;-&gt; domain mapping for the Candidate Profile aggregate. No repository
 * calls, no transaction management - {@link CandidateProfileRepositoryAdapter} owns both.
 */
final class CandidateProfilePersistenceMapper {

    private CandidateProfilePersistenceMapper() {
    }

    static CandidateProfileAggregate toDomain(
            CandidateProfileEntity entity,
            List<CandidateProfileSkillEntity> skillEntities,
            List<CandidateProfileLanguageEntity> languageEntities,
            List<CandidateProfilePreferenceEntity> preferenceEntities,
            List<CandidateProfileEducationEntity> educationEntities) {
        return new CandidateProfileAggregate(
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
                entity.getCurrentCountry(),
                entity.isRelocationAllowed(),
                entity.getSalaryExpectationNote(),
                entity.getEmail(),
                entity.getPhone(),
                entity.getLinkedinUrl(),
                entity.getCvLocation(),
                entity.getCvHeadline(),
                skillEntities.stream().map(CandidateProfilePersistenceMapper::toDomainSkill).toList(),
                languageEntities.stream().map(CandidateProfilePersistenceMapper::toDomainLanguage).toList(),
                preferenceEntities.stream().map(CandidateProfilePersistenceMapper::toDomainPreference).toList(),
                educationEntities.stream().map(CandidateProfilePersistenceMapper::toDomainEducation).toList(),
                entity.getVersion());
    }

    private static CandidateSkill toDomainSkill(CandidateProfileSkillEntity entity) {
        return new CandidateSkill(
                entity.getId(), entity.getSkillName(), entity.getCategory(), entity.getNote(), entity.getProficiency());
    }

    private static CandidateLanguage toDomainLanguage(CandidateProfileLanguageEntity entity) {
        return new CandidateLanguage(entity.getLanguageCode(), entity.getProficiency(), entity.getDisplayOrder());
    }

    private static CandidateProfilePreference toDomainPreference(CandidateProfilePreferenceEntity entity) {
        return new CandidateProfilePreference(entity.getType(), entity.getValue(), entity.getImportance(), entity.getPriorityOrder());
    }

    private static CandidateEducation toDomainEducation(CandidateProfileEducationEntity entity) {
        return new CandidateEducation(
                entity.getId(), entity.getInstitution(), entity.getDegree(), entity.getFieldOfStudy(), entity.getLocation(),
                entity.getStartDate(), entity.getEndDate(), entity.getDescription(), entity.getDisplayOrder());
    }

    /** A brand-new, not-yet-persisted parent row - id left null so Hibernate generates one. */
    static CandidateProfileEntity toNewEntity(CandidateProfileAggregate profile) {
        CandidateProfileEntity entity = new CandidateProfileEntity();
        applyScalarFields(entity, profile);
        return entity;
    }

    private static void applyScalarFields(CandidateProfileEntity entity, CandidateProfileAggregate profile) {
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
        entity.setCurrentCountry(profile.currentCountry());
        entity.setRelocationAllowed(profile.relocationAllowed());
        entity.setSalaryExpectationNote(profile.salaryExpectationNote());
        entity.setEmail(profile.email());
        entity.setPhone(profile.phone());
        entity.setLinkedinUrl(profile.linkedinUrl());
        entity.setCvLocation(profile.cvLocation());
        entity.setCvHeadline(profile.cvHeadline());
    }

    static CandidateProfileSkillEntity toSkillEntity(CandidateSkill skill, CandidateProfileEntity profile) {
        return CandidateProfileSkillEntity.builder()
                .candidateProfile(profile)
                .skillName(skill.name())
                .category(skill.category())
                .note(skill.note())
                .proficiency(skill.proficiency())
                .build();
    }

    static CandidateProfileLanguageEntity toLanguageEntity(CandidateLanguage language, CandidateProfileEntity profile) {
        return CandidateProfileLanguageEntity.builder()
                .candidateProfile(profile)
                .languageCode(language.languageCode())
                .proficiency(language.proficiency())
                .displayOrder(language.displayOrder())
                .build();
    }

    static CandidateProfilePreferenceEntity toPreferenceEntity(CandidateProfilePreference preference, CandidateProfileEntity profile) {
        return CandidateProfilePreferenceEntity.builder()
                .candidateProfile(profile)
                .type(preference.type())
                .value(preference.value())
                .importance(preference.importance())
                .priorityOrder(preference.priorityOrder())
                .build();
    }

    static CandidateProfileEducationEntity toEducationEntity(CandidateEducation education, CandidateProfileEntity profile) {
        return CandidateProfileEducationEntity.builder()
                .candidateProfile(profile)
                .institution(education.institution())
                .degree(education.degree())
                .fieldOfStudy(education.fieldOfStudy())
                .location(education.location())
                .startDate(education.startDate())
                .endDate(education.endDate())
                .description(education.description())
                .displayOrder(education.displayOrder())
                .build();
    }
}
