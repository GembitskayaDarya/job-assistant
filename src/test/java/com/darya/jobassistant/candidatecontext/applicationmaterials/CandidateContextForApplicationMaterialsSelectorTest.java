package com.darya.jobassistant.candidatecontext.applicationmaterials;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.candidatecontext.CandidateContextSnapshot;
import com.darya.jobassistant.candidatecontext.CareerHistoryAvailability;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.CandidateContextForApplicationMaterials;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.CandidateContextForApplicationMaterialsSelectionMetadata;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.SelectedCareerCompany;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.SelectedCareerPosition;
import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.CandidateSkill;
import com.darya.jobassistant.candidates.SkillProficiency;
import com.darya.jobassistant.careerhistory.aggregate.CareerAchievement;
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

class CandidateContextForApplicationMaterialsSelectorTest {

    private static final CandidateContextForApplicationMaterialsProperties GENEROUS =
            new CandidateContextForApplicationMaterialsProperties(8, 12, 6, 6, 6, 6, 15, 1500, 20000);

    // ==================== 1. Candidate Profile-only (no Career History) ====================

    @Test
    void select_missingCareerHistory_producesProfileOnlyContext() {
        CandidateContextForApplicationMaterialsSelector selector = new CandidateContextForApplicationMaterialsSelector(GENEROUS);
        CandidateContextSnapshot snapshot = snapshot(Optional.empty());

        CandidateContextForApplicationMaterials result = selector.select(snapshot, vacancy("Backend Engineer", "Java role"));

        assertThat(result.candidateProfile()).isEqualTo(validProfile());
        assertThat(result.careerHistoryAvailability()).isEqualTo(CareerHistoryAvailability.NOT_PROVIDED);
        assertThat(result.selectedCompanies()).isEmpty();
        assertThat(result.selectionMetadata())
                .isEqualTo(CandidateContextForApplicationMaterialsSelectionMetadata.empty(CareerHistoryAvailability.NOT_PROVIDED, null));
    }

    @Test
    void select_emptyCareerHistory_producesProfileOnlyContext() {
        CandidateContextForApplicationMaterialsSelector selector = new CandidateContextForApplicationMaterialsSelector(GENEROUS);
        UUID candidateProfileId = UUID.randomUUID();
        CareerHistoryAggregate emptyHistory = new CareerHistoryAggregate(UUID.randomUUID(), candidateProfileId, List.of(), 0L);
        CandidateContextSnapshot snapshot = snapshot(candidateProfileId, Optional.of(emptyHistory));

        CandidateContextForApplicationMaterials result = selector.select(snapshot, vacancy("Backend Engineer", "Java role"));

        assertThat(result.careerHistoryAvailability()).isEqualTo(CareerHistoryAvailability.EMPTY);
        assertThat(result.selectedCompanies()).isEmpty();
        assertThat(result.selectionMetadata())
                .isEqualTo(CandidateContextForApplicationMaterialsSelectionMetadata.empty(CareerHistoryAvailability.EMPTY, 0L));
    }

    // ==================== 2. Context containing Career History (companies + provenance) ====================

    @Test
    void select_availableCareerHistory_producesSelectedCompaniesWithProvenanceIds() {
        CandidateContextForApplicationMaterialsSelector selector = new CandidateContextForApplicationMaterialsSelector(GENEROUS);
        UUID positionId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        CareerPosition position = new CareerPosition(positionId, "Backend Engineer", null, null, null,
                LocalDate.of(2020, 1, 1), null, true, "Built services", 0, List.of(), List.of(), List.of());
        CareerCompany company = new CareerCompany(companyId, "Acme", "https://acme.test", "Fintech", "Remote",
                "A fintech company", 0, List.of(position));
        CandidateContextSnapshot snapshot = snapshotWithCompanies(List.of(company));

        CandidateContextForApplicationMaterials result = selector.select(snapshot, vacancy("Backend Engineer", "Java backend role"));

        assertThat(result.careerHistoryAvailability()).isEqualTo(CareerHistoryAvailability.AVAILABLE);
        assertThat(result.selectedCompanies()).hasSize(1);
        SelectedCareerCompany selectedCompany = result.selectedCompanies().get(0);
        assertThat(selectedCompany.careerCompanyId()).isEqualTo(companyId);
        assertThat(selectedCompany.name()).isEqualTo("Acme");
        assertThat(selectedCompany.website()).isEqualTo("https://acme.test");
        assertThat(selectedCompany.industry()).isEqualTo("Fintech");
        assertThat(selectedCompany.positions()).hasSize(1);
        assertThat(selectedCompany.positions().get(0).careerPositionId()).isEqualTo(positionId);
        assertThat(selectedCompany.positions().get(0).title()).isEqualTo("Backend Engineer");
    }

    // ==================== 3. Vacancy-aware deterministic selection ====================

    @Test
    void select_technologyMatch_outranksUnrelatedPosition() {
        CandidateContextForApplicationMaterialsProperties properties =
                new CandidateContextForApplicationMaterialsProperties(1, 12, 6, 6, 6, 6, 15, 1500, 20000);
        CandidateContextForApplicationMaterialsSelector selector = new CandidateContextForApplicationMaterialsSelector(properties);

        CareerProject matchingProject = project("Billing Platform", "Payments system", List.of(technology("Kafka", 0)), 0);
        CareerPosition matchingPosition = position("Software Engineer", null, List.of(matchingProject), 0,
                LocalDate.of(2018, 1, 1), LocalDate.of(2019, 1, 1), false);

        CareerPosition unrelatedPosition = position("Software Developer", null, List.of(), 1,
                LocalDate.of(2018, 1, 1), LocalDate.of(2019, 1, 1), false);

        CandidateContextSnapshot snapshot = snapshotWithCompanies(
                List.of(company("Acme", List.of(unrelatedPosition, matchingPosition), 0)));

        CandidateContextForApplicationMaterials result = selector.select(
                snapshot, vacancy("Backend role", "Looking for someone with strong Kafka experience"));

        assertThat(result.selectedCompanies()).hasSize(1);
        assertThat(result.selectedCompanies().get(0).positions()).hasSize(1);
        assertThat(result.selectedCompanies().get(0).positions().get(0).title()).isEqualTo("Software Engineer");
    }

    @Test
    void select_recentPosition_outranksOlderEquallyIrrelevantPosition() {
        CandidateContextForApplicationMaterialsProperties properties =
                new CandidateContextForApplicationMaterialsProperties(1, 12, 6, 6, 6, 6, 15, 1500, 20000);
        CandidateContextForApplicationMaterialsSelector selector = new CandidateContextForApplicationMaterialsSelector(properties);

        CareerPosition recent = position("Recent Role", null, List.of(), 0, LocalDate.of(2023, 1, 1), null, true);
        CareerPosition old = position("Old Role", null, List.of(), 1, LocalDate.of(2010, 1, 1), LocalDate.of(2011, 1, 1), false);
        CandidateContextSnapshot snapshot = snapshotWithCompanies(List.of(company("Acme", List.of(old, recent), 0)));

        CandidateContextForApplicationMaterials result = selector.select(snapshot, vacancy("Unrelated vacancy", "no overlap at all"));

        assertThat(result.selectedCompanies().get(0).positions()).hasSize(1);
        assertThat(result.selectedCompanies().get(0).positions().get(0).title()).isEqualTo("Recent Role");
    }

    @Test
    void select_strongTechnicalMatch_canOutrankPureRecency() {
        CandidateContextForApplicationMaterialsProperties properties =
                new CandidateContextForApplicationMaterialsProperties(1, 12, 6, 6, 6, 6, 15, 1500, 20000);
        CandidateContextForApplicationMaterialsSelector selector = new CandidateContextForApplicationMaterialsSelector(properties);

        // Older but a strong technology match (100) - must still beat a merely-recent, zero-score role.
        CareerProject matchingProject = project("Platform", "desc", List.of(technology("Kafka", 0)), 0);
        CareerPosition oldButMatching = position("Old Engineer", null, List.of(matchingProject), 0,
                LocalDate.of(2010, 1, 1), LocalDate.of(2011, 1, 1), false);
        CareerPosition recentButUnrelated = position("Recent Analyst", null, List.of(), 1, LocalDate.of(2023, 1, 1), null, true);
        CandidateContextSnapshot snapshot =
                snapshotWithCompanies(List.of(company("Acme", List.of(recentButUnrelated, oldButMatching), 0)));

        CandidateContextForApplicationMaterials result =
                selector.select(snapshot, vacancy("Backend role", "Strong Kafka experience required"));

        assertThat(result.selectedCompanies().get(0).positions().get(0).title()).isEqualTo("Old Engineer");
    }

    // ==================== 4. Ordering preservation ====================

    @Test
    void select_positionsWithinACompany_keepOriginalDisplayOrder_notScoreOrder() {
        CandidateContextForApplicationMaterialsSelector selector = new CandidateContextForApplicationMaterialsSelector(GENEROUS);

        // "Kafka Engineer" scores far higher (title + tech match) than "Support Engineer", but both
        // must render in their own authored displayOrder (0 then 1), not re-sorted by score.
        CareerProject matchingProject = project("Platform", "desc", List.of(technology("Kafka", 0)), 0);
        CareerPosition first = position("Support Engineer", null, List.of(), 0, LocalDate.of(2019, 1, 1), LocalDate.of(2020, 1, 1), false);
        CareerPosition second = position("Kafka Engineer", null, List.of(matchingProject), 1,
                LocalDate.of(2020, 1, 1), LocalDate.of(2021, 1, 1), false);
        CandidateContextSnapshot snapshot = snapshotWithCompanies(List.of(company("Acme", List.of(first, second), 0)));

        CandidateContextForApplicationMaterials result =
                selector.select(snapshot, vacancy("Kafka Engineer", "Strong Kafka experience required"));

        List<SelectedCareerPosition> positions = result.selectedCompanies().get(0).positions();
        assertThat(positions).extracting(SelectedCareerPosition::title).containsExactly("Support Engineer", "Kafka Engineer");
    }

    @Test
    void select_companies_orderedByBestContainedPositionScore() {
        CandidateContextForApplicationMaterialsSelector selector = new CandidateContextForApplicationMaterialsSelector(GENEROUS);

        CareerProject matchingProject = project("Platform", "desc", List.of(technology("Kafka", 0)), 0);
        CareerPosition strongMatch = position("Kafka Engineer", null, List.of(matchingProject), 0,
                LocalDate.of(2015, 1, 1), LocalDate.of(2016, 1, 1), false);
        CareerPosition weakMatch = position("Generalist", null, List.of(), 0, LocalDate.of(2015, 1, 1), LocalDate.of(2016, 1, 1), false);

        CareerCompany companyA = company("Company A", List.of(weakMatch), 0);
        CareerCompany companyB = company("Company B", List.of(strongMatch), 1);
        CandidateContextSnapshot snapshot = snapshotWithCompanies(List.of(companyA, companyB));

        CandidateContextForApplicationMaterials result =
                selector.select(snapshot, vacancy("Backend role", "Strong Kafka experience required"));

        assertThat(result.selectedCompanies()).extracting(SelectedCareerCompany::name).containsExactly("Company B", "Company A");
    }

    // ==================== 5. Bounded output for oversized Career History ====================

    @Test
    void select_positionLimit_isRespected() {
        CandidateContextForApplicationMaterialsProperties properties =
                new CandidateContextForApplicationMaterialsProperties(2, 12, 6, 6, 6, 6, 15, 1500, 20000);
        CandidateContextForApplicationMaterialsSelector selector = new CandidateContextForApplicationMaterialsSelector(properties);

        List<CareerPosition> positions = List.of(
                position("Engineer 1", null, List.of(), 0, LocalDate.of(2018, 1, 1), LocalDate.of(2019, 1, 1), false),
                position("Engineer 2", null, List.of(), 1, LocalDate.of(2019, 1, 1), LocalDate.of(2020, 1, 1), false),
                position("Engineer 3", null, List.of(), 2, LocalDate.of(2020, 1, 1), LocalDate.of(2021, 1, 1), false));
        CandidateContextSnapshot snapshot = snapshotWithCompanies(List.of(company("Acme", positions, 0)));

        CandidateContextForApplicationMaterials result = selector.select(snapshot, vacancy("Unrelated vacancy", "no overlap"));

        int selectedPositionCount = result.selectedCompanies().stream().mapToInt(c -> c.positions().size()).sum();
        assertThat(selectedPositionCount).isEqualTo(2);
        assertThat(result.selectionMetadata().availablePositionCount()).isEqualTo(3);
        assertThat(result.selectionMetadata().selectedPositionCount()).isEqualTo(2);
        assertThat(result.selectionMetadata().omittedPositionCount()).isEqualTo(1);
    }

    @Test
    void select_projectLimit_isRespected() {
        CandidateContextForApplicationMaterialsProperties properties =
                new CandidateContextForApplicationMaterialsProperties(8, 2, 6, 6, 6, 6, 15, 1500, 20000);
        CandidateContextForApplicationMaterialsSelector selector = new CandidateContextForApplicationMaterialsSelector(properties);

        List<CareerProject> projects = List.of(
                project("Project 1", null, List.of(), 0),
                project("Project 2", null, List.of(), 1),
                project("Project 3", null, List.of(), 2));
        CareerPosition position = position("Engineer", null, projects, 0, LocalDate.of(2020, 1, 1), null, true);
        CandidateContextSnapshot snapshot = snapshotWithCompanies(List.of(company("Acme", List.of(position), 0)));

        CandidateContextForApplicationMaterials result = selector.select(snapshot, vacancy("Unrelated vacancy", "no overlap"));

        assertThat(result.selectedCompanies().get(0).positions().get(0).projects()).hasSize(2);
        assertThat(result.selectionMetadata().availableProjectCount()).isEqualTo(3);
        assertThat(result.selectionMetadata().selectedProjectCount()).isEqualTo(2);
        assertThat(result.selectionMetadata().omittedProjectCount()).isEqualTo(1);
    }

    @Test
    void select_bulletAndTechnologyLimits_areRespected() {
        CandidateContextForApplicationMaterialsProperties properties =
                new CandidateContextForApplicationMaterialsProperties(8, 12, 2, 2, 2, 2, 2, 1500, 20000);
        CandidateContextForApplicationMaterialsSelector selector = new CandidateContextForApplicationMaterialsSelector(properties);

        List<CareerResponsibility> responsibilities = List.of(
                new CareerResponsibility(UUID.randomUUID(), "Responsibility one", 0),
                new CareerResponsibility(UUID.randomUUID(), "Responsibility two", 1),
                new CareerResponsibility(UUID.randomUUID(), "Responsibility three", 2));
        List<CareerTechnology> technologies = List.of(technology("Java", 0), technology("Kafka", 1), technology("Docker", 2));
        CareerProject project = new CareerProject(UUID.randomUUID(), "Platform", null, LocalDate.of(2020, 1, 1), null, 0,
                List.of(), List.of(), technologies);
        CareerPosition position = new CareerPosition(UUID.randomUUID(), "Engineer", null, null, null,
                LocalDate.of(2020, 1, 1), null, true, null, 0, responsibilities, List.of(), List.of(project));
        CandidateContextSnapshot snapshot = snapshotWithCompanies(List.of(company("Acme", List.of(position), 0)));

        CandidateContextForApplicationMaterials result = selector.select(snapshot, vacancy("Engineer", "role"));

        SelectedCareerPosition selected = result.selectedCompanies().get(0).positions().get(0);
        assertThat(selected.responsibilities()).hasSize(2);
        assertThat(selected.projects().get(0).technologies()).hasSize(2);
        assertThat(result.selectionMetadata().omittedResponsibilityCount()).isEqualTo(1);
        assertThat(result.selectionMetadata().omittedTechnologyCount()).isEqualTo(1);
    }

    @Test
    void select_characterBudget_isRespected() {
        CandidateContextForApplicationMaterialsProperties tightBudget =
                new CandidateContextForApplicationMaterialsProperties(8, 12, 6, 6, 6, 6, 15, 50, 50);
        CandidateContextForApplicationMaterialsSelector selector = new CandidateContextForApplicationMaterialsSelector(tightBudget);

        CareerPosition longPosition = position("Engineer", "A".repeat(200), List.of(), 0, LocalDate.of(2020, 1, 1), null, true);
        CandidateContextSnapshot snapshot = snapshotWithCompanies(List.of(company("Acme", List.of(longPosition), 0)));

        CandidateContextForApplicationMaterials result = selector.select(snapshot, vacancy("Engineer", "role"));

        assertThat(result.selectionMetadata().totalRenderedCharacters()).isLessThanOrEqualTo(50);
        assertThat(result.selectionMetadata().truncated()).isTrue();
    }

    @Test
    void select_oversizedField_isTruncatedDeterministically() {
        CandidateContextForApplicationMaterialsProperties properties =
                new CandidateContextForApplicationMaterialsProperties(8, 12, 6, 6, 6, 6, 15, 10, 20000);
        CandidateContextForApplicationMaterialsSelector selector = new CandidateContextForApplicationMaterialsSelector(properties);

        CareerResponsibility longResponsibility = new CareerResponsibility(UUID.randomUUID(), "A".repeat(30), 0);
        CareerPosition position = new CareerPosition(UUID.randomUUID(), "Engineer", null, null, null,
                LocalDate.of(2020, 1, 1), null, true, null, 0, List.of(longResponsibility), List.of(), List.of());
        CandidateContextSnapshot snapshot = snapshotWithCompanies(List.of(company("Acme", List.of(position), 0)));

        CandidateContextForApplicationMaterials result = selector.select(snapshot, vacancy("Engineer", "role"));

        String truncatedText = result.selectedCompanies().get(0).positions().get(0).responsibilities().get(0).text();
        assertThat(truncatedText).startsWith("A".repeat(10)).endsWith("[truncated]");
    }

    // ==================== 6. Supported skill proficiency values ====================

    @Test
    void select_candidateSkills_passThroughWithSupportedProficienciesOnly() {
        CandidateContextForApplicationMaterialsSelector selector = new CandidateContextForApplicationMaterialsSelector(GENEROUS);
        CandidateContextSnapshot snapshot = snapshot(Optional.empty());

        CandidateContextForApplicationMaterials result = selector.select(snapshot, vacancy("Engineer", "role"));

        assertThat(result.candidateProfile().skills()).extracting(CandidateSkill::proficiency)
                .containsExactly(SkillProficiency.STRONG, SkillProficiency.BASIC);
        assertThat(SkillProficiency.values())
                .containsExactlyInAnyOrder(SkillProficiency.BASIC, SkillProficiency.WORKING, SkillProficiency.STRONG, SkillProficiency.EXPERT);
    }

    // ==================== 7. Deterministic repeated selection ====================

    @Test
    void select_repeatedSelection_producesStructurallyEqualOutput() {
        CandidateContextForApplicationMaterialsSelector selector = new CandidateContextForApplicationMaterialsSelector(GENEROUS);
        CareerPosition position = position("Engineer", "Builds services", List.of(), 0, LocalDate.of(2020, 1, 1), null, true);
        CandidateContextSnapshot snapshot = snapshotWithCompanies(List.of(company("Acme", List.of(position), 0)));
        JobOffer vacancy = vacancy("Engineer", "role");

        CandidateContextForApplicationMaterials first = selector.select(snapshot, vacancy);
        CandidateContextForApplicationMaterials second = selector.select(snapshot, vacancy);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void select_companyAndPositionConstructionOrder_doesNotAffectOutput() {
        CandidateContextForApplicationMaterialsSelector selector = new CandidateContextForApplicationMaterialsSelector(GENEROUS);
        UUID candidateProfileId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        CareerPosition positionA = position("Engineer A", "desc A", List.of(), 0, LocalDate.of(2019, 1, 1), LocalDate.of(2020, 1, 1), false);
        CareerPosition positionB = position("Engineer B", "desc B", List.of(), 1, LocalDate.of(2020, 1, 1), LocalDate.of(2021, 1, 1), false);
        JobOffer vacancy = vacancy("Engineer", "General engineering role");

        CareerHistoryAggregate forward = new CareerHistoryAggregate(UUID.randomUUID(), candidateProfileId,
                List.of(company(companyId, "Acme", List.of(positionA, positionB), 0)), 0L);
        CareerHistoryAggregate reversed = new CareerHistoryAggregate(UUID.randomUUID(), candidateProfileId,
                List.of(company(companyId, "Acme", List.of(positionB, positionA), 0)), 0L);

        CandidateContextForApplicationMaterials resultForward =
                selector.select(snapshot(candidateProfileId, Optional.of(forward)), vacancy);
        CandidateContextForApplicationMaterials resultReversed =
                selector.select(snapshot(candidateProfileId, Optional.of(reversed)), vacancy);

        assertThat(resultForward.selectedCompanies()).isEqualTo(resultReversed.selectedCompanies());
    }

    // ==================== 8. No mutation of source aggregates ====================

    @Test
    void select_neverMutatesPersistedDomainValues() {
        CandidateContextForApplicationMaterialsSelector selector = new CandidateContextForApplicationMaterialsSelector(GENEROUS);
        UUID companyId = UUID.randomUUID();
        UUID positionId = UUID.randomUUID();
        CareerPosition position = new CareerPosition(positionId, "Engineer", null, null, null,
                LocalDate.of(2020, 1, 1), null, true, "Builds services", 0, List.of(), List.of(), List.of());
        CareerPosition expectedUnchanged = new CareerPosition(positionId, "Engineer", null, null, null,
                LocalDate.of(2020, 1, 1), null, true, "Builds services", 0, List.of(), List.of(), List.of());
        CareerCompany company = new CareerCompany(companyId, "Acme", null, null, null, null, 0, List.of(position));
        CareerCompany expectedUnchangedCompany = new CareerCompany(companyId, "Acme", null, null, null, null, 0, List.of(expectedUnchanged));
        CandidateContextSnapshot snapshot = snapshotWithCompanies(List.of(company));

        selector.select(snapshot, vacancy("Engineer", "role"));

        assertThat(company).isEqualTo(expectedUnchangedCompany);
    }

    // ==================== Fixtures ====================

    private CandidateContextSnapshot snapshot(Optional<CareerHistoryAggregate> careerHistory) {
        return snapshot(UUID.randomUUID(), careerHistory);
    }

    private CandidateContextSnapshot snapshot(UUID candidateProfileId, Optional<CareerHistoryAggregate> careerHistory) {
        return new CandidateContextSnapshot(candidateProfileId, "primary", 0L, validProfile(), careerHistory);
    }

    private CandidateContextSnapshot snapshotWithCompanies(List<CareerCompany> companies) {
        UUID candidateProfileId = UUID.randomUUID();
        CareerHistoryAggregate careerHistory = new CareerHistoryAggregate(UUID.randomUUID(), candidateProfileId, companies, 0L);
        return snapshot(candidateProfileId, Optional.of(careerHistory));
    }

    private CandidateProfile validProfile() {
        return new CandidateProfile(
                "Senior Java Backend Engineer", "Senior",
                List.of(new CandidateSkill("Java", SkillProficiency.STRONG, null),
                        new CandidateSkill("Kafka", SkillProficiency.BASIC, null)),
                List.of("en"), 5,
                new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null));
    }

    private JobOffer vacancy(String title, String description) {
        return new JobOffer("job-1", title, "Acme Corp", "Remote", null, description, "https://example.com/job-1", "test");
    }

    private CareerCompany company(String name, List<CareerPosition> positions, int displayOrder) {
        return company(UUID.randomUUID(), name, positions, displayOrder);
    }

    private CareerCompany company(UUID id, String name, List<CareerPosition> positions, int displayOrder) {
        return new CareerCompany(id, name, null, null, null, null, displayOrder, positions);
    }

    private CareerPosition position(
            String title, String description, List<CareerProject> projects, int displayOrder,
            LocalDate startDate, LocalDate endDate, boolean currentRole) {
        return new CareerPosition(UUID.randomUUID(), title, null, null, null,
                startDate, endDate, currentRole, description, displayOrder, List.of(), List.of(), projects);
    }

    private CareerProject project(String name, String description, List<CareerTechnology> technologies, int displayOrder) {
        return new CareerProject(UUID.randomUUID(), name, description, LocalDate.of(2020, 1, 1), null, displayOrder,
                List.of(), List.of(), technologies);
    }

    private CareerTechnology technology(String name, int displayOrder) {
        return new CareerTechnology(UUID.randomUUID(), name, null, displayOrder);
    }
}
