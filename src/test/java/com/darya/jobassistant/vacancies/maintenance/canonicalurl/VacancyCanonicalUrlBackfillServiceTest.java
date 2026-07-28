package com.darya.jobassistant.vacancies.maintenance.canonicalurl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

/**
 * Pure unit tests for {@link VacancyCanonicalUrlBackfillService} - {@link VacancyRepository} and
 * {@link PlatformTransactionManager} are mocked, so none of this requires a database. {@code
 * transactionManager} is stubbed just enough for {@code TransactionTemplate} to actually invoke
 * its callback (returning a mock {@link TransactionStatus} and no-op commit/rollback) - real
 * physical transaction/rollback behavior is proven separately by the Testcontainers-backed
 * real-PostgreSQL tests.
 */
@ExtendWith(MockitoExtension.class)
class VacancyCanonicalUrlBackfillServiceTest {

    @Mock
    private VacancyRepository vacancyRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
    }

    // ---- DRY_RUN ----

    @Test
    void dryRun_reportsAllSafeAssignments() {
        stubLegacyRows(List.of(
                new LegacyVacancyUrlRow(UUID.randomUUID(), "https://example.com/jobs/" + UUID.randomUUID()),
                new LegacyVacancyUrlRow(UUID.randomUUID(), "https://example.com/jobs/" + UUID.randomUUID())));
        stubNoPopulatedUrls();

        VacancyCanonicalUrlBackfillResult result = service(500).dryRun();

        assertThat(result.mode()).isEqualTo(VacancyCanonicalUrlBackfillMode.DRY_RUN);
        assertThat(result.legacyRowsScanned()).isEqualTo(2);
        assertThat(result.plannedAssignments()).isEqualTo(2);
    }

    @Test
    void dryRun_invokesNoRepositoryUpdate() {
        stubLegacyRows(List.of(new LegacyVacancyUrlRow(UUID.randomUUID(), "https://example.com/jobs/1")));
        stubNoPopulatedUrls();

        service(500).dryRun();

        verify(vacancyRepository, never()).setCanonicalUrlIfNull(any(), any());
    }

    @Test
    void dryRun_leavesDatabaseValuesUnchanged_updatedRowsZeroAndNotCommitted() {
        stubLegacyRows(List.of(new LegacyVacancyUrlRow(UUID.randomUUID(), "https://example.com/jobs/1")));
        stubNoPopulatedUrls();

        VacancyCanonicalUrlBackfillResult result = service(500).dryRun();

        // "committed" here is the domain result field (no row was ever written), not whether the
        // surrounding read-only transaction itself committed - a read-only transaction with
        // nothing to roll back is expected to complete normally.
        assertThat(result.updatedRows()).isZero();
        assertThat(result.committed()).isFalse();
        verify(vacancyRepository, never()).setCanonicalUrlIfNull(any(), any());
    }

    @Test
    void dryRun_reportsBlockers_withoutThrowingSolelyBecauseBlockersExist() {
        UUID invalidId = UUID.randomUUID();
        UUID collisionFirst = UUID.randomUUID();
        UUID collisionSecond = UUID.randomUUID();
        String collisionBase = "https://example.com/jobs/" + UUID.randomUUID();
        stubLegacyRows(List.of(
                new LegacyVacancyUrlRow(invalidId, "not a url"),
                new LegacyVacancyUrlRow(collisionFirst, collisionBase),
                new LegacyVacancyUrlRow(collisionSecond, collisionBase + "?utm_source=linkedin")));
        stubNoPopulatedUrls();

        VacancyCanonicalUrlBackfillResult result = service(500).dryRun();

        assertThat(result.invalidRows()).isEqualTo(1);
        assertThat(result.legacyCollisionGroups()).isEqualTo(1);
        assertThat(result.legacyCollisionRows()).isEqualTo(2);
        assertThat(result.plannedAssignments()).isZero();
        assertThat(result.committed()).isFalse();
    }

    // ---- APPLY ----

    @Test
    void apply_updatesAllSafeRows() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        stubLegacyRows(List.of(
                new LegacyVacancyUrlRow(first, "https://example.com/jobs/" + UUID.randomUUID()),
                new LegacyVacancyUrlRow(second, "https://example.com/jobs/" + UUID.randomUUID())));
        stubNoPopulatedUrls();
        when(vacancyRepository.setCanonicalUrlIfNull(any(), any())).thenReturn(1);

        VacancyCanonicalUrlBackfillResult result = service(500).apply();

        assertThat(result.updatedRows()).isEqualTo(2);
        verify(vacancyRepository, times(2)).setCanonicalUrlIfNull(any(), any());
    }

    @Test
    void apply_preservesOriginalUrl_updateCallNeverReceivesTheRawUrl() {
        UUID vacancyId = UUID.randomUUID();
        String rawUrl = "HTTPS://EXAMPLE.COM:443/jobs/123/?utm_source=linkedin";
        stubLegacyRows(List.of(new LegacyVacancyUrlRow(vacancyId, rawUrl)));
        stubNoPopulatedUrls();
        when(vacancyRepository.setCanonicalUrlIfNull(any(), any())).thenReturn(1);

        service(500).apply();

        // setCanonicalUrlIfNull's own signature takes only (vacancyId, canonicalUrl) - url is
        // structurally impossible to pass, and the second argument here proves it received the
        // canonicalized value, never the raw one.
        verify(vacancyRepository).setCanonicalUrlIfNull(vacancyId, "https://example.com/jobs/123");
    }

    @Test
    void apply_canonicalUrl_isExactlyTheVacancyUrlCanonicalizerResult() {
        UUID vacancyId = UUID.randomUUID();
        stubLegacyRows(List.of(new LegacyVacancyUrlRow(vacancyId, "http://EXAMPLE.com/jobs/123?utm_campaign=x&language=en")));
        stubNoPopulatedUrls();
        when(vacancyRepository.setCanonicalUrlIfNull(any(), any())).thenReturn(1);

        service(500).apply();

        verify(vacancyRepository).setCanonicalUrlIfNull(eq(vacancyId), eq("http://example.com/jobs/123?language=en"));
    }

    @Test
    void apply_noBlockers_producesSuccessfulCommittedResult() {
        stubLegacyRows(List.of(new LegacyVacancyUrlRow(UUID.randomUUID(), "https://example.com/jobs/1")));
        stubNoPopulatedUrls();
        when(vacancyRepository.setCanonicalUrlIfNull(any(), any())).thenReturn(1);

        VacancyCanonicalUrlBackfillResult result = service(500).apply();

        assertThat(result.committed()).isTrue();
        assertThat(result.updatedRows()).isEqualTo(result.plannedAssignments());
        verify(transactionManager).commit(transactionStatus);
        verify(transactionManager, never()).rollback(any());
    }

    @Test
    void apply_anyInvalidUrl_blocksTheEntireApply_withZeroUpdates() {
        stubLegacyRows(List.of(
                new LegacyVacancyUrlRow(UUID.randomUUID(), "not a url"),
                new LegacyVacancyUrlRow(UUID.randomUUID(), "https://example.com/jobs/1")));
        stubNoPopulatedUrls();

        assertThatThrownBy(() -> service(500).apply())
                .isInstanceOf(VacancyCanonicalUrlBackfillBlockedException.class);

        verify(vacancyRepository, never()).setCanonicalUrlIfNull(any(), any());
        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(any());
    }

    @Test
    void apply_anyLegacyToLegacyCollision_blocksTheCompleteApply() {
        String base = "https://example.com/jobs/" + UUID.randomUUID();
        stubLegacyRows(List.of(
                new LegacyVacancyUrlRow(UUID.randomUUID(), base),
                new LegacyVacancyUrlRow(UUID.randomUUID(), base + "?utm_source=linkedin")));
        stubNoPopulatedUrls();

        VacancyCanonicalUrlBackfillBlockedException exception = (VacancyCanonicalUrlBackfillBlockedException)
                catchThrowable(() -> service(500).apply());

        assertThat(exception).isNotNull();
        assertThat(exception.legacyToLegacyCollisionRowCount()).isEqualTo(2);
        verify(vacancyRepository, never()).setCanonicalUrlIfNull(any(), any());
    }

    @Test
    void apply_anyLegacyToCurrentCollision_blocksTheCompleteApply() {
        String url = "https://example.com/jobs/" + UUID.randomUUID();
        stubLegacyRows(List.of(new LegacyVacancyUrlRow(UUID.randomUUID(), url)));
        when(vacancyRepository.findPopulatedCanonicalUrlRows())
                .thenReturn(List.of(new PopulatedCanonicalUrlRow(url, UUID.randomUUID())));

        VacancyCanonicalUrlBackfillBlockedException exception = (VacancyCanonicalUrlBackfillBlockedException)
                catchThrowable(() -> service(500).apply());

        assertThat(exception).isNotNull();
        assertThat(exception.legacyToCurrentCollisionRowCount()).isEqualTo(1);
        verify(vacancyRepository, never()).setCanonicalUrlIfNull(any(), any());
    }

    @Test
    void apply_oneConditionalUpdateReturningZero_rollsBackAllPriorUpdatesFromThisRun() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        stubLegacyRows(List.of(
                new LegacyVacancyUrlRow(first, "https://example.com/jobs/" + UUID.randomUUID()),
                new LegacyVacancyUrlRow(second, "https://example.com/jobs/" + UUID.randomUUID())));
        stubNoPopulatedUrls();
        when(vacancyRepository.setCanonicalUrlIfNull(any(), any())).thenReturn(1, 0);

        assertThatThrownBy(() -> service(500).apply())
                .isInstanceOf(VacancyCanonicalUrlBackfillInvariantViolationException.class);

        verify(vacancyRepository, times(2)).setCanonicalUrlIfNull(any(), any());
        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(any());
    }

    @Test
    void apply_unexpectedUniqueIndexViolation_rollsBackAllUpdates() {
        stubLegacyRows(List.of(new LegacyVacancyUrlRow(UUID.randomUUID(), "https://example.com/jobs/1")));
        stubNoPopulatedUrls();
        when(vacancyRepository.setCanonicalUrlIfNull(any(), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint \"uk_vacancy_canonical_url\""));

        assertThatThrownBy(() -> service(500).apply())
                .isInstanceOf(VacancyCanonicalUrlBackfillInvariantViolationException.class);

        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(any());
    }

    @Test
    void apply_updatedRowCount_mustEqualPlannedAssignmentCount() {
        stubLegacyRows(List.of(
                new LegacyVacancyUrlRow(UUID.randomUUID(), "https://example.com/jobs/" + UUID.randomUUID()),
                new LegacyVacancyUrlRow(UUID.randomUUID(), "https://example.com/jobs/" + UUID.randomUUID()),
                new LegacyVacancyUrlRow(UUID.randomUUID(), "https://example.com/jobs/" + UUID.randomUUID())));
        stubNoPopulatedUrls();
        when(vacancyRepository.setCanonicalUrlIfNull(any(), any())).thenReturn(1);

        VacancyCanonicalUrlBackfillResult result = service(500).apply();

        assertThat(result.updatedRows()).isEqualTo(result.plannedAssignments());
        assertThat(result.updatedRows()).isEqualTo(3);
    }

    @Test
    void apply_secondRunAfterSuccess_isAnIdempotentSuccessfulNoOp() {
        // Simulates the state right after a successful APPLY: no more canonical_url IS NULL rows.
        stubLegacyRows(List.of());
        stubNoPopulatedUrls();

        VacancyCanonicalUrlBackfillResult result = service(500).apply();

        assertThat(result.legacyRowsScanned()).isZero();
        assertThat(result.plannedAssignments()).isZero();
        assertThat(result.updatedRows()).isZero();
        assertThat(result.committed()).isTrue();
        verify(vacancyRepository, never()).setCanonicalUrlIfNull(any(), any());
    }

    private VacancyCanonicalUrlBackfillService service(int batchSize) {
        return new VacancyCanonicalUrlBackfillService(
                vacancyRepository,
                new VacancyCanonicalUrlBackfillProperties(true, VacancyCanonicalUrlBackfillMode.APPLY, batchSize),
                transactionManager);
    }

    private void stubNoPopulatedUrls() {
        lenient().when(vacancyRepository.findPopulatedCanonicalUrlRows()).thenReturn(List.of());
    }

    private void stubLegacyRows(List<LegacyVacancyUrlRow> allRows) {
        when(vacancyRepository.findLegacyCanonicalUrlRows(any(Pageable.class))).thenAnswer(invocation -> {
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
