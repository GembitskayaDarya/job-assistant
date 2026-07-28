package com.darya.jobassistant.vacancies.maintenance.canonicalurl;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.companies.repository.CompanyRepository;
import com.darya.jobassistant.config.JpaAuditingConfig;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import com.darya.jobassistant.vacancies.url.VacancyUrlCanonicalizer;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Real-PostgreSQL counterpart of {@link VacancyCanonicalUrlAuditServiceTest}: proves the
 * projection queries and the whole {@link VacancyCanonicalUrlAuditService#audit()} scan behave
 * correctly against this project's actual schema (in particular {@code
 * uk_vacancy_canonical_url}'s partial-unique-index semantics for legacy {@code NULL} rows), and
 * that running the audit truly never writes anything.
 *
 * <p>Pinned to Flyway target {@code 12} ({@code spring.flyway.target=12}), not {@code latest}:
 * since Sprint 8 Step 4B2D (migration V13), {@code canonical_url} is {@code NOT NULL} at the
 * database level, so a "legacy row with {@code canonical_url IS NULL}" - the entire premise of
 * this audit tool and every test in this class - can no longer exist in a database migrated past
 * V12. This class specifically exercises the tool an operator runs <em>against a V12 database
 * that has not yet been backfilled</em>, so its own test database must stay at V12 to match that
 * real scenario, regardless of what later migrations this project's "latest" schema has gained.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(JpaAuditingConfig.class)
@TestPropertySource(properties = "spring.flyway.target=12")
class VacancyCanonicalUrlAuditRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private VacancyRepository vacancyRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Test
    void findLegacyCanonicalUrlRows_returnsOnlyCanonicalUrlIsNullRows() {
        Company company = company();
        Vacancy legacy = legacyVacancy(company, uniqueUrl());
        Vacancy current = currentVacancy(company, uniqueUrl());
        vacancyRepository.save(legacy);
        vacancyRepository.save(current);

        List<LegacyVacancyUrlRow> rows = vacancyRepository.findLegacyCanonicalUrlRows(PageRequest.of(0, 500));

        assertThat(rows).extracting(LegacyVacancyUrlRow::vacancyId).contains(legacy.getId());
        assertThat(rows).extracting(LegacyVacancyUrlRow::vacancyId).doesNotContain(current.getId());
    }

    @Test
    void findLegacyCanonicalUrlRows_selectsOnlyIdAndUrl_notCompanyOrOtherColumns() {
        Company company = company();
        String url = uniqueUrl();
        Vacancy legacy = legacyVacancy(company, url);
        legacy.setDescription("should never be fetched by this projection");
        vacancyRepository.save(legacy);

        List<LegacyVacancyUrlRow> rows = vacancyRepository.findLegacyCanonicalUrlRows(PageRequest.of(0, 500));

        LegacyVacancyUrlRow row = rows.stream().filter(r -> r.vacancyId().equals(legacy.getId())).findFirst().orElseThrow();
        assertThat(row.sourceUrl()).isEqualTo(url);
        // LegacyVacancyUrlRow itself has exactly these two components - no company, description,
        // salary, or any other column is even representable on the projection, let alone fetched.
        assertThat(LegacyVacancyUrlRow.class.getRecordComponents()).hasSize(2);
    }

    @Test
    void findPopulatedCanonicalUrlRows_readsExistingNonNullCanonicalUrlsCorrectly() {
        Company company = company();
        String url = uniqueUrl();
        Vacancy current = currentVacancy(company, url);
        vacancyRepository.save(current);
        vacancyRepository.save(legacyVacancy(company, uniqueUrl()));

        List<PopulatedCanonicalUrlRow> populated = vacancyRepository.findPopulatedCanonicalUrlRows();

        assertThat(populated).extracting(PopulatedCanonicalUrlRow::canonicalUrl)
                .contains(canonicalize(url));
        assertThat(populated).filteredOn(r -> r.canonicalUrl().equals(canonicalize(url)))
                .extracting(PopulatedCanonicalUrlRow::vacancyId)
                .containsExactly(current.getId());
    }

    @Test
    void audit_pagesThroughAllLegacyRowsExactlyOnce_withASmallBatchSize() {
        Company company = company();
        int rowCount = 7;
        for (int i = 0; i < rowCount; i++) {
            vacancyRepository.save(legacyVacancy(company, uniqueUrl()));
        }

        VacancyCanonicalUrlAuditReport report = auditService(2, 100).audit();

        assertThat(report.totalLegacyRows()).isEqualTo(rowCount);
        assertThat(report.scannedBatchCount()).isEqualTo(4);
        int classified = report.safeToBackfillRows() + report.invalidSourceUrlRows()
                + report.legacyToLegacyCollisionRows() + report.legacyToCurrentCollisionRows();
        assertThat(classified).isEqualTo(report.totalLegacyRows());
    }

    @Test
    void audit_realPostgresCosmeticUrlVariantCollision_matchesUnitTestSemantics() {
        Company company = company();
        String suffix = UUID.randomUUID().toString();
        String base = "https://example.com/jobs/" + suffix;
        Vacancy first = legacyVacancy(company, base);
        Vacancy second = legacyVacancy(company, "HTTPS://EXAMPLE.COM:443/jobs/" + suffix + "/?utm_source=linkedin");
        vacancyRepository.save(first);
        vacancyRepository.save(second);

        VacancyCanonicalUrlAuditReport report = auditService(500, 100).audit();

        boolean bothClassifiedAsColliding = report.issues().stream()
                .filter(issue -> issue.vacancyId().equals(first.getId()) || issue.vacancyId().equals(second.getId()))
                .allMatch(issue -> issue.issueType() == VacancyCanonicalUrlAuditIssueType.LEGACY_TO_LEGACY_COLLISION);
        assertThat(bothClassifiedAsColliding).isTrue();
        assertThat(report.issues().stream().filter(issue -> issue.vacancyId().equals(first.getId())
                || issue.vacancyId().equals(second.getId())).count()).isEqualTo(2);
    }

    @Test
    void audit_performsNoUpdateOrDelete_databaseValuesRemainByteForByteUnchanged() {
        Company company = company();
        Vacancy legacy = legacyVacancy(company, uniqueUrl());
        vacancyRepository.save(legacy);
        UUID vacancyId = legacy.getId();
        Vacancy before = vacancyRepository.findById(vacancyId).orElseThrow();
        String titleBefore = before.getTitle();
        String urlBefore = before.getUrl();
        String canonicalUrlBefore = before.getCanonicalUrl();
        var updatedAtBefore = before.getUpdatedAt();
        long countBefore = vacancyRepository.count();

        auditService(500, 100).audit();

        Optional<Vacancy> after = vacancyRepository.findById(vacancyId);
        assertThat(after).isPresent();
        assertThat(after.get().getTitle()).isEqualTo(titleBefore);
        assertThat(after.get().getUrl()).isEqualTo(urlBefore);
        assertThat(after.get().getCanonicalUrl()).isEqualTo(canonicalUrlBefore);
        assertThat(after.get().getUpdatedAt()).isEqualTo(updatedAtBefore);
        assertThat(vacancyRepository.count()).isEqualTo(countBefore);
    }

    private VacancyCanonicalUrlAuditService auditService(int batchSize, int maxReportedIssues) {
        return new VacancyCanonicalUrlAuditService(
                vacancyRepository, new VacancyCanonicalUrlAuditProperties(true, batchSize, maxReportedIssues));
    }

    private String canonicalize(String rawUrl) {
        return VacancyUrlCanonicalizer.canonicalize(URI.create(rawUrl)).value();
    }

    private Company company() {
        return companyRepository.save(Company.builder().name("Acme-" + UUID.randomUUID()).build());
    }

    private Vacancy legacyVacancy(Company company, String url) {
        return Vacancy.builder()
                .company(company).title("Backend Engineer").description("Build backend services")
                .url(url).canonicalUrl(null).source("remoteok").build();
    }

    private Vacancy currentVacancy(Company company, String url) {
        return Vacancy.builder()
                .company(company).title("Backend Engineer").description("Build backend services")
                .url(url).canonicalUrl(canonicalize(url)).source("remoteok").build();
    }

    private String uniqueUrl() {
        return "https://example.com/job-" + UUID.randomUUID();
    }
}
