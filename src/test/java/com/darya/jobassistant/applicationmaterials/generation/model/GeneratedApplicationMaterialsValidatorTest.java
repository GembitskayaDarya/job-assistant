package com.darya.jobassistant.applicationmaterials.generation.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.candidatecontext.CandidateContextSnapshot;
import com.darya.jobassistant.candidatecontext.applicationmaterials.CandidateContextForApplicationMaterialsProperties;
import com.darya.jobassistant.candidatecontext.applicationmaterials.CandidateContextForApplicationMaterialsSelector;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.CandidateContextForApplicationMaterials;
import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.CandidateSkill;
import com.darya.jobassistant.candidates.SkillProficiency;
import com.darya.jobassistant.careerhistory.aggregate.CareerCompany;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import com.darya.jobassistant.careerhistory.aggregate.CareerPosition;
import com.darya.jobassistant.careerhistory.aggregate.CareerProject;
import com.darya.jobassistant.careerhistory.aggregate.CareerResponsibility;
import com.darya.jobassistant.careerhistory.aggregate.CareerTechnology;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GeneratedApplicationMaterialsValidatorTest {

    private static final CandidateContextForApplicationMaterialsProperties GENEROUS =
            new CandidateContextForApplicationMaterialsProperties(8, 12, 6, 6, 6, 6, 15, 1500, 20000);

    private final UUID companyId = UUID.randomUUID();
    private final UUID positionId = UUID.randomUUID();
    private final UUID responsibilityId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();
    private final UUID technologyId = UUID.randomUUID();

    // ==================== Valid combining of multiple source references ====================

    @Test
    void validate_bulletCombiningResponsibilityAndTechnology_isAccepted() {
        CandidateContextForApplicationMaterials context = contextWithProjectAndTechnology();
        GeneratedApplicationMaterials raw = materialsWithBulletSourceIds(List.of(responsibilityId, technologyId));

        GeneratedApplicationMaterials validated = GeneratedApplicationMaterialsValidator.validate(raw, context);

        assertThat(validated.cv().experiences().get(0).bullets().get(0).sourceIds())
                .containsExactlyInAnyOrder(responsibilityId, technologyId);
    }

    @Test
    void validate_duplicateSourceIdsInOneBullet_areNormalizedNotRejected() {
        CandidateContextForApplicationMaterials context = contextWithProjectAndTechnology();
        GeneratedApplicationMaterials raw = materialsWithBulletSourceIds(List.of(responsibilityId, responsibilityId));

        GeneratedApplicationMaterials validated = GeneratedApplicationMaterialsValidator.validate(raw, context);

        assertThat(validated.cv().experiences().get(0).bullets().get(0).sourceIds()).containsExactly(responsibilityId);
    }

    // ==================== Invalid/unknown provenance UUID ====================

    @Test
    void validate_bulletSourceIdNotInContext_isRejected() {
        CandidateContextForApplicationMaterials context = simpleContext();
        GeneratedApplicationMaterials raw = materialsWithBulletSourceIds(List.of(UUID.randomUUID()));

        assertThatThrownBy(() -> GeneratedApplicationMaterialsValidator.validate(raw, context))
                .isInstanceOf(ApplicationMaterialsValidationException.class)
                .hasMessageContaining("source id");
    }

    @Test
    void validate_bulletSourceIdFromADifferentPosition_isRejected() {
        UUID otherPositionResponsibilityId = UUID.randomUUID();
        CareerPosition otherPosition = new CareerPosition(UUID.randomUUID(), "Other Role", null, null, null,
                LocalDate.of(2015, 1, 1), LocalDate.of(2016, 1, 1), false, null, 1,
                List.of(new CareerResponsibility(otherPositionResponsibilityId, "Did other work", 0)), List.of(), List.of());
        CareerPosition mainPosition = position(List.of(), List.of());
        CareerCompany company = new CareerCompany(companyId, "Acme", null, null, null, null, 0, List.of(mainPosition, otherPosition));
        CandidateContextForApplicationMaterials context = selectContext(company);

        GeneratedApplicationMaterials raw = materialsWithBulletSourceIds(List.of(otherPositionResponsibilityId));

        assertThatThrownBy(() -> GeneratedApplicationMaterialsValidator.validate(raw, context))
                .isInstanceOf(ApplicationMaterialsValidationException.class);
    }

    @Test
    void validate_experienceReferencingUnselectedPosition_isRejected() {
        CandidateContextForApplicationMaterials context = simpleContext();
        GeneratedCvExperience unknownExperience = new GeneratedCvExperience(
                UUID.randomUUID(), List.of(new GeneratedExperienceBullet("Did work", List.of(responsibilityId))));
        GeneratedApplicationMaterials raw = new GeneratedApplicationMaterials(
                new GeneratedCv("Headline", "Summary", List.of(), List.of(unknownExperience), List.of()),
                validCoverLetter());

        assertThatThrownBy(() -> GeneratedApplicationMaterialsValidator.validate(raw, context))
                .isInstanceOf(ApplicationMaterialsValidationException.class)
                .hasMessageContaining("careerPositionId");
    }

    @Test
    void validate_duplicateExperienceForSamePosition_isRejected() {
        CandidateContextForApplicationMaterials context = simpleContext();
        GeneratedCvExperience experience = new GeneratedCvExperience(
                positionId, List.of(new GeneratedExperienceBullet("Did work", List.of(responsibilityId))));
        GeneratedApplicationMaterials raw = new GeneratedApplicationMaterials(
                new GeneratedCv("Headline", "Summary", List.of(), List.of(experience, experience), List.of()),
                validCoverLetter());

        assertThatThrownBy(() -> GeneratedApplicationMaterialsValidator.validate(raw, context))
                .isInstanceOf(ApplicationMaterialsValidationException.class)
                .hasMessageContaining("more than one CV experience");
    }

    // ==================== Missing provenance for generated career bullet ====================

    @Test
    void validate_bulletWithNoSourceIds_isRejectedAtConstructionTime() {
        assertThatThrownBy(() -> new GeneratedExperienceBullet("Did work", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one source reference");
    }

    // ==================== Generated skill validation ====================

    @Test
    void validate_skillPresentInCandidateProfile_resolvesCanonicalProficiency() {
        CandidateContextForApplicationMaterials context = simpleContext();
        GeneratedApplicationMaterials raw = materialsWithSkills(List.of("Java"));

        GeneratedApplicationMaterials validated = GeneratedApplicationMaterialsValidator.validate(raw, context);

        assertThat(validated.cv().skills()).containsExactly(new GeneratedCvSkill("Java", SkillProficiency.STRONG));
    }

    @Test
    void validate_skillPresentOnlyInSelectedCareerHistoryTechnology_hasNullProficiency() {
        CandidateContextForApplicationMaterials context = contextWithProjectAndTechnology();
        GeneratedApplicationMaterials raw = materialsWithSkillsAndBullet(List.of("Kafka"), List.of(responsibilityId));

        GeneratedApplicationMaterials validated = GeneratedApplicationMaterialsValidator.validate(raw, context);

        assertThat(validated.cv().skills()).containsExactly(new GeneratedCvSkill("Kafka", null));
    }

    @Test
    void validate_vacancyOnlySkillNotInCandidateContext_isRejected() {
        CandidateContextForApplicationMaterials context = simpleContext();
        GeneratedApplicationMaterials raw = materialsWithSkills(List.of("Kubernetes"));

        assertThatThrownBy(() -> GeneratedApplicationMaterialsValidator.validate(raw, context))
                .isInstanceOf(ApplicationMaterialsValidationException.class)
                .hasMessageContaining("Kubernetes");
    }

    @Test
    void validate_aiSuppliedProficiencyIsNeverTrusted_alwaysOverwrittenByCanonicalValue() {
        CandidateContextForApplicationMaterials context = simpleContext();
        GeneratedCvSkill aiSuppliedSkillWithWrongProficiency = new GeneratedCvSkill("Java", SkillProficiency.EXPERT);
        GeneratedApplicationMaterials raw = new GeneratedApplicationMaterials(
                new GeneratedCv("Headline", "Summary", List.of(aiSuppliedSkillWithWrongProficiency),
                        List.of(validExperience()), List.of()),
                validCoverLetter());

        GeneratedApplicationMaterials validated = GeneratedApplicationMaterialsValidator.validate(raw, context);

        assertThat(validated.cv().skills().get(0).proficiency()).isEqualTo(SkillProficiency.STRONG);
    }

    @Test
    void validate_duplicateSkillNames_areNormalizedNotRejected() {
        CandidateContextForApplicationMaterials context = simpleContext();
        GeneratedApplicationMaterials raw = materialsWithSkills(List.of("Java", "java"));

        GeneratedApplicationMaterials validated = GeneratedApplicationMaterialsValidator.validate(raw, context);

        assertThat(validated.cv().skills()).hasSize(1);
    }

    // ==================== Cover letter provenance ====================

    @Test
    void validate_coverLetterParagraphWithNoSourceIds_isAccepted() {
        CandidateContextForApplicationMaterials context = simpleContext();
        GeneratedApplicationMaterials raw = new GeneratedApplicationMaterials(
                new GeneratedCv("Headline", "Summary", List.of(), List.of(validExperience()), List.of()),
                new GeneratedCoverLetter(null, List.of(new GeneratedCoverLetterParagraph("Purely connective wording.", List.of())), "Closing"));

        GeneratedApplicationMaterials validated = GeneratedApplicationMaterialsValidator.validate(raw, context);

        assertThat(validated.coverLetter().paragraphs().get(0).sourceIds()).isEmpty();
    }

    @Test
    void validate_coverLetterParagraphWithInvalidSourceId_isRejected() {
        CandidateContextForApplicationMaterials context = simpleContext();
        GeneratedApplicationMaterials raw = new GeneratedApplicationMaterials(
                new GeneratedCv("Headline", "Summary", List.of(), List.of(validExperience()), List.of()),
                new GeneratedCoverLetter(null,
                        List.of(new GeneratedCoverLetterParagraph("Factual claim.", List.of(UUID.randomUUID()))), "Closing"));

        assertThatThrownBy(() -> GeneratedApplicationMaterialsValidator.validate(raw, context))
                .isInstanceOf(ApplicationMaterialsValidationException.class);
    }

    @Test
    void validate_coverLetterParagraphWithValidSourceId_isAccepted() {
        CandidateContextForApplicationMaterials context = simpleContext();
        GeneratedApplicationMaterials raw = new GeneratedApplicationMaterials(
                new GeneratedCv("Headline", "Summary", List.of(), List.of(validExperience()), List.of()),
                new GeneratedCoverLetter(null,
                        List.of(new GeneratedCoverLetterParagraph("Factual claim.", List.of(responsibilityId))), "Closing"));

        GeneratedApplicationMaterials validated = GeneratedApplicationMaterialsValidator.validate(raw, context);

        assertThat(validated.coverLetter().paragraphs().get(0).sourceIds()).containsExactly(responsibilityId);
    }

    // ==================== Structural checks ====================

    @Test
    void validate_nullResponse_isRejected() {
        assertThatThrownBy(() -> GeneratedApplicationMaterialsValidator.validate(null, simpleContext()))
                .isInstanceOf(ApplicationMaterialsValidationException.class);
    }

    @Test
    void validate_noExperiences_isAccepted_notAHallucinationRisk() {
        CandidateContextForApplicationMaterials context = simpleContext();
        GeneratedApplicationMaterials raw = new GeneratedApplicationMaterials(
                new GeneratedCv("Headline", "Summary", List.of(), List.of(), List.of()), validCoverLetter());

        GeneratedApplicationMaterials validated = GeneratedApplicationMaterialsValidator.validate(raw, context);

        assertThat(validated.cv().experiences()).isEmpty();
    }

    // ==================== Fixtures ====================

    private CandidateContextForApplicationMaterials simpleContext() {
        CareerCompany company = new CareerCompany(companyId, "Acme", null, null, null, null, 0, List.of(position(List.of(), List.of())));
        return selectContext(company);
    }

    private CandidateContextForApplicationMaterials contextWithProjectAndTechnology() {
        CareerProject project = new CareerProject(projectId, "Billing Platform", null, LocalDate.of(2020, 1, 1), null, 0,
                List.of(), List.of(), List.of(new CareerTechnology(technologyId, "Kafka", null, 0)));
        CareerCompany company = new CareerCompany(companyId, "Acme", null, null, null, null, 0, List.of(position(List.of(), List.of(project))));
        return selectContext(company);
    }

    private CandidateContextForApplicationMaterials selectContext(CareerCompany company) {
        CareerHistoryAggregate careerHistory = new CareerHistoryAggregate(UUID.randomUUID(), UUID.randomUUID(), List.of(company), 0L);
        CandidateContextSnapshot snapshot = new CandidateContextSnapshot(
                UUID.randomUUID(), "primary", 0L, validProfile(), Optional.of(careerHistory));
        return new CandidateContextForApplicationMaterialsSelector(GENEROUS).select(snapshot, vacancy());
    }

    private CareerPosition position(List<CareerResponsibility> extraResponsibilities, List<CareerProject> projects) {
        List<CareerResponsibility> responsibilities = new java.util.ArrayList<>();
        responsibilities.add(new CareerResponsibility(responsibilityId, "Built backend services", 0));
        responsibilities.addAll(extraResponsibilities);
        return new CareerPosition(positionId, "Backend Engineer", null, null, null,
                LocalDate.of(2020, 1, 1), null, true, null, 0, responsibilities, List.of(), projects);
    }

    private CandidateProfile validProfile() {
        return new CandidateProfile("Senior Java Backend Engineer", "Senior",
                List.of(new CandidateSkill("Java", SkillProficiency.STRONG, null)), List.of("en"), 6,
                new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null));
    }

    private JobOffer vacancy() {
        return new JobOffer("job-1", "Backend Engineer", "Acme Corp", "Remote", null,
                "We need a backend engineer.", "https://example.com/job-1", "test");
    }

    private GeneratedCvExperience validExperience() {
        return new GeneratedCvExperience(positionId, List.of(new GeneratedExperienceBullet("Built backend services", List.of(responsibilityId))));
    }

    private GeneratedCoverLetter validCoverLetter() {
        return new GeneratedCoverLetter(null, List.of(new GeneratedCoverLetterParagraph("Closing thoughts.", List.of())), "Closing");
    }

    private GeneratedApplicationMaterials materialsWithBulletSourceIds(List<UUID> sourceIds) {
        GeneratedCvExperience experience = new GeneratedCvExperience(
                positionId, List.of(new GeneratedExperienceBullet("Did work", sourceIds)));
        return new GeneratedApplicationMaterials(
                new GeneratedCv("Headline", "Summary", List.of(), List.of(experience), List.of()), validCoverLetter());
    }

    private GeneratedApplicationMaterials materialsWithSkills(List<String> skillNames) {
        return materialsWithSkillsAndBullet(skillNames, List.of(responsibilityId));
    }

    private GeneratedApplicationMaterials materialsWithSkillsAndBullet(List<String> skillNames, List<UUID> bulletSourceIds) {
        List<GeneratedCvSkill> skills = skillNames.stream().map(name -> new GeneratedCvSkill(name, null)).toList();
        GeneratedCvExperience experience = new GeneratedCvExperience(
                positionId, List.of(new GeneratedExperienceBullet("Did work", bulletSourceIds)));
        return new GeneratedApplicationMaterials(
                new GeneratedCv("Headline", "Summary", skills, List.of(experience), List.of()), validCoverLetter());
    }
}
