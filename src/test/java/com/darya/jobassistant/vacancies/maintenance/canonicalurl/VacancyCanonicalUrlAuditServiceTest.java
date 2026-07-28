package com.darya.jobassistant.vacancies.maintenance.canonicalurl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pure unit tests for {@link VacancyCanonicalUrlAuditService} - {@link VacancyRepository} is
 * mocked, so none of this requires a database, Docker, or any external service. {@link
 * #stubLegacyRows} pages through an in-memory list the same way a real repository would, given the
 * {@link Pageable} the service passes, which is what makes {@link
 * #audit_classificationIsDeterministic_acrossDifferentBatchSizes} and {@link
 * #audit_safeClassification_onlyHappensAfterTheCompleteScan} meaningful.
 */
@ExtendWith(MockitoExtension.class)
class VacancyCanonicalUrlAuditServiceTest {

    @Mock
    private VacancyRepository vacancyRepository;

    @BeforeEach
    void setUp() {
        lenient().when(vacancyRepository.findPopulatedCanonicalUrlRows()).thenReturn(List.of());
    }

    @Test
    void audit_isDeclaredReadOnlyRepeatableRead_soItCanOnlyReadAConsistentSnapshot() throws NoSuchMethodException {
        Transactional annotation = VacancyCanonicalUrlAuditService.class.getMethod("audit").getAnnotation(Transactional.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.readOnly()).isTrue();
        assertThat(annotation.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
    }

    @Test
    void constructor_onlyDependsOnTheRepositoryAndItsOwnProperties_needsNoExternalService() {
        Constructor<?>[] constructors = VacancyCanonicalUrlAuditService.class.getDeclaredConstructors();

        assertThat(constructors).hasSize(1);
        assertThat(constructors[0].getParameterTypes())
                .containsExactly(VacancyRepository.class, VacancyCanonicalUrlAuditProperties.class);
    }

    @Test
    void audit_noLegacyRows_returnsEmptySuccessfulReport() {
        stubLegacyRows(List.of());

        VacancyCanonicalUrlAuditReport report = service(500, 100).audit();

        assertThat(report.totalLegacyRows()).isZero();
        assertThat(report.safeToBackfillRows()).isZero();
        assertThat(report.invalidSourceUrlRows()).isZero();
        assertThat(report.legacyToLegacyCollisionGroups()).isZero();
        assertThat(report.legacyToLegacyCollisionRows()).isZero();
        assertThat(report.legacyToCurrentCollisionRows()).isZero();
        assertThat(report.scannedBatchCount()).isZero();
        assertThat(report.omittedIssueCount()).isZero();
        assertThat(report.issues()).isEmpty();
        assertThat(report.issuesTruncated()).isFalse();
    }

    @Test
    void audit_oneCanonicalizableUniqueLegacyRow_isSafeToBackfill() {
        UUID vacancyId = UUID.randomUUID();
        stubLegacyRows(List.of(new LegacyVacancyUrlRow(vacancyId, "https://example.com/jobs/123")));

        VacancyCanonicalUrlAuditReport report = service(500, 100).audit();

        assertThat(report.totalLegacyRows()).isEqualTo(1);
        assertThat(report.safeToBackfillRows()).isEqualTo(1);
        assertThat(report.issues()).isEmpty();
    }

    @Test
    void audit_invalidSourceUrl_isClassifiedWithoutAbortingTheAudit() {
        UUID invalidId = UUID.randomUUID();
        UUID validId = UUID.randomUUID();
        stubLegacyRows(List.of(
                new LegacyVacancyUrlRow(invalidId, "not a url"),
                new LegacyVacancyUrlRow(validId, "https://example.com/jobs/123")));

        VacancyCanonicalUrlAuditReport report = service(500, 100).audit();

        assertThat(report.totalLegacyRows()).isEqualTo(2);
        assertThat(report.invalidSourceUrlRows()).isEqualTo(1);
        assertThat(report.safeToBackfillRows()).isEqualTo(1);
        assertThat(report.issues()).hasSize(1);
        VacancyCanonicalUrlAuditIssue issue = report.issues().get(0);
        assertThat(issue.vacancyId()).isEqualTo(invalidId);
        assertThat(issue.issueType()).isEqualTo(VacancyCanonicalUrlAuditIssueType.INVALID_SOURCE_URL);
        assertThat(issue.canonicalCandidate()).isNull();
        assertThat(issue.relatedVacancyIds()).isEmpty();
    }

    @Test
    void audit_twoLegacyRowsWithCosmeticUrlVariants_formOneCollisionGroup() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        stubLegacyRows(List.of(
                new LegacyVacancyUrlRow(firstId, "https://example.com/jobs/123?utm_source=linkedin"),
                new LegacyVacancyUrlRow(secondId, "HTTPS://EXAMPLE.COM:443/jobs/123/")));

        VacancyCanonicalUrlAuditReport report = service(500, 100).audit();

        assertThat(report.legacyToLegacyCollisionGroups()).isEqualTo(1);
        assertThat(report.legacyToLegacyCollisionRows()).isEqualTo(2);
        assertThat(report.safeToBackfillRows()).isZero();
        assertThat(report.issues()).hasSize(2)
                .allSatisfy(issue -> assertThat(issue.issueType()).isEqualTo(VacancyCanonicalUrlAuditIssueType.LEGACY_TO_LEGACY_COLLISION));
        VacancyCanonicalUrlAuditIssue firstIssue = issueFor(report, firstId);
        assertThat(firstIssue.relatedVacancyIds()).containsExactly(secondId);
        VacancyCanonicalUrlAuditIssue secondIssue = issueFor(report, secondId);
        assertThat(secondIssue.relatedVacancyIds()).containsExactly(firstId);
    }

    @Test
    void audit_threeLegacyRowsSharingOneCanonicalIdentity_isOneGroupOfThreeRows() {
        String base = "https://example.com/jobs/" + UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        stubLegacyRows(List.of(
                new LegacyVacancyUrlRow(first, base),
                new LegacyVacancyUrlRow(second, base + "?utm_source=linkedin"),
                new LegacyVacancyUrlRow(third, base + "/#top")));

        VacancyCanonicalUrlAuditReport report = service(500, 100).audit();

        assertThat(report.legacyToLegacyCollisionGroups()).isEqualTo(1);
        assertThat(report.legacyToLegacyCollisionRows()).isEqualTo(3);
        assertThat(report.issues()).hasSize(3);
    }

    @Test
    void audit_legacyRowMatchingAPopulatedCanonicalUrl_isLegacyToCurrentCollision() {
        UUID legacyId = UUID.randomUUID();
        UUID currentOwnerId = UUID.randomUUID();
        String url = "https://example.com/jobs/" + UUID.randomUUID();
        stubLegacyRows(List.of(new LegacyVacancyUrlRow(legacyId, url)));
        when(vacancyRepository.findPopulatedCanonicalUrlRows())
                .thenReturn(List.of(new PopulatedCanonicalUrlRow(url, currentOwnerId)));

        VacancyCanonicalUrlAuditReport report = service(500, 100).audit();

        assertThat(report.legacyToCurrentCollisionRows()).isEqualTo(1);
        assertThat(report.safeToBackfillRows()).isZero();
        assertThat(report.issues()).hasSize(1);
        VacancyCanonicalUrlAuditIssue issue = report.issues().get(0);
        assertThat(issue.vacancyId()).isEqualTo(legacyId);
        assertThat(issue.issueType()).isEqualTo(VacancyCanonicalUrlAuditIssueType.LEGACY_TO_CURRENT_COLLISION);
        assertThat(issue.canonicalCandidate()).isEqualTo(url);
        assertThat(issue.relatedVacancyIds()).containsExactly(currentOwnerId);
    }

    @Test
    void audit_meaningfulQueryParameters_remainDistinct() {
        String suffix = UUID.randomUUID().toString();
        stubLegacyRows(List.of(
                new LegacyVacancyUrlRow(UUID.randomUUID(), "https://example.com/jobs/" + suffix + "?language=en"),
                new LegacyVacancyUrlRow(UUID.randomUUID(), "https://example.com/jobs/" + suffix + "?language=pl")));

        VacancyCanonicalUrlAuditReport report = service(500, 100).audit();

        assertThat(report.safeToBackfillRows()).isEqualTo(2);
        assertThat(report.legacyToLegacyCollisionGroups()).isZero();
    }

    @Test
    void audit_httpAndHttps_remainDistinct() {
        String suffix = UUID.randomUUID().toString();
        stubLegacyRows(List.of(
                new LegacyVacancyUrlRow(UUID.randomUUID(), "http://example.com/jobs/" + suffix),
                new LegacyVacancyUrlRow(UUID.randomUUID(), "https://example.com/jobs/" + suffix)));

        VacancyCanonicalUrlAuditReport report = service(500, 100).audit();

        assertThat(report.safeToBackfillRows()).isEqualTo(2);
        assertThat(report.legacyToLegacyCollisionGroups()).isZero();
    }

    @Test
    void audit_classificationIsDeterministic_acrossDifferentBatchSizes() {
        String collisionBase = "https://example.com/jobs/" + UUID.randomUUID();
        String safeUrl = "https://example.com/jobs/" + UUID.randomUUID();
        String currentCollisionUrl = "https://example.com/jobs/" + UUID.randomUUID();
        UUID collisionFirst = UUID.randomUUID();
        UUID collisionSecond = UUID.randomUUID();
        UUID safeId = UUID.randomUUID();
        UUID invalidId = UUID.randomUUID();
        UUID currentCollisionId = UUID.randomUUID();
        UUID currentOwnerId = UUID.randomUUID();
        List<LegacyVacancyUrlRow> rows = List.of(
                new LegacyVacancyUrlRow(collisionFirst, collisionBase),
                new LegacyVacancyUrlRow(collisionSecond, collisionBase + "?utm_source=linkedin"),
                new LegacyVacancyUrlRow(safeId, safeUrl),
                new LegacyVacancyUrlRow(invalidId, "not a url"),
                new LegacyVacancyUrlRow(currentCollisionId, currentCollisionUrl));
        List<PopulatedCanonicalUrlRow> populated = List.of(new PopulatedCanonicalUrlRow(currentCollisionUrl, currentOwnerId));

        VacancyCanonicalUrlAuditReport small = runWithBatchSize(rows, populated, 1);
        VacancyCanonicalUrlAuditReport medium = runWithBatchSize(rows, populated, 2);
        VacancyCanonicalUrlAuditReport large = runWithBatchSize(rows, populated, 1000);

        assertThat(medium.totalLegacyRows()).isEqualTo(small.totalLegacyRows());
        assertThat(large.totalLegacyRows()).isEqualTo(small.totalLegacyRows());
        assertThat(medium.safeToBackfillRows()).isEqualTo(small.safeToBackfillRows());
        assertThat(large.safeToBackfillRows()).isEqualTo(small.safeToBackfillRows());
        assertThat(medium.invalidSourceUrlRows()).isEqualTo(small.invalidSourceUrlRows());
        assertThat(large.invalidSourceUrlRows()).isEqualTo(small.invalidSourceUrlRows());
        assertThat(medium.legacyToLegacyCollisionGroups()).isEqualTo(small.legacyToLegacyCollisionGroups());
        assertThat(large.legacyToLegacyCollisionGroups()).isEqualTo(small.legacyToLegacyCollisionGroups());
        assertThat(medium.legacyToLegacyCollisionRows()).isEqualTo(small.legacyToLegacyCollisionRows());
        assertThat(large.legacyToLegacyCollisionRows()).isEqualTo(small.legacyToLegacyCollisionRows());
        assertThat(medium.legacyToCurrentCollisionRows()).isEqualTo(small.legacyToCurrentCollisionRows());
        assertThat(large.legacyToCurrentCollisionRows()).isEqualTo(small.legacyToCurrentCollisionRows());
        // scannedBatchCount legitimately differs with batch size - everything else must not.
        assertThat(small.scannedBatchCount()).isEqualTo(5);
        assertThat(medium.scannedBatchCount()).isEqualTo(3);
        assertThat(large.scannedBatchCount()).isEqualTo(1);
        assertThat(medium.issues()).containsExactlyInAnyOrderElementsOf(small.issues());
        assertThat(large.issues()).containsExactlyInAnyOrderElementsOf(small.issues());
    }

    @Test
    void audit_safeClassification_onlyHappensAfterTheCompleteScan() {
        // batchSize=1 forces each row into its own page, so the first-scanned row would look
        // uniquely safe if the service decided anything before the whole scan finished.
        String sharedUrl = "https://example.com/jobs/" + UUID.randomUUID();
        UUID firstScanned = UUID.randomUUID();
        UUID secondScanned = UUID.randomUUID();
        stubLegacyRows(List.of(
                new LegacyVacancyUrlRow(firstScanned, sharedUrl),
                new LegacyVacancyUrlRow(secondScanned, sharedUrl + "?utm_source=linkedin")));

        VacancyCanonicalUrlAuditReport report = service(1, 100).audit();

        assertThat(report.safeToBackfillRows()).isZero();
        assertThat(report.legacyToLegacyCollisionGroups()).isEqualTo(1);
        assertThat(report.legacyToLegacyCollisionRows()).isEqualTo(2);
        assertThat(report.issues()).extracting(VacancyCanonicalUrlAuditIssue::vacancyId)
                .containsExactlyInAnyOrder(firstScanned, secondScanned);
    }

    @Test
    void audit_maxReportedIssues_limitsDetailsButNotTotals() {
        List<LegacyVacancyUrlRow> rows = List.of(
                new LegacyVacancyUrlRow(UUID.randomUUID(), "not a url 1"),
                new LegacyVacancyUrlRow(UUID.randomUUID(), "not a url 2"),
                new LegacyVacancyUrlRow(UUID.randomUUID(), "not a url 3"),
                new LegacyVacancyUrlRow(UUID.randomUUID(), "not a url 4"),
                new LegacyVacancyUrlRow(UUID.randomUUID(), "not a url 5"));
        stubLegacyRows(rows);

        VacancyCanonicalUrlAuditReport report = service(500, 2).audit();

        assertThat(report.totalLegacyRows()).isEqualTo(5);
        assertThat(report.invalidSourceUrlRows()).isEqualTo(5);
        assertThat(report.issues()).hasSize(2);
        assertThat(report.omittedIssueCount()).isEqualTo(3);
        assertThat(report.issuesTruncated()).isTrue();
    }

    @Test
    void report_andItsCollections_areImmutable() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        String base = "https://example.com/jobs/" + UUID.randomUUID();
        stubLegacyRows(List.of(
                new LegacyVacancyUrlRow(firstId, base),
                new LegacyVacancyUrlRow(secondId, base + "?utm_source=linkedin")));

        VacancyCanonicalUrlAuditReport report = service(500, 100).audit();

        assertThatThrownBy(() -> report.issues().add(report.issues().get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> report.issues().get(0).relatedVacancyIds().add(UUID.randomUUID()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void audit_oneMalformedRowAmongMany_doesNotPreventTheOthersFromBeingClassified() {
        UUID malformedId = UUID.randomUUID();
        UUID safeId = UUID.randomUUID();
        UUID collisionFirst = UUID.randomUUID();
        UUID collisionSecond = UUID.randomUUID();
        String collisionBase = "https://example.com/jobs/" + UUID.randomUUID();
        stubLegacyRows(List.of(
                new LegacyVacancyUrlRow(malformedId, "://not-a-valid-uri"),
                new LegacyVacancyUrlRow(safeId, "https://example.com/jobs/" + UUID.randomUUID()),
                new LegacyVacancyUrlRow(collisionFirst, collisionBase),
                new LegacyVacancyUrlRow(collisionSecond, collisionBase + "?utm_source=linkedin")));

        VacancyCanonicalUrlAuditReport report = service(500, 100).audit();

        assertThat(report.totalLegacyRows()).isEqualTo(4);
        assertThat(report.invalidSourceUrlRows()).isEqualTo(1);
        assertThat(report.safeToBackfillRows()).isEqualTo(1);
        assertThat(report.legacyToLegacyCollisionGroups()).isEqualTo(1);
        assertThat(report.legacyToLegacyCollisionRows()).isEqualTo(2);
    }

    @Test
    void audit_neverInvokesAnyRepositoryWriteMethod() {
        stubLegacyRows(List.of(new LegacyVacancyUrlRow(UUID.randomUUID(), "https://example.com/jobs/123")));

        service(500, 100).audit();

        verify(vacancyRepository, never()).save(any(Vacancy.class));
        verify(vacancyRepository, never()).saveIfAbsent(any(Vacancy.class));
        verify(vacancyRepository, never()).saveAll(any());
        verify(vacancyRepository, never()).delete(any(Vacancy.class));
        verify(vacancyRepository, never()).deleteById(any());
        verify(vacancyRepository, never()).deleteAll();
    }

    private VacancyCanonicalUrlAuditReport runWithBatchSize(
            List<LegacyVacancyUrlRow> rows, List<PopulatedCanonicalUrlRow> populated, int batchSize) {
        VacancyRepository repository = org.mockito.Mockito.mock(VacancyRepository.class);
        stubLegacyRows(repository, rows);
        when(repository.findPopulatedCanonicalUrlRows()).thenReturn(populated);
        return new VacancyCanonicalUrlAuditService(repository, new VacancyCanonicalUrlAuditProperties(true, batchSize, 100)).audit();
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

    private VacancyCanonicalUrlAuditIssue issueFor(VacancyCanonicalUrlAuditReport report, UUID vacancyId) {
        return report.issues().stream()
                .filter(issue -> issue.vacancyId().equals(vacancyId))
                .findFirst()
                .orElseThrow();
    }

    private VacancyCanonicalUrlAuditService service(int batchSize, int maxReportedIssues) {
        return new VacancyCanonicalUrlAuditService(
                vacancyRepository, new VacancyCanonicalUrlAuditProperties(true, batchSize, maxReportedIssues));
    }
}
