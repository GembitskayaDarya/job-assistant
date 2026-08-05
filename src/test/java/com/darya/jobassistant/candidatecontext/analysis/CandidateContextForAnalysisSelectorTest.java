package com.darya.jobassistant.candidatecontext.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.candidatecontext.CandidateContextSnapshot;
import com.darya.jobassistant.candidatecontext.CareerHistoryAvailability;
import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfile;
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
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CandidateContextForAnalysisSelectorTest {

    private static final CandidateContextAnalysisProperties GENEROUS =
            new CandidateContextAnalysisProperties(4, 6, 4, 4, 4, 4, 12, 1200, 12000);

    private final Locale originalDefaultLocale = Locale.getDefault();

    @AfterEach
    void restoreDefaultLocale() {
        Locale.setDefault(originalDefaultLocale);
    }

    @Test
    void select_missingCareerHistory_producesNoSelectedExperience() {
        CandidateContextForAnalysisSelector selector = new CandidateContextForAnalysisSelector(GENEROUS);
        CandidateContextSnapshot snapshot = snapshot(Optional.empty());

        CandidateContextForAnalysis result = selector.select(snapshot, vacancy("Backend Engineer", "Java role"));

        assertThat(result.careerHistoryAvailability()).isEqualTo(CareerHistoryAvailability.NOT_PROVIDED);
        assertThat(result.selectedPositions()).isEmpty();
        assertThat(result.selectionMetadata())
                .isEqualTo(CandidateContextSelectionMetadata.empty(CareerHistoryAvailability.NOT_PROVIDED, null));
    }

    @Test
    void select_emptyCareerHistory_producesNoSelectedExperience() {
        CandidateContextForAnalysisSelector selector = new CandidateContextForAnalysisSelector(GENEROUS);
        UUID candidateProfileId = UUID.randomUUID();
        CareerHistoryAggregate emptyHistory = new CareerHistoryAggregate(UUID.randomUUID(), candidateProfileId, List.of(), 0L);
        CandidateContextSnapshot snapshot = snapshot(candidateProfileId, Optional.of(emptyHistory));

        CandidateContextForAnalysis result = selector.select(snapshot, vacancy("Backend Engineer", "Java role"));

        assertThat(result.careerHistoryAvailability()).isEqualTo(CareerHistoryAvailability.EMPTY);
        assertThat(result.selectedPositions()).isEmpty();
        assertThat(result.selectionMetadata())
                .isEqualTo(CandidateContextSelectionMetadata.empty(CareerHistoryAvailability.EMPTY, 0L));
    }

    @Test
    void select_availableCareerHistory_producesSelectedEvidence() {
        CandidateContextForAnalysisSelector selector = new CandidateContextForAnalysisSelector(GENEROUS);
        CareerPosition position = position("Backend Engineer", "Built services", List.of(), List.of(), List.of(), 0);
        CandidateContextSnapshot snapshot = snapshotWithPositions(List.of(position));

        CandidateContextForAnalysis result = selector.select(snapshot, vacancy("Backend Engineer", "Java backend role"));

        assertThat(result.careerHistoryAvailability()).isEqualTo(CareerHistoryAvailability.AVAILABLE);
        assertThat(result.selectedPositions()).hasSize(1);
        assertThat(result.selectedPositions().get(0).title()).isEqualTo("Backend Engineer");
    }

    @Test
    void select_technologyMatch_outranksUnrelatedProject() {
        CandidateContextAnalysisProperties properties = new CandidateContextAnalysisProperties(1, 6, 4, 4, 4, 4, 12, 1200, 12000);
        CandidateContextForAnalysisSelector selector = new CandidateContextForAnalysisSelector(properties);

        CareerProject matchingProject = project("Billing Platform", "Payments system", List.of(), List.of(),
                List.of(technology("Kafka", 0)), 0);
        CareerPosition matchingPosition = position("Software Engineer", null, List.of(), List.of(), List.of(matchingProject), 0);

        CareerProject unrelatedProject = project("Internal Tools", "Unrelated internal tooling", List.of(), List.of(), List.of(), 0);
        CareerPosition unrelatedPosition = position("Software Developer", null, List.of(), List.of(), List.of(unrelatedProject), 1);

        CandidateContextSnapshot snapshot = snapshotWithPositions(List.of(unrelatedPosition, matchingPosition));

        CandidateContextForAnalysis result = selector.select(
                snapshot, vacancy("Backend role", "Looking for someone with strong Kafka experience"));

        assertThat(result.selectedPositions()).hasSize(1);
        assertThat(result.selectedPositions().get(0).title()).isEqualTo("Software Engineer");
    }

    @Test
    void select_positionTitleMatch_affectsRelevance() {
        CandidateContextAnalysisProperties properties = new CandidateContextAnalysisProperties(1, 6, 4, 4, 4, 4, 12, 1200, 12000);
        CandidateContextForAnalysisSelector selector = new CandidateContextForAnalysisSelector(properties);

        CareerPosition matchingTitle = position("Senior Golang Engineer", null, List.of(), List.of(), List.of(), 0);
        CareerPosition nonMatchingTitle = position("Support Analyst", null, List.of(), List.of(), List.of(), 1);
        CandidateContextSnapshot snapshot = snapshotWithPositions(List.of(nonMatchingTitle, matchingTitle));

        CandidateContextForAnalysis result = selector.select(snapshot, vacancy("Senior Golang Engineer", "Remote role"));

        assertThat(result.selectedPositions()).hasSize(1);
        assertThat(result.selectedPositions().get(0).title()).isEqualTo("Senior Golang Engineer");
    }

    @Test
    void select_projectNameMatch_affectsRelevance() {
        CandidateContextAnalysisProperties properties = new CandidateContextAnalysisProperties(4, 1, 4, 4, 4, 4, 12, 1200, 12000);
        CandidateContextForAnalysisSelector selector = new CandidateContextForAnalysisSelector(properties);

        CareerProject matchingProject = project("Nova Migration", "Data migration effort", List.of(), List.of(), List.of(), 0);
        CareerProject unrelatedProject = project("Legacy Cleanup", "Unrelated cleanup work", List.of(), List.of(), List.of(), 1);
        CareerPosition position = position("Engineer", null, List.of(), List.of(), List.of(unrelatedProject, matchingProject), 0);
        CandidateContextSnapshot snapshot = snapshotWithPositions(List.of(position));

        CandidateContextForAnalysis result = selector.select(snapshot, vacancy("Engineer", "Work on the Nova Migration project"));

        assertThat(result.selectedPositions()).hasSize(1);
        assertThat(result.selectedPositions().get(0).projects()).hasSize(1);
        assertThat(result.selectedPositions().get(0).projects().get(0).name()).isEqualTo("Nova Migration");
    }

    @Test
    void select_bulletOverlap_affectsRelevance() {
        CandidateContextAnalysisProperties properties = new CandidateContextAnalysisProperties(1, 6, 4, 4, 4, 4, 12, 1200, 12000);
        CandidateContextForAnalysisSelector selector = new CandidateContextForAnalysisSelector(properties);

        CareerResponsibility matchingResponsibility = new CareerResponsibility(null, "Implemented event streaming pipelines", 0);
        CareerPosition matchingPosition = position("Engineer A", null, List.of(matchingResponsibility), List.of(), List.of(), 0);
        CareerPosition unrelatedPosition = position("Engineer B", null, List.of(), List.of(), List.of(), 1);
        CandidateContextSnapshot snapshot = snapshotWithPositions(List.of(unrelatedPosition, matchingPosition));

        CandidateContextForAnalysis result = selector.select(
                snapshot, vacancy("Backend role", "We need someone to build event streaming pipelines"));

        assertThat(result.selectedPositions()).hasSize(1);
        assertThat(result.selectedPositions().get(0).title()).isEqualTo("Engineer A");
    }

    @Test
    void select_equalNonZeroScores_breakTiesByDisplayOrder() {
        CandidateContextAnalysisProperties properties = new CandidateContextAnalysisProperties(1, 6, 4, 4, 4, 4, 12, 1200, 12000);
        CandidateContextForAnalysisSelector selector = new CandidateContextForAnalysisSelector(properties);

        // Both positions match on title equally (same weight) - score tie, broken by displayOrder.
        CareerPosition first = position("Java Engineer", null, List.of(), List.of(), List.of(), 0);
        CareerPosition second = position("Java Engineer", null, List.of(), List.of(), List.of(), 1);
        CandidateContextSnapshot snapshot = snapshotWithPositions(List.of(second, first));

        CandidateContextForAnalysis result = selector.select(snapshot, vacancy("Java Engineer", "role"));

        assertThat(result.selectedPositions()).hasSize(1);
        // displayOrder 0 (first) must win the tie, regardless of aggregate construction order above.
        assertThat(result.selectedPositions().get(0).description()).isNull();
    }

    @Test
    void select_everyScoreZero_fallsBackToDisplayOrder() {
        CandidateContextAnalysisProperties properties = new CandidateContextAnalysisProperties(1, 6, 4, 4, 4, 4, 12, 1200, 12000);
        CandidateContextForAnalysisSelector selector = new CandidateContextForAnalysisSelector(properties);

        CareerPosition displayOrderZero = position("Alpha Role", null, List.of(), List.of(), List.of(), 0);
        CareerPosition displayOrderOne = position("Beta Role", null, List.of(), List.of(), List.of(), 1);
        CandidateContextSnapshot snapshot = snapshotWithPositions(List.of(displayOrderOne, displayOrderZero));

        CandidateContextForAnalysis result = selector.select(snapshot, vacancy("Completely unrelated vacancy", "No overlap at all"));

        assertThat(result.selectedPositions()).hasSize(1);
        assertThat(result.selectedPositions().get(0).title()).isEqualTo("Alpha Role");
    }

    @Test
    void select_companyAndPositionConstructionOrder_doesNotAffectOutput() {
        CandidateContextForAnalysisSelector selector = new CandidateContextForAnalysisSelector(GENEROUS);
        UUID candidateProfileId = UUID.randomUUID();
        CareerPosition positionA = position("Engineer A", "desc A", List.of(), List.of(), List.of(), 0);
        CareerPosition positionB = position("Engineer B", "desc B", List.of(), List.of(), List.of(), 1);
        JobOffer vacancy = vacancy("Engineer", "General engineering role");

        CareerHistoryAggregate forward = new CareerHistoryAggregate(UUID.randomUUID(), candidateProfileId,
                List.of(company("Acme", List.of(positionA, positionB), 0)), 0L);
        CareerHistoryAggregate reversed = new CareerHistoryAggregate(UUID.randomUUID(), candidateProfileId,
                List.of(company("Acme", List.of(positionB, positionA), 0)), 0L);

        CandidateContextForAnalysis resultForward =
                selector.select(snapshot(candidateProfileId, Optional.of(forward)), vacancy);
        CandidateContextForAnalysis resultReversed =
                selector.select(snapshot(candidateProfileId, Optional.of(reversed)), vacancy);

        assertThat(resultForward.selectedPositions()).isEqualTo(resultReversed.selectedPositions());
    }

    @Test
    void select_defaultJvmLocale_doesNotAffectOutput() {
        CandidateContextAnalysisProperties properties = new CandidateContextAnalysisProperties(1, 6, 4, 4, 4, 4, 12, 1200, 12000);
        CandidateContextForAnalysisSelector selector = new CandidateContextForAnalysisSelector(properties);

        CareerProject matchingProject = project("Pipeline", "Delivery pipeline", List.of(), List.of(),
                List.of(technology("CI/CD", 0)), 0);
        CareerPosition matchingPosition = position("Engineer", null, List.of(), List.of(), List.of(matchingProject), 0);
        CareerPosition unrelatedPosition = position("Analyst", null, List.of(), List.of(), List.of(), 1);
        JobOffer vacancy = vacancy("Engineer", "Experience with CI/CD required");

        Locale.setDefault(Locale.US);
        CandidateContextForAnalysis underUsLocale =
                selector.select(snapshotWithPositions(List.of(unrelatedPosition, matchingPosition)), vacancy);

        // Turkish lowercasing turns 'I' into dotless-i ('ı'), breaking naive toLowerCase() matches -
        // the selector must use Locale.ROOT internally and be unaffected by this.
        Locale.setDefault(Locale.forLanguageTag("tr"));
        CandidateContextForAnalysis underTurkishLocale =
                selector.select(snapshotWithPositions(List.of(unrelatedPosition, matchingPosition)), vacancy);

        assertThat(underTurkishLocale.selectedPositions()).isEqualTo(underUsLocale.selectedPositions());
        assertThat(underUsLocale.selectedPositions().get(0).title()).isEqualTo("Engineer");
    }

    @Test
    void select_characterBudget_isRespected() {
        CandidateContextAnalysisProperties tightBudget =
                new CandidateContextAnalysisProperties(4, 6, 4, 4, 4, 4, 12, 50, 50);
        CandidateContextForAnalysisSelector selector = new CandidateContextForAnalysisSelector(tightBudget);

        CareerPosition longPosition = position("Engineer", "A".repeat(200), List.of(), List.of(), List.of(), 0);
        CandidateContextSnapshot snapshot = snapshotWithPositions(List.of(longPosition));

        CandidateContextForAnalysis result = selector.select(snapshot, vacancy("Engineer", "role"));

        assertThat(result.selectionMetadata().totalRenderedCharacters()).isLessThanOrEqualTo(50);
        assertThat(result.selectionMetadata().truncated()).isTrue();
    }

    @Test
    void select_positionLimit_isRespected() {
        CandidateContextAnalysisProperties properties = new CandidateContextAnalysisProperties(2, 6, 4, 4, 4, 4, 12, 1200, 12000);
        CandidateContextForAnalysisSelector selector = new CandidateContextForAnalysisSelector(properties);

        List<CareerPosition> positions = List.of(
                position("Engineer 1", null, List.of(), List.of(), List.of(), 0),
                position("Engineer 2", null, List.of(), List.of(), List.of(), 1),
                position("Engineer 3", null, List.of(), List.of(), List.of(), 2));
        CandidateContextSnapshot snapshot = snapshotWithPositions(positions);

        CandidateContextForAnalysis result = selector.select(snapshot, vacancy("Unrelated vacancy", "no overlap"));

        assertThat(result.selectedPositions()).hasSize(2);
        assertThat(result.selectionMetadata().availablePositionCount()).isEqualTo(3);
        assertThat(result.selectionMetadata().selectedPositionCount()).isEqualTo(2);
        assertThat(result.selectionMetadata().omittedPositionCount()).isEqualTo(1);
    }

    @Test
    void select_projectLimit_isRespected() {
        CandidateContextAnalysisProperties properties = new CandidateContextAnalysisProperties(4, 2, 4, 4, 4, 4, 12, 1200, 12000);
        CandidateContextForAnalysisSelector selector = new CandidateContextForAnalysisSelector(properties);

        List<CareerProject> projects = List.of(
                project("Project 1", null, List.of(), List.of(), List.of(), 0),
                project("Project 2", null, List.of(), List.of(), List.of(), 1),
                project("Project 3", null, List.of(), List.of(), List.of(), 2));
        CareerPosition position = position("Engineer", null, List.of(), List.of(), projects, 0);
        CandidateContextSnapshot snapshot = snapshotWithPositions(List.of(position));

        CandidateContextForAnalysis result = selector.select(snapshot, vacancy("Unrelated vacancy", "no overlap"));

        assertThat(result.selectedPositions()).hasSize(1);
        assertThat(result.selectedPositions().get(0).projects()).hasSize(2);
        assertThat(result.selectionMetadata().availableProjectCount()).isEqualTo(3);
        assertThat(result.selectionMetadata().selectedProjectCount()).isEqualTo(2);
        assertThat(result.selectionMetadata().omittedProjectCount()).isEqualTo(1);
    }

    @Test
    void select_bulletLimits_areRespected() {
        CandidateContextAnalysisProperties properties = new CandidateContextAnalysisProperties(4, 6, 2, 2, 4, 4, 12, 1200, 12000);
        CandidateContextForAnalysisSelector selector = new CandidateContextForAnalysisSelector(properties);

        List<CareerResponsibility> responsibilities = List.of(
                new CareerResponsibility(null, "Responsibility one", 0),
                new CareerResponsibility(null, "Responsibility two", 1),
                new CareerResponsibility(null, "Responsibility three", 2));
        List<CareerAchievement> achievements = List.of(
                new CareerAchievement(null, "Achievement one", 0),
                new CareerAchievement(null, "Achievement two", 1),
                new CareerAchievement(null, "Achievement three", 2));
        CareerPosition position = position("Engineer", null, responsibilities, achievements, List.of(), 0);
        CandidateContextSnapshot snapshot = snapshotWithPositions(List.of(position));

        CandidateContextForAnalysis result = selector.select(snapshot, vacancy("Engineer", "role"));

        SelectedCareerPosition selected = result.selectedPositions().get(0);
        assertThat(selected.responsibilities()).hasSize(2);
        assertThat(selected.achievements()).hasSize(2);
        assertThat(result.selectionMetadata().omittedResponsibilityCount()).isEqualTo(1);
        assertThat(result.selectionMetadata().omittedAchievementCount()).isEqualTo(1);
    }

    @Test
    void select_technologyLimit_isRespected() {
        CandidateContextAnalysisProperties properties = new CandidateContextAnalysisProperties(4, 6, 4, 4, 4, 4, 2, 1200, 12000);
        CandidateContextForAnalysisSelector selector = new CandidateContextForAnalysisSelector(properties);

        List<CareerTechnology> technologies = List.of(
                technology("Java", 0), technology("Kafka", 1), technology("Docker", 2), technology("AWS", 3));
        CareerProject project = project("Platform", null, List.of(), List.of(), technologies, 0);
        CareerPosition position = position("Engineer", null, List.of(), List.of(), List.of(project), 0);
        CandidateContextSnapshot snapshot = snapshotWithPositions(List.of(position));

        CandidateContextForAnalysis result = selector.select(snapshot, vacancy("Engineer", "role"));

        assertThat(result.selectedPositions().get(0).projects().get(0).technologies()).hasSize(2);
        assertThat(result.selectionMetadata().omittedTechnologyCount()).isEqualTo(2);
    }

    @Test
    void select_oversizedField_isTruncatedDeterministically() {
        CandidateContextAnalysisProperties properties = new CandidateContextAnalysisProperties(4, 6, 4, 4, 4, 4, 12, 10, 12000);
        CandidateContextForAnalysisSelector selector = new CandidateContextForAnalysisSelector(properties);

        CareerResponsibility longResponsibility = new CareerResponsibility(null, "A".repeat(30), 0);
        CareerPosition position = position("Engineer", null, List.of(longResponsibility), List.of(), List.of(), 0);
        CandidateContextSnapshot snapshot = snapshotWithPositions(List.of(position));

        CandidateContextForAnalysis firstRun = selector.select(snapshot, vacancy("Engineer", "role"));
        CandidateContextForAnalysis secondRun = selector.select(snapshot, vacancy("Engineer", "role"));

        String truncatedText = firstRun.selectedPositions().get(0).responsibilities().get(0).text();
        assertThat(truncatedText).startsWith("A".repeat(10)).endsWith("[truncated]");
        assertThat(truncatedText).isEqualTo(secondRun.selectedPositions().get(0).responsibilities().get(0).text());
    }

    @Test
    void select_truncationMetadata_isCorrect() {
        CandidateContextForAnalysisSelector generousSelector = new CandidateContextForAnalysisSelector(GENEROUS);
        CareerPosition shortPosition = position("Engineer", "short", List.of(), List.of(), List.of(), 0);
        CandidateContextSnapshot snapshot = snapshotWithPositions(List.of(shortPosition));

        CandidateContextForAnalysis noTruncation = generousSelector.select(snapshot, vacancy("Engineer", "role"));
        assertThat(noTruncation.selectionMetadata().truncated()).isFalse();

        CandidateContextAnalysisProperties tightFields = new CandidateContextAnalysisProperties(4, 6, 4, 4, 4, 4, 12, 3, 12000);
        CandidateContextForAnalysisSelector tightSelector = new CandidateContextForAnalysisSelector(tightFields);
        CandidateContextForAnalysis withTruncation = tightSelector.select(snapshot, vacancy("Engineer", "role"));
        assertThat(withTruncation.selectionMetadata().truncated()).isTrue();
    }

    @Test
    void select_neverMutatesPersistedDomainValues() {
        CandidateContextForAnalysisSelector selector = new CandidateContextForAnalysisSelector(GENEROUS);
        CareerPosition position = position("Engineer", "Builds services", List.of(), List.of(), List.of(), 0);
        CareerPosition expectedUnchanged = position("Engineer", "Builds services", List.of(), List.of(), List.of(), 0);
        CandidateContextSnapshot snapshot = snapshotWithPositions(List.of(position));

        selector.select(snapshot, vacancy("Engineer", "role"));

        assertThat(position).isEqualTo(expectedUnchanged);
    }

    @Test
    void select_repeatedSelection_producesStructurallyEqualOutput() {
        CandidateContextForAnalysisSelector selector = new CandidateContextForAnalysisSelector(GENEROUS);
        CareerPosition position = position("Engineer", "Builds services", List.of(), List.of(), List.of(), 0);
        CandidateContextSnapshot snapshot = snapshotWithPositions(List.of(position));
        JobOffer vacancy = vacancy("Engineer", "role");

        CandidateContextForAnalysis first = selector.select(snapshot, vacancy);
        CandidateContextForAnalysis second = selector.select(snapshot, vacancy);

        assertThat(first).isEqualTo(second);
    }

    // --- Fixtures ---------------------------------------------------------------------------------

    private CandidateContextSnapshot snapshot(Optional<CareerHistoryAggregate> careerHistory) {
        return snapshot(UUID.randomUUID(), careerHistory);
    }

    private CandidateContextSnapshot snapshot(UUID candidateProfileId, Optional<CareerHistoryAggregate> careerHistory) {
        return new CandidateContextSnapshot(candidateProfileId, "primary", 0L, validProfile(), careerHistory);
    }

    private CandidateContextSnapshot snapshotWithPositions(List<CareerPosition> positions) {
        UUID candidateProfileId = UUID.randomUUID();
        CareerHistoryAggregate careerHistory = new CareerHistoryAggregate(UUID.randomUUID(), candidateProfileId,
                List.of(company("Acme", positions, 0)), 0L);
        return snapshot(candidateProfileId, Optional.of(careerHistory));
    }

    private CandidateProfile validProfile() {
        return new CandidateProfile(
                "Senior Java Backend Engineer", "Senior", List.of(), List.of(), 5,
                new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null));
    }

    private JobOffer vacancy(String title, String description) {
        return new JobOffer("job-1", title, "Acme Corp", "Remote", null, description, "https://example.com/job-1", "test");
    }

    private CareerCompany company(String name, List<CareerPosition> positions, int displayOrder) {
        return new CareerCompany(null, name, null, null, null, null, displayOrder, positions);
    }

    private CareerPosition position(
            String title, String description, List<CareerResponsibility> responsibilities,
            List<CareerAchievement> achievements, List<CareerProject> projects, int displayOrder) {
        return new CareerPosition(null, title, null, null, null,
                LocalDate.of(2020, 1, 1), null, true, description, displayOrder, responsibilities, achievements, projects);
    }

    private CareerProject project(
            String name, String description, List<CareerResponsibility> responsibilities,
            List<CareerAchievement> achievements, List<CareerTechnology> technologies, int displayOrder) {
        return new CareerProject(null, name, description, LocalDate.of(2020, 1, 1), null, displayOrder,
                responsibilities, achievements, technologies);
    }

    private CareerTechnology technology(String name, int displayOrder) {
        return new CareerTechnology(null, name, null, displayOrder);
    }
}
