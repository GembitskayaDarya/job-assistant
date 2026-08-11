package com.darya.jobassistant.applicationmaterials.render.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedApplicationMaterials;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCoverLetter;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCoverLetterParagraph;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCv;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCvExperience;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCvSkill;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedExperienceBullet;
import com.darya.jobassistant.candidatecontext.CandidateContextSnapshot;
import com.darya.jobassistant.candidatecontext.applicationmaterials.CandidateContextForApplicationMaterialsProperties;
import com.darya.jobassistant.candidatecontext.applicationmaterials.CandidateContextForApplicationMaterialsSelector;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.CandidateContextForApplicationMaterials;
import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.SkillProficiency;
import com.darya.jobassistant.careerhistory.aggregate.CareerCompany;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import com.darya.jobassistant.careerhistory.aggregate.CareerPosition;
import com.darya.jobassistant.careerhistory.aggregate.CareerProject;
import com.darya.jobassistant.careerhistory.aggregate.CareerResponsibility;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RenderModelAssemblerTest {

    private final UUID companyId = UUID.randomUUID();
    private final UUID positionId = UUID.randomUUID();
    private final UUID responsibilityId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    // ==================== 2. Canonical company/title/date reconstruction from provenance/context ====================

    @Test
    void assemble_resolvesCanonicalCompanyTitleAndDatesFromContext_neverFromAiResponse() {
        CandidateContextForApplicationMaterials context = contextWithOnePosition();
        GeneratedCvExperience experience = new GeneratedCvExperience(
                positionId, List.of(new GeneratedExperienceBullet("Built backend services", List.of(responsibilityId))));
        GeneratedApplicationMaterials semanticResult = new GeneratedApplicationMaterials(
                new GeneratedCv("Headline", "Summary", List.of(), List.of(experience), List.of()), validCoverLetter());

        RenderableApplicationMaterials rendered = RenderModelAssembler.assemble(semanticResult, context, vacancy());

        RenderableCvExperience renderedExperience = rendered.cv().experiences().get(0);
        assertThat(renderedExperience.companyName()).isEqualTo("Acme Corp");
        assertThat(renderedExperience.positionTitle()).isEqualTo("Senior Backend Engineer");
        assertThat(renderedExperience.startDate()).isEqualTo(LocalDate.of(2020, 1, 1));
        assertThat(renderedExperience.currentRole()).isTrue();
        assertThat(renderedExperience.bullets()).containsExactly("Built backend services");
    }

    @Test
    void assemble_includesProjectInformationForSelectedProjects() {
        CareerProject project = new CareerProject(projectId, "Billing Platform", null, LocalDate.of(2021, 1, 1), LocalDate.of(2022, 1, 1), 0,
                List.of(), List.of(), List.of());
        CandidateContextForApplicationMaterials context = contextWithPositionAndProject(project);
        GeneratedCvExperience experience = new GeneratedCvExperience(
                positionId, List.of(new GeneratedExperienceBullet("Built backend services", List.of(responsibilityId))));
        GeneratedApplicationMaterials semanticResult = new GeneratedApplicationMaterials(
                new GeneratedCv("Headline", "Summary", List.of(), List.of(experience), List.of()), validCoverLetter());

        RenderableApplicationMaterials rendered = RenderModelAssembler.assemble(semanticResult, context, vacancy());

        RenderableCvProject renderedProject = rendered.cv().experiences().get(0).projects().get(0);
        assertThat(renderedProject.name()).isEqualTo("Billing Platform");
        assertThat(renderedProject.startDate()).isEqualTo(LocalDate.of(2021, 1, 1));
    }

    // ==================== 3. AI semantic output cannot substitute canonical metadata ====================

    @Test
    void assemble_experienceReferencingUnknownPosition_throwsRatherThanFabricating() {
        CandidateContextForApplicationMaterials context = contextWithOnePosition();
        GeneratedCvExperience experienceWithBogusId = new GeneratedCvExperience(
                UUID.randomUUID(), List.of(new GeneratedExperienceBullet("Did work", List.of(UUID.randomUUID()))));
        GeneratedApplicationMaterials semanticResult = new GeneratedApplicationMaterials(
                new GeneratedCv("Headline", "Summary", List.of(), List.of(experienceWithBogusId), List.of()), validCoverLetter());

        assertThatThrownBy(() -> RenderModelAssembler.assemble(semanticResult, context, vacancy()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not present in the supplied candidate context");
    }

    // ==================== Candidate profile header / skills / languages pass-through ====================

    @Test
    void assemble_populatesHeaderAndSkillsAndLanguagesFromContext() {
        CandidateContextForApplicationMaterials context = contextWithOnePosition();
        GeneratedApplicationMaterials semanticResult = new GeneratedApplicationMaterials(
                new GeneratedCv("Headline", "Summary", List.of(new GeneratedCvSkill("Java", SkillProficiency.EXPERT)), List.of(), List.of("en")),
                validCoverLetter());

        RenderableApplicationMaterials rendered = RenderModelAssembler.assemble(semanticResult, context, vacancy());

        assertThat(rendered.cv().targetRole()).isEqualTo("Senior Java Backend Engineer");
        assertThat(rendered.cv().seniority()).isEqualTo("Senior");
        assertThat(rendered.cv().totalExperienceYears()).isEqualTo(6);
        assertThat(rendered.cv().skills()).containsExactly(new GeneratedCvSkill("Java", SkillProficiency.EXPERT));
        assertThat(rendered.cv().languages()).containsExactly("en");
    }

    @Test
    void assemble_coverLetterCarriesTrustedVacancyMetadata() {
        CandidateContextForApplicationMaterials context = contextWithOnePosition();
        GeneratedApplicationMaterials semanticResult = new GeneratedApplicationMaterials(
                new GeneratedCv("Headline", "Summary", List.of(), List.of(), List.of()), validCoverLetter());

        RenderableApplicationMaterials rendered = RenderModelAssembler.assemble(semanticResult, context, vacancy());

        assertThat(rendered.coverLetter().vacancyTitle()).isEqualTo("Senior Backend Engineer");
        assertThat(rendered.coverLetter().vacancyCompany()).isEqualTo("Acme Corp");
        assertThat(rendered.coverLetter().closing()).isEqualTo("Sincerely, the candidate");
    }

    // ==================== Fixtures ====================

    private CandidateContextForApplicationMaterials contextWithOnePosition() {
        CareerPosition position = new CareerPosition(positionId, "Senior Backend Engineer", null, null, null,
                LocalDate.of(2020, 1, 1), null, true, null, 0,
                List.of(new CareerResponsibility(responsibilityId, "Built backend services", 0)), List.of(), List.of());
        return select(new CareerCompany(companyId, "Acme Corp", null, null, null, null, 0, List.of(position)));
    }

    private CandidateContextForApplicationMaterials contextWithPositionAndProject(CareerProject project) {
        CareerPosition position = new CareerPosition(positionId, "Senior Backend Engineer", null, null, null,
                LocalDate.of(2020, 1, 1), null, true, null, 0,
                List.of(new CareerResponsibility(responsibilityId, "Built backend services", 0)), List.of(), List.of(project));
        return select(new CareerCompany(companyId, "Acme Corp", null, null, null, null, 0, List.of(position)));
    }

    private CandidateContextForApplicationMaterials select(CareerCompany company) {
        CareerHistoryAggregate careerHistory = new CareerHistoryAggregate(UUID.randomUUID(), UUID.randomUUID(), List.of(company), 0L);
        CandidateContextSnapshot snapshot = new CandidateContextSnapshot(
                UUID.randomUUID(), "primary", 0L, validProfile(), Optional.of(careerHistory));
        CandidateContextForApplicationMaterialsProperties properties =
                new CandidateContextForApplicationMaterialsProperties(8, 12, 6, 6, 6, 6, 15, 1500, 20000);
        return new CandidateContextForApplicationMaterialsSelector(properties).select(snapshot, vacancy());
    }

    private CandidateProfile validProfile() {
        return new CandidateProfile("Senior Java Backend Engineer", "Senior", List.of(), List.of(), 6,
                new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null));
    }

    private JobOffer vacancy() {
        return new JobOffer("job-1", "Senior Backend Engineer", "Acme Corp", "Remote", null,
                "We need a backend engineer.", "https://example.com/job-1", "test");
    }

    private GeneratedCoverLetter validCoverLetter() {
        return new GeneratedCoverLetter(null, List.of(new GeneratedCoverLetterParagraph("Body text.", List.of())), "Sincerely, the candidate");
    }
}
