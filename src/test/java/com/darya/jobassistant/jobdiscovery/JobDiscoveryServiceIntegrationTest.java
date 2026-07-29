package com.darya.jobassistant.jobdiscovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.AbstractIntegrationTest;
import com.darya.jobassistant.candidates.CandidateProfileProvider;
import com.darya.jobassistant.integrations.jobsearch.DiscoveredJobReference;
import com.darya.jobassistant.integrations.jobsearch.JobPageContent;
import com.darya.jobassistant.integrations.jobsearch.JobPageFetchPort;
import com.darya.jobassistant.integrations.jobsearch.JobSearchPort;
import com.darya.jobassistant.integrations.jobsearch.JobSearchRequest;
import com.darya.jobassistant.jobdiscovery.config.JobDiscoveryProperties;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import com.darya.jobassistant.vacancies.service.VacancyIngestionService;
import com.darya.jobassistant.vacancyextraction.VacancyExtractionService;
import com.darya.jobassistant.vacancyextraction.model.ExtractedVacancyData;
import com.darya.jobassistant.vacancyextraction.model.RemotePolicy;
import com.darya.jobassistant.vacancyextraction.port.VacancyExtractionPort;
import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Real-PostgreSQL counterpart of {@link JobDiscoveryServiceTest}: proves the transaction,
 * persisted-identity pre-check, and canonical-conflict-safety guarantees actually hold against
 * this project's real schema and real {@link VacancyIngestionService}/{@code
 * VacancyCreationService} transaction boundary - none of which a pure-mock unit test can prove.
 * {@link JobSearchPort}, {@link JobPageFetchPort}, and {@link VacancyExtractionPort} are replaced
 * with {@link MockitoBean} mocks so this test never calls a real provider, matching every other
 * automated test in this project.
 *
 * <p>Every test uses its own freshly-generated URL (never a shared constant): unlike every other
 * {@code AbstractIntegrationTest} subclass in this project (which has exactly one {@code @Test}
 * method), this class has several, all sharing the same real, non-rolled-back Postgres schema
 * across the whole class - a shared URL would let one test's committed row leak into another's
 * "must not already exist" assumption.
 */
@TestPropertySource(properties = {
        "job-discovery.enabled=true",
        "job-discovery.execution.max-queries-per-run=3",
        "job-discovery.execution.max-scrapes-per-run=5",
        "job-discovery.execution.max-extractions-per-run=5",
        "job-discovery.execution.max-unique-references-per-run=30",
        "job-discovery.execution.max-reported-issues=50"
})
class JobDiscoveryServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JobDiscoveryService jobDiscoveryService;

    @Autowired
    private CandidateProfileProvider candidateProfileProvider;

    @MockitoBean
    private JobSearchQueryPlanner queryPlanner;

    @Autowired
    private VacancyIngestionService vacancyIngestionService;

    @Autowired
    private VacancyExtractionService vacancyExtractionService;

    @Autowired
    private VacancyRepository vacancyRepository;

    @Autowired
    private JobDiscoveryProperties properties;

    @Autowired
    private Clock clock;

    @MockitoBean
    private JobSearchPort jobSearchPort;

    @MockitoBean
    private JobPageFetchPort jobPageFetchPort;

    @MockitoBean
    private VacancyExtractionPort vacancyExtractionPort;

    @Test
    void run_persistsANewVacancyThroughTheRealTransactionBoundary() {
        String url = uniqueUrl();
        stubSingleDiscoveredReference(url);

        JobDiscoveryRunResult result = jobDiscoveryService.runDiscovery();

        assertThat(result.createdVacancies()).isEqualTo(1);
        assertThat(result.createdVacancyIds()).hasSize(1);
        assertThat(vacancyRepository.findByUrl(url)).isPresent();
        assertThat(vacancyRepository.findByUrl(url).orElseThrow().getTitle()).isEqualTo("Senior Java Backend Engineer");
    }

    @Test
    void run_repeatedRun_skipsPersistedIdentityBeforeScrapeAndCreatesNoDuplicate() {
        String url = uniqueUrl();
        stubSingleDiscoveredReference(url);
        JobDiscoveryRunResult first = jobDiscoveryService.runDiscovery();
        assertThat(first.createdVacancies()).isEqualTo(1);

        JobDiscoveryRunResult second = jobDiscoveryService.runDiscovery();

        assertThat(second.createdVacancies()).isZero();
        assertThat(second.existingVacanciesSkipped()).isEqualTo(1);
        assertThat(second.scrapeAttempts()).isZero();
        assertThat(second.extractionAttempts()).isZero();
        // The provider mocks were called exactly once in total (during the first run) - the
        // second run's pre-check prevented any repeat external call.
        verify(jobPageFetchPort, times(1)).fetch(any());
        verify(vacancyExtractionPort, times(1)).extract(any());
        assertThat(vacancyRepository.findAll().stream().filter(v -> url.equals(v.getUrl())).count()).isEqualTo(1);
    }

    @Test
    void run_preCheckFalseNegative_stillResolvesToAlreadyExistsWithoutADuplicateRow() {
        // Simulates a lost pre-check race: another transaction's row is already committed by the
        // time this run's own persistence attempt happens, even though this run's own (here,
        // stubbed-stale) pre-check reported "not found". Correctness must still come from the
        // real, transactional VacancyCreationService lookup-then-insert, not from the pre-check.
        String url = uniqueUrl();
        stubSingleDiscoveredReference(url);
        VacancyRepository staleAlwaysFalsePreCheck = mock(VacancyRepository.class);
        when(staleAlwaysFalsePreCheck.existsByCanonicalUrl(any())).thenReturn(false);
        JobDiscoveryService serviceWithStalePreCheck = new JobDiscoveryService(
                candidateProfileProvider, queryPlanner, jobSearchPort, jobPageFetchPort,
                vacancyExtractionService, vacancyIngestionService, staleAlwaysFalsePreCheck, properties, clock);

        // The winning run: commits a real row for this canonical URL first.
        JobDiscoveryRunResult winnerRun = jobDiscoveryService.runDiscovery();
        assertThat(winnerRun.createdVacancies()).isEqualTo(1);

        JobDiscoveryRunResult loserRun = serviceWithStalePreCheck.runDiscovery();

        assertThat(loserRun.createdVacancies()).isZero();
        assertThat(loserRun.alreadyExistingAfterRace()).isEqualTo(1);
        assertThat(loserRun.persistenceFailures()).isZero();
        assertThat(vacancyRepository.findAll().stream().filter(v -> url.equals(v.getUrl())).count()).isEqualTo(1);
    }

    @Test
    void run_failedPersistenceDoesNotAffectPreviouslyPersistedCandidates() {
        String firstUrl = uniqueUrl();
        String secondUrl = uniqueUrl();
        DiscoveredJobReference first = new DiscoveredJobReference(URI.create(firstUrl), "First Job", "First snippet");
        DiscoveredJobReference second = new DiscoveredJobReference(URI.create(secondUrl), "Second Job", "Second snippet");
        when(queryPlanner.plan(any())).thenReturn(List.of(new JobSearchRequest("query", 10)));
        when(jobSearchPort.search(any())).thenReturn(List.of(first, second));
        when(jobPageFetchPort.fetch(URI.create(firstUrl))).thenReturn(new JobPageContent(URI.create(firstUrl), "content one"));
        when(jobPageFetchPort.fetch(URI.create(secondUrl))).thenReturn(new JobPageContent(URI.create(secondUrl), "content two"));
        when(vacancyExtractionPort.extract(any()))
                .thenReturn(extractedData("First Co"))
                .thenReturn(new ExtractedVacancyData(null, "Second Co", null, RemotePolicy.UNSPECIFIED, List.of(), List.of(),
                        null, null, null, null, null));

        JobDiscoveryRunResult result = jobDiscoveryService.runDiscovery();

        assertThat(result.createdVacancies()).isEqualTo(1);
        assertThat(result.extractionFailures()).isEqualTo(1);
        assertThat(vacancyRepository.findByUrl(firstUrl)).isPresent();
        assertThat(vacancyRepository.findByUrl(secondUrl)).isEmpty();
    }

    private String uniqueUrl() {
        return "https://example.com/jobs/" + UUID.randomUUID();
    }

    private void stubSingleDiscoveredReference(String url) {
        when(queryPlanner.plan(any())).thenReturn(List.of(new JobSearchRequest("query", 10)));
        when(jobSearchPort.search(any())).thenReturn(List.of(
                new DiscoveredJobReference(URI.create(url), "Senior Java Backend Engineer (discovered)", "A great remote role")));
        when(jobPageFetchPort.fetch(URI.create(url)))
                .thenReturn(new JobPageContent(URI.create(url), "# Senior Java Backend Engineer\n\nDescription..."));
        when(vacancyExtractionPort.extract(any())).thenReturn(extractedData("Integration Test Co"));
    }

    private ExtractedVacancyData extractedData(String company) {
        return new ExtractedVacancyData(
                "Senior Java Backend Engineer", company, "Remote", RemotePolicy.REMOTE,
                List.of("B2B"), List.of("Java", "Kafka"), null, null, null, null, null);
    }
}
