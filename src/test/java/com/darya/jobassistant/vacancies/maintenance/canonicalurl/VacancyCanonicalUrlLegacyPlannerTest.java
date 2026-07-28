package com.darya.jobassistant.vacancies.maintenance.canonicalurl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

/**
 * Pure unit tests for the package-private {@link VacancyCanonicalUrlLegacyPlanner} - the single
 * classification algorithm both {@link VacancyCanonicalUrlAuditService} and {@link
 * VacancyCanonicalUrlBackfillService} delegate to. {@link VacancyCanonicalUrlAuditServiceTest}
 * already covers the classification rules end to end through the audit report; these tests focus
 * specifically on the {@link VacancyCanonicalUrlLegacyPlan} shape the backfill side consumes.
 */
@ExtendWith(MockitoExtension.class)
class VacancyCanonicalUrlLegacyPlannerTest {

    @Mock
    private VacancyRepository vacancyRepository;

    @Test
    void plan_safeAssignments_includeVacancyIdAndExactCanonicalValue() {
        UUID vacancyId = UUID.randomUUID();
        stubLegacyRows(List.of(new LegacyVacancyUrlRow(vacancyId, "HTTPS://EXAMPLE.COM:443/jobs/123/?utm_source=linkedin")));
        when(vacancyRepository.findPopulatedCanonicalUrlRows()).thenReturn(List.of());

        VacancyCanonicalUrlLegacyPlan plan = new VacancyCanonicalUrlLegacyPlanner(vacancyRepository).plan(500);

        assertThat(plan.safeAssignments()).hasSize(1);
        SafeCanonicalUrlAssignment assignment = plan.safeAssignments().get(0);
        assertThat(assignment.vacancyId()).isEqualTo(vacancyId);
        assertThat(assignment.canonicalUrl()).isEqualTo("https://example.com/jobs/123");
    }

    @Test
    void plan_invalidAndCollisionRows_areNeverIncludedInSafeAssignments() {
        UUID invalidId = UUID.randomUUID();
        UUID collisionFirst = UUID.randomUUID();
        UUID collisionSecond = UUID.randomUUID();
        UUID currentCollisionId = UUID.randomUUID();
        UUID currentOwnerId = UUID.randomUUID();
        String collisionBase = "https://example.com/jobs/" + UUID.randomUUID();
        String currentCollisionUrl = "https://example.com/jobs/" + UUID.randomUUID();
        stubLegacyRows(List.of(
                new LegacyVacancyUrlRow(invalidId, "not a url"),
                new LegacyVacancyUrlRow(collisionFirst, collisionBase),
                new LegacyVacancyUrlRow(collisionSecond, collisionBase + "?utm_source=linkedin"),
                new LegacyVacancyUrlRow(currentCollisionId, currentCollisionUrl)));
        when(vacancyRepository.findPopulatedCanonicalUrlRows())
                .thenReturn(List.of(new PopulatedCanonicalUrlRow(currentCollisionUrl, currentOwnerId)));

        VacancyCanonicalUrlLegacyPlan plan = new VacancyCanonicalUrlLegacyPlanner(vacancyRepository).plan(500);

        assertThat(plan.safeAssignments()).isEmpty();
        assertThat(plan.invalidSourceUrlVacancyIds()).containsExactly(invalidId);
        assertThat(plan.legacyToLegacyCollisionGroups()).hasSize(1);
        assertThat(plan.legacyToLegacyCollisionGroups().get(0).vacancyIds())
                .containsExactlyInAnyOrder(collisionFirst, collisionSecond);
        assertThat(plan.legacyToCurrentCollisions()).hasSize(1);
        assertThat(plan.legacyToCurrentCollisions().get(0).vacancyId()).isEqualTo(currentCollisionId);
        assertThat(plan.legacyToCurrentCollisions().get(0).currentOwnerVacancyId()).isEqualTo(currentOwnerId);
        assertThat(plan.hasBlockers()).isTrue();
    }

    @Test
    void plan_isDeterministic_acrossDifferentBatchSizes() {
        String collisionBase = "https://example.com/jobs/" + UUID.randomUUID();
        UUID collisionFirst = UUID.randomUUID();
        UUID collisionSecond = UUID.randomUUID();
        UUID safeId = UUID.randomUUID();
        UUID invalidId = UUID.randomUUID();
        List<LegacyVacancyUrlRow> rows = List.of(
                new LegacyVacancyUrlRow(collisionFirst, collisionBase),
                new LegacyVacancyUrlRow(collisionSecond, collisionBase + "?utm_source=linkedin"),
                new LegacyVacancyUrlRow(safeId, "https://example.com/jobs/" + UUID.randomUUID()),
                new LegacyVacancyUrlRow(invalidId, "not a url"));

        VacancyCanonicalUrlLegacyPlan small = planWithBatchSize(rows, 1);
        VacancyCanonicalUrlLegacyPlan large = planWithBatchSize(rows, 1000);

        assertThat(large.totalLegacyRows()).isEqualTo(small.totalLegacyRows());
        assertThat(large.safeAssignments()).containsExactlyInAnyOrderElementsOf(small.safeAssignments());
        assertThat(large.invalidSourceUrlVacancyIds()).containsExactlyInAnyOrderElementsOf(small.invalidSourceUrlVacancyIds());
        assertThat(large.legacyToLegacyCollisionGroups()).isEqualTo(small.legacyToLegacyCollisionGroups());
        assertThat(large.hasBlockers()).isEqualTo(small.hasBlockers());
        // scannedBatchCount legitimately differs with batch size.
        assertThat(small.scannedBatchCount()).isEqualTo(4);
        assertThat(large.scannedBatchCount()).isEqualTo(1);
    }

    private VacancyCanonicalUrlLegacyPlan planWithBatchSize(List<LegacyVacancyUrlRow> rows, int batchSize) {
        VacancyRepository repository = org.mockito.Mockito.mock(VacancyRepository.class);
        stubLegacyRows(repository, rows);
        when(repository.findPopulatedCanonicalUrlRows()).thenReturn(List.of());
        return new VacancyCanonicalUrlLegacyPlanner(repository).plan(batchSize);
    }

    private void stubLegacyRows(List<LegacyVacancyUrlRow> allRows) {
        stubLegacyRows(vacancyRepository, allRows);
    }

    private void stubLegacyRows(VacancyRepository repository, List<LegacyVacancyUrlRow> allRows) {
        when(repository.findLegacyCanonicalUrlRows(any(Pageable.class))).thenAnswer(invocation -> {
            Pageable pageable = invocation.getArgument(0);
            int start = (int) pageable.getOffset();
            if (start >= allRows.size()) {
                return List.of();
            }
            int end = Math.min(start + pageable.getPageSize(), allRows.size());
            return allRows.subList(start, end);
        });
    }
}
