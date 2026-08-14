package com.darya.jobassistant.candidatecontext.cv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.darya.jobassistant.candidatecontext.CandidateContextSnapshot;
import com.darya.jobassistant.candidatecontext.CareerHistoryAvailability;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceAchievement;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceCompany;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourcePosition;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceProject;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceResponsibility;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceSnapshot;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceTechnology;
import com.darya.jobassistant.candidates.CandidateLanguageFacts;
import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfileFacts;
import com.darya.jobassistant.candidates.CandidateSkillFacts;
import com.darya.jobassistant.candidates.PreferenceImportance;
import com.darya.jobassistant.candidates.SkillProficiency;
import com.darya.jobassistant.careerhistory.aggregate.CareerAchievement;
import com.darya.jobassistant.careerhistory.aggregate.CareerCompany;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import com.darya.jobassistant.careerhistory.aggregate.CareerPosition;
import com.darya.jobassistant.careerhistory.aggregate.CareerProject;
import com.darya.jobassistant.careerhistory.aggregate.CareerResponsibility;
import com.darya.jobassistant.careerhistory.aggregate.CareerTechnology;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CvSourceSnapshotFactoryTest {

    // ==================== 1/2/3/4. Candidate profile, skills, proficiency, category ====================

    @Test
    void from_mapsCandidateProfileFieldsUnchanged() {
        CandidateProfileFacts profile = validProfile();
        CandidateContextSnapshot snapshot = snapshot(profile, Optional.empty());

        CvSourceSnapshot result = CvSourceSnapshotFactory.from(snapshot);

        assertThat(result.candidateProfile()).isEqualTo(profile);
        assertThat(result.candidateProfile().targetRole()).isEqualTo("Senior Backend Engineer");
        assertThat(result.candidateProfile().experienceYears()).isEqualTo(6);
    }

    @Test
    void from_preservesEverySkillWithItsProficiencyAndCategory() {
        CandidateProfileFacts profile = validProfile();
        CvSourceSnapshot result = CvSourceSnapshotFactory.from(snapshot(profile, Optional.empty()));

        assertThat(result.candidateProfile().skills()).containsExactly(
                new CandidateSkillFacts("Java", "Language", "6+ years", SkillProficiency.EXPERT),
                new CandidateSkillFacts("Kubernetes", "Platform", null, SkillProficiency.WORKING),
                new CandidateSkillFacts("Rust", null, null, SkillProficiency.BASIC),
                new CandidateSkillFacts("PostgreSQL", "Database", null, SkillProficiency.STRONG));
    }

    /**
     * Sprint 11 Step 2: the persisted {@code candidate_profile_skill.id} - propagated starting at
     * {@code CandidateProfileFactsAssembler} (see {@code CandidateProfileFactsAssemblerTest}) -
     * survives unchanged through {@code CandidateContextSnapshot} all the way to {@link
     * CvSourceSnapshot}, so a future CV tailoring result can reference an existing skill by stable
     * identity instead of a free-form name.
     */
    @Test
    void from_preservesStableSkillIdentity() {
        UUID javaSkillId = UUID.randomUUID();
        CandidateProfileFacts profile = new CandidateProfileFacts(
                "Senior Backend Engineer", "Senior",
                List.of(new CandidateSkillFacts(javaSkillId, "Java", "Language", null, SkillProficiency.EXPERT)),
                List.of(), 6,
                new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null));

        CvSourceSnapshot result = CvSourceSnapshotFactory.from(snapshot(profile, Optional.empty()));

        assertThat(result.candidateProfile().skills()).hasSize(1);
        assertThat(result.candidateProfile().skills().get(0).candidateSkillId()).isEqualTo(javaSkillId);
    }

    /**
     * Sprint 11 Step 1 correction requirement: skill category - previously dropped before {@code
     * CandidateContextSnapshot} was ever built, since the common context used to route through the
     * vacancy-analysis-bounded {@code CandidateProfile} (see {@code CandidateProfileAnalysisAssembler})
     * - now survives all the way to {@link CvSourceSnapshot} whenever the source domain has it.
     */
    @Test
    void from_skillCategory_reachesCvSourceSnapshotWhenPresentInSourceDomain() {
        CvSourceSnapshot result = CvSourceSnapshotFactory.from(snapshot(validProfile(), Optional.empty()));

        assertThat(result.candidateProfile().skills())
                .extracting(CandidateSkillFacts::name, CandidateSkillFacts::category)
                .containsExactly(
                        tuple("Java", "Language"),
                        tuple("Kubernetes", "Platform"),
                        tuple("Rust", null),
                        tuple("PostgreSQL", "Database"));
    }

    @Test
    void skillProficiency_supportsOnlyTheFourDocumentedLevels() {
        assertThat(SkillProficiency.values()).containsExactlyInAnyOrder(
                SkillProficiency.BASIC, SkillProficiency.WORKING, SkillProficiency.STRONG, SkillProficiency.EXPERT);
    }

    // ==================== 5. Languages, including proficiency ====================

    @Test
    void from_preservesLanguageNames() {
        CandidateProfileFacts profile = validProfile();
        CvSourceSnapshot result = CvSourceSnapshotFactory.from(snapshot(profile, Optional.empty()));

        assertThat(result.candidateProfile().languages())
                .extracting(CandidateLanguageFacts::name)
                .containsExactly("English", "Polish", "Russian");
    }

    /**
     * Sprint 11 Step 1 correction requirement: language proficiency - previously dropped entirely
     * before {@code CandidateContextSnapshot} was ever built (the old lossy {@code
     * CandidateProfile.languages()} was a bare {@code List<String>} of names only) - now survives
     * all the way to {@link CvSourceSnapshot}.
     */
    @Test
    void from_languageProficiency_reachesCvSourceSnapshot() {
        CvSourceSnapshot result = CvSourceSnapshotFactory.from(snapshot(validProfile(), Optional.empty()));

        assertThat(result.candidateProfile().languages()).containsExactly(
                new CandidateLanguageFacts("English", "native"),
                new CandidateLanguageFacts("Polish", "fluent"),
                new CandidateLanguageFacts("Russian", null));
    }

    // ==================== Career history availability ====================

    @Test
    void from_missingCareerHistory_producesNotProvidedWithNoCompanies() {
        CvSourceSnapshot result = CvSourceSnapshotFactory.from(snapshot(validProfile(), Optional.empty()));

        assertThat(result.careerHistoryAvailability()).isEqualTo(CareerHistoryAvailability.NOT_PROVIDED);
        assertThat(result.companies()).isEmpty();
    }

    @Test
    void from_emptyCareerHistory_producesEmptyWithNoCompanies() {
        CareerHistoryAggregate emptyHistory = new CareerHistoryAggregate(UUID.randomUUID(), UUID.randomUUID(), List.of(), 3L);
        CvSourceSnapshot result = CvSourceSnapshotFactory.from(snapshot(validProfile(), Optional.of(emptyHistory)));

        assertThat(result.careerHistoryAvailability()).isEqualTo(CareerHistoryAvailability.EMPTY);
        assertThat(result.companies()).isEmpty();
    }

    @Test
    void from_nullSnapshot_throws() {
        assertThatThrownBy(() -> CvSourceSnapshotFactory.from(null)).isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== 6/7/8/9/10/11/12. Complete career graph, bullets, technologies, ordering, identity ====================

    @Test
    void from_preservesAllCompaniesPositionsProjectsBulletsAndTechnologiesWithIdentityAndOrder() {
        UUID respId1 = UUID.randomUUID();
        UUID respId2 = UUID.randomUUID();
        UUID achId1 = UUID.randomUUID();
        UUID techId1 = UUID.randomUUID();
        UUID techId2 = UUID.randomUUID();
        UUID projectId1 = UUID.randomUUID();
        UUID projectId2 = UUID.randomUUID();
        UUID positionId1 = UUID.randomUUID();
        UUID positionId2 = UUID.randomUUID();
        UUID companyId1 = UUID.randomUUID();
        UUID companyId2 = UUID.randomUUID();

        CareerProject projectZebra = new CareerProject(projectId1, "Zebra Migration", "desc-z",
                LocalDate.of(2021, 1, 1), LocalDate.of(2021, 6, 1),
                /* displayOrder */ 0,
                List.of(new CareerResponsibility(respId1, "Led migration", 0)),
                List.of(new CareerAchievement(achId1, "Cut latency by 30%", 0)),
                List.of(new CareerTechnology(techId1, "Kafka", "Messaging", 0)));
        CareerProject projectAlpha = new CareerProject(projectId2, "Alpha Platform", "desc-a",
                LocalDate.of(2021, 6, 2), LocalDate.of(2022, 1, 1),
                /* displayOrder */ 1,
                List.of(new CareerResponsibility(respId2, "Built platform", 0)),
                List.of(),
                List.of(new CareerTechnology(techId2, "Redis", "Cache", 0)));

        // Constructed out of display-order sequence to prove the factory does not re-sort itself -
        // CareerPosition's own constructor is what restores display order.
        CareerPosition position = new CareerPosition(positionId1, "Senior Engineer", "Full-time", "Remote", "Remote",
                LocalDate.of(2021, 1, 1), LocalDate.of(2022, 1, 1), false, "Owned backend services", 0,
                List.of(), List.of(), List.of(projectAlpha, projectZebra));

        CareerPosition positionNoProjects = new CareerPosition(positionId2, "Junior Engineer", null, null, null,
                LocalDate.of(2019, 1, 1), LocalDate.of(2020, 1, 1), false, null, 1, List.of(), List.of(), List.of());

        CareerCompany companyWithHistory = new CareerCompany(companyId1, "Acme Corp", "https://acme.test", "Fintech",
                "Berlin", "A fintech company", 0, List.of(position, positionNoProjects));
        CareerCompany companyWithoutPositions = new CareerCompany(companyId2, "Shell Co", null, null, null, null, 1, List.of());

        CareerHistoryAggregate history = new CareerHistoryAggregate(
                UUID.randomUUID(), UUID.randomUUID(), List.of(companyWithoutPositions, companyWithHistory), 5L);

        CvSourceSnapshot result = CvSourceSnapshotFactory.from(snapshot(validProfile(), Optional.of(history)));

        assertThat(result.careerHistoryAvailability()).isEqualTo(CareerHistoryAvailability.AVAILABLE);
        assertThat(result.companies()).hasSize(2);

        // Companies preserved in their own display order (0, 1), including the position-less one.
        CvSourceCompany firstCompany = result.companies().get(0);
        assertThat(firstCompany.careerCompanyId()).isEqualTo(companyId1);
        assertThat(firstCompany.name()).isEqualTo("Acme Corp");
        assertThat(firstCompany.positions()).hasSize(2);

        CvSourceCompany secondCompany = result.companies().get(1);
        assertThat(secondCompany.careerCompanyId()).isEqualTo(companyId2);
        assertThat(secondCompany.name()).isEqualTo("Shell Co");
        assertThat(secondCompany.positions()).isEmpty();

        // Both positions preserved, in display order.
        CvSourcePosition firstPosition = firstCompany.positions().get(0);
        assertThat(firstPosition.careerPositionId()).isEqualTo(positionId1);
        assertThat(firstPosition.title()).isEqualTo("Senior Engineer");
        CvSourcePosition secondPosition = firstCompany.positions().get(1);
        assertThat(secondPosition.careerPositionId()).isEqualTo(positionId2);
        assertThat(secondPosition.title()).isEqualTo("Junior Engineer");
        assertThat(secondPosition.projects()).isEmpty();

        // Both projects preserved, in their own display order (Zebra=0 before Alpha=1) despite
        // being constructed in the opposite (Zebra, Alpha) list-literal order above and despite
        // "Alpha" sorting first alphabetically - neither insertion order nor name is what governs
        // the result, only the authored display order.
        assertThat(firstPosition.projects()).hasSize(2);
        CvSourceProject firstProject = firstPosition.projects().get(0);
        CvSourceProject secondProject = firstPosition.projects().get(1);
        assertThat(firstProject.name()).isEqualTo("Zebra Migration");
        assertThat(firstProject.careerProjectId()).isEqualTo(projectId1);
        assertThat(secondProject.name()).isEqualTo("Alpha Platform");
        assertThat(secondProject.careerProjectId()).isEqualTo(projectId2);

        // Bullets preserved with identity, attached to the correct project.
        assertThat(firstProject.responsibilities()).containsExactly(new CvSourceResponsibility(respId1, "Led migration"));
        assertThat(firstProject.achievements()).containsExactly(new CvSourceAchievement(achId1, "Cut latency by 30%"));
        assertThat(secondProject.responsibilities()).containsExactly(new CvSourceResponsibility(respId2, "Built platform"));
        assertThat(secondProject.achievements()).isEmpty();

        // Technologies remain attached to the correct project, not swapped or merged.
        assertThat(firstProject.technologies()).containsExactly(new CvSourceTechnology(techId1, "Kafka", "Messaging"));
        assertThat(secondProject.technologies()).containsExactly(new CvSourceTechnology(techId2, "Redis", "Cache"));
    }

    // ==================== 13. Determinism ====================

    @Test
    void from_equivalentInput_producesEquivalentSnapshot() {
        CandidateContextSnapshot snapshotA = snapshot(validProfile(), Optional.of(sampleHistory()));
        CandidateContextSnapshot snapshotB = snapshot(validProfile(), Optional.of(sampleHistory()));

        CvSourceSnapshot resultA = CvSourceSnapshotFactory.from(snapshotA);
        CvSourceSnapshot resultB = CvSourceSnapshotFactory.from(snapshotB);

        assertThat(resultA).isEqualTo(resultB);
    }

    // ==================== 14. Immutability ====================

    @Test
    void snapshot_collectionsAreUnmodifiable() {
        CvSourceSnapshot result = CvSourceSnapshotFactory.from(snapshot(validProfile(), Optional.of(sampleHistory())));
        CvSourceCompany company = result.companies().get(0);
        CvSourcePosition position = company.positions().get(0);
        CvSourceProject project = position.projects().get(0);

        assertThatThrownBy(() -> result.companies().add(company)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> company.positions().add(position)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> position.projects().add(project)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> project.technologies().add(project.technologies().get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> project.responsibilities().add(project.responsibilities().get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.candidateProfile().skills().add(result.candidateProfile().skills().get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.candidateProfile().languages().add(result.candidateProfile().languages().get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ==================== Fixtures ====================

    /**
     * Fixed, non-random ids throughout - {@link #from_equivalentInput_producesEquivalentSnapshot}
     * calls this twice and compares the results for equality, so every id that ends up in the
     * resulting {@link CvSourceSnapshot} must be stable across calls, not freshly randomized.
     */
    private CareerHistoryAggregate sampleHistory() {
        CareerProject project = new CareerProject(
                UUID.fromString("00000000-0000-0000-0000-000000000001"), "Billing Platform", "Payments system",
                LocalDate.of(2020, 1, 1), LocalDate.of(2021, 1, 1), 0,
                List.of(new CareerResponsibility(
                        UUID.fromString("00000000-0000-0000-0000-000000000002"), "Designed billing pipeline", 0)),
                List.of(new CareerAchievement(
                        UUID.fromString("00000000-0000-0000-0000-000000000003"), "Reduced billing errors by 40%", 0)),
                List.of(new CareerTechnology(
                        UUID.fromString("00000000-0000-0000-0000-000000000004"), "Java", "Language", 0)));
        CareerPosition position = new CareerPosition(
                UUID.fromString("00000000-0000-0000-0000-000000000005"), "Backend Engineer", "Full-time", "Remote",
                "Remote", LocalDate.of(2020, 1, 1), LocalDate.of(2021, 1, 1), false, "Built services", 0,
                List.of(), List.of(), List.of(project));
        CareerCompany company = new CareerCompany(
                UUID.fromString("00000000-0000-0000-0000-000000000006"), "Acme", "https://acme.test", "Fintech",
                "Remote", "A fintech company", 0, List.of(position));
        return new CareerHistoryAggregate(
                UUID.fromString("00000000-0000-0000-0000-000000000007"),
                UUID.fromString("00000000-0000-0000-0000-000000000008"), List.of(company), 1L);
    }

    private CandidateContextSnapshot snapshot(CandidateProfileFacts profile, Optional<CareerHistoryAggregate> careerHistory) {
        return new CandidateContextSnapshot(UUID.randomUUID(), "primary", 1L, profile, careerHistory);
    }

    // ==================== Sprint 11 Step 5: Personal Projects ====================

    @Test
    void from_noPersonalProjects_producesEmptyList() {
        CvSourceSnapshot result = CvSourceSnapshotFactory.from(snapshot(validProfile(), Optional.empty()));

        assertThat(result.personalProjects()).isEmpty();
    }

    @Test
    void from_preservesEveryPersonalProjectHighlightAndTechnologyUnfilteredInOrder() {
        UUID projectId = UUID.randomUUID();
        UUID highlightId = UUID.randomUUID();
        UUID technologyId = UUID.randomUUID();
        com.darya.jobassistant.personalprojects.aggregate.PersonalProject project =
                new com.darya.jobassistant.personalprojects.aggregate.PersonalProject(
                        projectId, UUID.randomUUID(), "Example Project", "A factual description",
                        "https://github.com/example/project", LocalDate.of(2022, 1, 1), LocalDate.of(2022, 6, 1), 0,
                        List.of(new com.darya.jobassistant.personalprojects.aggregate.PersonalProjectHighlight(highlightId, "Built a REST API", 0)),
                        List.of(new com.darya.jobassistant.personalprojects.aggregate.PersonalProjectTechnology(technologyId, "Java", "Language", 0)),
                        0L);
        CandidateContextSnapshot snapshot = new CandidateContextSnapshot(
                UUID.randomUUID(), "primary", 1L, validProfile(), Optional.empty(), List.of(project));

        CvSourceSnapshot result = CvSourceSnapshotFactory.from(snapshot);

        assertThat(result.personalProjects()).hasSize(1);
        var mappedProject = result.personalProjects().get(0);
        assertThat(mappedProject.personalProjectId()).isEqualTo(projectId);
        assertThat(mappedProject.name()).isEqualTo("Example Project");
        assertThat(mappedProject.highlights()).extracting("personalProjectHighlightId", "text")
                .containsExactly(tuple(highlightId, "Built a REST API"));
        assertThat(mappedProject.technologies()).extracting("personalProjectTechnologyId", "name", "category")
                .containsExactly(tuple(technologyId, "Java", "Language"));
    }

    private CandidateProfileFacts validProfile() {
        return new CandidateProfileFacts(
                "Senior Backend Engineer",
                "Senior",
                List.of(
                        new CandidateSkillFacts("Java", "Language", "6+ years", SkillProficiency.EXPERT),
                        new CandidateSkillFacts("Kubernetes", "Platform", null, SkillProficiency.WORKING),
                        new CandidateSkillFacts("Rust", null, null, SkillProficiency.BASIC),
                        new CandidateSkillFacts("PostgreSQL", "Database", null, SkillProficiency.STRONG)),
                List.of(
                        new CandidateLanguageFacts("English", "native"),
                        new CandidateLanguageFacts("Polish", "fluent"),
                        new CandidateLanguageFacts("Russian", null)),
                6,
                new CandidatePreferences(
                        "Poland", "Remote", PreferenceImportance.REQUIRED, List.of("PL", "DE"), true,
                        List.of("B2B"), PreferenceImportance.PREFERRED, "Product", PreferenceImportance.PREFERRED,
                        "120000+ PLN"));
    }
}
