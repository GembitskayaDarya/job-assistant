package com.darya.jobassistant.jobdiscovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.CandidateProfileProvider;
import com.darya.jobassistant.integrations.jobsearch.DiscoveredJobReference;
import com.darya.jobassistant.integrations.jobsearch.JobDiscoveryException;
import com.darya.jobassistant.integrations.jobsearch.JobPageContent;
import com.darya.jobassistant.integrations.jobsearch.JobPageFetchPort;
import com.darya.jobassistant.integrations.jobsearch.JobSearchPort;
import com.darya.jobassistant.integrations.jobsearch.JobSearchRequest;
import com.darya.jobassistant.jobdiscovery.budget.JobDiscoveryBudgetDecision;
import com.darya.jobassistant.jobdiscovery.budget.JobDiscoveryBudgetPort;
import com.darya.jobassistant.jobdiscovery.budget.JobDiscoveryBudgetRequest;
import com.darya.jobassistant.jobdiscovery.budget.JobDiscoveryBudgetStatus;
import com.darya.jobassistant.jobdiscovery.config.JobDiscoveryProperties;
import com.darya.jobassistant.vacancies.dto.VacancyCreationCommand;
import com.darya.jobassistant.vacancies.dto.VacancyCreationResult;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import com.darya.jobassistant.vacancies.service.VacancyIngestionService;
import com.darya.jobassistant.vacancyextraction.VacancyExtractionService;
import com.darya.jobassistant.vacancyextraction.exception.VacancyExtractionException;
import com.darya.jobassistant.vacancyextraction.model.ExtractedVacancyData;
import com.darya.jobassistant.vacancyextraction.model.RemotePolicy;
import com.darya.jobassistant.vacancyextraction.model.VacancyExtractionRequest;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class JobDiscoveryServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-29T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private CandidateProfileProvider candidateProfileProvider;

    @Mock
    private JobSearchQueryPlanner queryPlanner;

    @Mock
    private VacancyExtractionService vacancyExtractionService;

    @Mock
    private VacancyIngestionService vacancyIngestionService;

    @Mock
    private VacancyRepository vacancyRepository;

    @Mock
    private JobDiscoveryBudgetPort budgetPort;

    private FakeJobSearchPort jobSearchPort;
    private FakeJobPageFetchPort jobPageFetchPort;
    private CandidateProfile profile;

    @BeforeEach
    void setUp() {
        jobSearchPort = new FakeJobSearchPort();
        jobPageFetchPort = new FakeJobPageFetchPort();
        profile = validProfile();
        lenient().when(candidateProfileProvider.getProfile()).thenReturn(profile);
        // Default: no existing vacancy for any canonical URL unless a test overrides it.
        lenient().when(vacancyRepository.existsByCanonicalUrl(any())).thenReturn(false);
        // Default: the budget gate is wide open unless a test stubs it otherwise, so every
        // existing (pre-budget) test keeps exercising the full search/scrape/extraction flow.
        lenient().when(budgetPort.assessBudget(any())).thenReturn(allowedDecision());
    }

    // --- Query planning and search --------------------------------------------------------

    @Test
    void run_readsProfileOnceAndInvokesPlannerOnce() {
        stubPlan(List.of(request("java backend")));
        jobSearchPort.respondWith("java backend", List.of());
        JobDiscoveryService service = service(execution(3, 5, 5, 30, 50));

        service.runDiscovery();

        verify(candidateProfileProvider, times(1)).getProfile();
        verify(queryPlanner, times(1)).plan(profile);
    }

    @Test
    void run_preservesPlannerQueryOrder() {
        stubPlan(List.of(request("first"), request("second"), request("third")));
        jobSearchPort.respondWith("first", List.of());
        jobSearchPort.respondWith("second", List.of());
        jobSearchPort.respondWith("third", List.of());
        JobDiscoveryService service = service(execution(3, 5, 5, 30, 50));

        service.runDiscovery();

        assertThat(jobSearchPort.queriesReceived).containsExactly("first", "second", "third");
    }

    @Test
    void run_executesAtMostMaxQueriesPerRun() {
        stubPlan(List.of(request("q1"), request("q2"), request("q3"), request("q4"), request("q5")));
        for (String q : List.of("q1", "q2", "q3", "q4", "q5")) {
            jobSearchPort.respondWith(q, List.of());
        }
        JobDiscoveryService service = service(execution(2, 5, 5, 30, 50));

        JobDiscoveryRunResult result = service.runDiscovery();

        assertThat(result.plannedQueries()).isEqualTo(5);
        assertThat(result.executedQueries()).isEqualTo(2);
        assertThat(jobSearchPort.queriesReceived).containsExactly("q1", "q2");
        assertThat(result.queryLimitReached()).isTrue();
    }

    @Test
    void run_searchFailureIsIsolated_nextQueryStillRuns() {
        stubPlan(List.of(request("bad"), request("good")));
        jobSearchPort.failFor("bad", new JobDiscoveryException("provider failure"));
        jobSearchPort.respondWith("good", List.of());
        JobDiscoveryService service = service(execution(3, 5, 5, 30, 50));

        JobDiscoveryRunResult result = service.runDiscovery();

        assertThat(result.executedQueries()).isEqualTo(2);
        assertThat(result.failedQueries()).isEqualTo(1);
        assertThat(jobSearchPort.queriesReceived).containsExactly("bad", "good");
        assertThat(result.issues()).hasSize(1);
        assertThat(result.issues().get(0).category()).isEqualTo(JobDiscoveryIssueCategory.SEARCH_FAILED);
    }

    @Test
    void run_noSearchRetryOccurs() {
        stubPlan(List.of(request("bad")));
        jobSearchPort.failFor("bad", new JobDiscoveryException("provider failure"));
        JobDiscoveryService service = service(execution(3, 5, 5, 30, 50));

        service.runDiscovery();

        assertThat(jobSearchPort.queriesReceived).containsExactly("bad");
    }

    @Test
    void run_stopsIssuingSearchesOncePaidCapacityExhausted() {
        stubPlan(List.of(request("q1"), request("q2"), request("q3")));
        jobSearchPort.respondWith("q1", List.of(reference("https://example.com/jobs/1")));
        jobSearchPort.respondWith("q2", List.of(reference("https://example.com/jobs/2")));
        jobSearchPort.respondWith("q3", List.of(reference("https://example.com/jobs/3")));
        stubScrapeAndExtractSuccess("https://example.com/jobs/1");
        stubScrapeAndExtractSuccess("https://example.com/jobs/2");
        stubPersistCreated();
        // maxScrapesPerRun=1: after the first candidate's scrape, no further scrape/extraction
        // budget remains, so query 3 must never be issued.
        JobDiscoveryService service = service(execution(3, 1, 1, 30, 50));

        JobDiscoveryRunResult result = service.runDiscovery();

        assertThat(result.executedQueries()).isEqualTo(1);
        assertThat(jobSearchPort.queriesReceived).containsExactly("q1");
        assertThat(result.scrapeAttempts()).isEqualTo(1);
    }

    // --- Canonicalization and deduplication ------------------------------------------------

    @Test
    void run_cosmeticUrlVariantsDeduplicateAcrossSameQuery() {
        stubPlan(List.of(request("q1")));
        jobSearchPort.respondWith("q1", List.of(
                reference("https://example.com/jobs/1?utm_source=x"),
                reference("https://example.com/jobs/1")));
        stubScrapeAndExtractSuccess("https://example.com/jobs/1?utm_source=x");
        stubPersistCreated();
        JobDiscoveryService service = service(execution(3, 5, 5, 30, 50));

        JobDiscoveryRunResult result = service.runDiscovery();

        assertThat(result.uniqueReferencesAccepted()).isEqualTo(1);
        assertThat(result.duplicateReferencesInRun()).isEqualTo(1);
        assertThat(jobPageFetchPort.urlsReceived).hasSize(1);
    }

    @Test
    void run_cosmeticUrlVariantsDeduplicateAcrossDifferentQueries() {
        stubPlan(List.of(request("q1"), request("q2")));
        jobSearchPort.respondWith("q1", List.of(reference("https://example.com/jobs/1")));
        jobSearchPort.respondWith("q2", List.of(reference("https://example.com/jobs/1?utm_campaign=y")));
        stubScrapeAndExtractSuccess("https://example.com/jobs/1");
        stubPersistCreated();
        JobDiscoveryService service = service(execution(3, 5, 5, 30, 50));

        JobDiscoveryRunResult result = service.runDiscovery();

        assertThat(result.uniqueReferencesAccepted()).isEqualTo(1);
        assertThat(result.duplicateReferencesInRun()).isEqualTo(1);
    }

    @Test
    void run_meaningfulQueryParametersRemainDistinct() {
        stubPlan(List.of(request("q1")));
        jobSearchPort.respondWith("q1", List.of(
                reference("https://example.com/jobs?id=1"),
                reference("https://example.com/jobs?id=2")));
        stubScrapeAndExtractSuccess("https://example.com/jobs?id=1");
        stubScrapeAndExtractSuccess("https://example.com/jobs?id=2");
        stubPersistCreated();
        JobDiscoveryService service = service(execution(3, 5, 5, 30, 50));

        JobDiscoveryRunResult result = service.runDiscovery();

        assertThat(result.uniqueReferencesAccepted()).isEqualTo(2);
        assertThat(result.duplicateReferencesInRun()).isZero();
    }

    @Test
    void run_httpAndHttpsRemainDistinct() {
        stubPlan(List.of(request("q1")));
        jobSearchPort.respondWith("q1", List.of(
                reference("http://example.com/jobs/1"),
                reference("https://example.com/jobs/1")));
        stubScrapeAndExtractSuccess("http://example.com/jobs/1");
        stubScrapeAndExtractSuccess("https://example.com/jobs/1");
        stubPersistCreated();
        JobDiscoveryService service = service(execution(3, 5, 5, 30, 50));

        JobDiscoveryRunResult result = service.runDiscovery();

        assertThat(result.uniqueReferencesAccepted()).isEqualTo(2);
    }

    // Note: an "invalid URL" reference cannot be constructed through DiscoveredJobReference's own
    // public API - its compact constructor already calls SourceUrlValidator.requireValid, which
    // enforces the exact same invariants (absolute, http/https, host present, no user-info) that
    // VacancyUrlCanonicalizer.canonicalize independently validates. The INVALID_REFERENCE_URL
    // handling in JobDiscoveryService.processReference is therefore defense-in-depth against a
    // hypothetical future JobSearchPort/DiscoveredJobReference relaxation, not a reachable path
    // for any currently-constructible reference - so it has no dedicated behavioral test here.

    @Test
    void run_uniqueReferenceLimitEnforcedExactly() {
        stubPlan(List.of(request("q1")));
        jobSearchPort.respondWith("q1", List.of(
                reference("https://example.com/jobs/1"),
                reference("https://example.com/jobs/2"),
                reference("https://example.com/jobs/3")));
        stubScrapeAndExtractSuccess("https://example.com/jobs/1");
        stubScrapeAndExtractSuccess("https://example.com/jobs/2");
        stubPersistCreated();
        JobDiscoveryService service = service(execution(3, 5, 5, 2, 50));

        JobDiscoveryRunResult result = service.runDiscovery();

        assertThat(result.uniqueReferencesAccepted()).isEqualTo(2);
        assertThat(result.uniqueReferenceLimitReached()).isTrue();
        assertThat(jobPageFetchPort.urlsReceived).hasSize(2);
    }

    // --- Persisted pre-check -----------------------------------------------------------------

    @Test
    void run_existingCanonicalIdentity_skippedBeforeScrape() {
        stubPlan(List.of(request("q1")));
        jobSearchPort.respondWith("q1", List.of(reference("https://example.com/jobs/1")));
        when(vacancyRepository.existsByCanonicalUrl(any())).thenReturn(true);
        JobDiscoveryService service = service(execution(3, 5, 5, 30, 50));

        JobDiscoveryRunResult result = service.runDiscovery();

        assertThat(result.existingVacanciesSkipped()).isEqualTo(1);
        assertThat(result.scrapeAttempts()).isZero();
        assertThat(result.extractionAttempts()).isZero();
        assertThat(jobPageFetchPort.urlsReceived).isEmpty();
        verify(vacancyExtractionService, never()).extract(any());
    }

    // --- Cost limits -------------------------------------------------------------------------

    @Test
    void run_neverExceedsMaxScrapesPerRun() {
        stubPlan(List.of(request("q1")));
        List<DiscoveredJobReference> refs = List.of(
                reference("https://example.com/jobs/1"), reference("https://example.com/jobs/2"),
                reference("https://example.com/jobs/3"));
        jobSearchPort.respondWith("q1", refs);
        for (DiscoveredJobReference ref : refs) {
            stubScrapeAndExtractSuccess(ref.sourceUrl().toString());
        }
        stubPersistCreated();
        JobDiscoveryService service = service(execution(3, 2, 2, 30, 50));

        JobDiscoveryRunResult result = service.runDiscovery();

        assertThat(result.scrapeAttempts()).isEqualTo(2);
        assertThat(result.scrapeLimitReached()).isTrue();
    }

    @Test
    void run_doesNotScrapeWhenExtractionBudgetAlreadyExhausted() {
        stubPlan(List.of(request("q1")));
        jobSearchPort.respondWith("q1", List.of(
                reference("https://example.com/jobs/1"), reference("https://example.com/jobs/2")));
        stubScrapeAndExtractSuccess("https://example.com/jobs/1");
        stubPersistCreated();
        // maxExtractionsPerRun=1, maxScrapesPerRun=5: after the first candidate uses the one
        // extraction slot, the second candidate must not be scraped even though scrape budget
        // remains.
        JobDiscoveryService service = service(execution(3, 5, 1, 30, 50));

        JobDiscoveryRunResult result = service.runDiscovery();

        assertThat(result.scrapeAttempts()).isEqualTo(1);
        assertThat(result.extractionAttempts()).isEqualTo(1);
        assertThat(result.extractionLimitReached()).isTrue();
        assertThat(jobPageFetchPort.urlsReceived).hasSize(1);
    }

    @Test
    void run_limitsRemainEnforcedWhenPriorScrapesFail() {
        stubPlan(List.of(request("q1")));
        jobSearchPort.respondWith("q1", List.of(
                reference("https://example.com/jobs/1"), reference("https://example.com/jobs/2"),
                reference("https://example.com/jobs/3")));
        jobPageFetchPort.failFor("https://example.com/jobs/1", new JobDiscoveryException("scrape failed"));
        stubScrapeAndExtractSuccess("https://example.com/jobs/2");
        stubPersistCreated();
        JobDiscoveryService service = service(execution(3, 2, 2, 30, 50));

        JobDiscoveryRunResult result = service.runDiscovery();

        // Failed scrapes still consume the scrape attempt budget.
        assertThat(result.scrapeAttempts()).isEqualTo(2);
        assertThat(result.scrapeFailures()).isEqualTo(1);
        assertThat(result.scrapeLimitReached()).isTrue();
    }

    // --- Scrape and extraction ---------------------------------------------------------------

    @Test
    void run_successfulScrapeProducesExactlyOneExtractionRequest() {
        stubPlan(List.of(request("q1")));
        jobSearchPort.respondWith("q1", List.of(reference("https://example.com/jobs/1")));
        stubScrapeAndExtractSuccess("https://example.com/jobs/1");
        stubPersistCreated();
        JobDiscoveryService service = service(execution(3, 5, 5, 30, 50));

        service.runDiscovery();

        verify(vacancyExtractionService, times(1)).extract(any());
    }

    @Test
    void run_scrapeFailureProducesZeroExtractionCalls() {
        stubPlan(List.of(request("q1")));
        jobSearchPort.respondWith("q1", List.of(reference("https://example.com/jobs/1")));
        jobPageFetchPort.failFor("https://example.com/jobs/1", new JobDiscoveryException("scrape failed"));
        JobDiscoveryService service = service(execution(3, 5, 5, 30, 50));

        JobDiscoveryRunResult result = service.runDiscovery();

        assertThat(result.scrapeFailures()).isEqualTo(1);
        assertThat(result.extractionAttempts()).isZero();
        verify(vacancyExtractionService, never()).extract(any());
        assertThat(result.issues().get(0).category()).isEqualTo(JobDiscoveryIssueCategory.SCRAPE_FAILED);
    }

    @Test
    void run_extractionRequestReceivesSourceUrlContentTitleHintAndSnippetHint() {
        stubPlan(List.of(request("q1")));
        DiscoveredJobReference ref = new DiscoveredJobReference(
                URI.create("https://example.com/jobs/1"), "Discovered Title", "Discovered snippet");
        jobSearchPort.respondWith("q1", List.of(ref));
        jobPageFetchPort.respondWith("https://example.com/jobs/1",
                new JobPageContent(URI.create("https://example.com/jobs/1"), "# Markdown body", "Page Title"));
        when(vacancyExtractionService.extract(any())).thenReturn(validExtractedData());
        stubPersistCreated();
        JobDiscoveryService service = service(execution(3, 5, 5, 30, 50));

        service.runDiscovery();

        ArgumentCaptor<VacancyExtractionRequest> captor = ArgumentCaptor.forClass(VacancyExtractionRequest.class);
        verify(vacancyExtractionService).extract(captor.capture());
        VacancyExtractionRequest request = captor.getValue();
        assertThat(request.sourceUrl()).isEqualTo(URI.create("https://example.com/jobs/1"));
        assertThat(request.content()).isEqualTo("# Markdown body");
        assertThat(request.discoveredTitle()).isEqualTo("Discovered Title");
        assertThat(request.discoveredSnippet()).isEqualTo("Discovered snippet");
    }

    @Test
    void run_searchHintsRemainOptional() {
        stubPlan(List.of(request("q1")));
        DiscoveredJobReference ref = new DiscoveredJobReference(URI.create("https://example.com/jobs/1"), "Title Only");
        jobSearchPort.respondWith("q1", List.of(ref));
        jobPageFetchPort.respondWith("https://example.com/jobs/1",
                new JobPageContent(URI.create("https://example.com/jobs/1"), "content"));
        when(vacancyExtractionService.extract(any())).thenReturn(validExtractedData());
        stubPersistCreated();
        JobDiscoveryService service = service(execution(3, 5, 5, 30, 50));

        service.runDiscovery();

        ArgumentCaptor<VacancyExtractionRequest> captor = ArgumentCaptor.forClass(VacancyExtractionRequest.class);
        verify(vacancyExtractionService).extract(captor.capture());
        assertThat(captor.getValue().discoveredSnippet()).isNull();
    }

    @Test
    void run_extractionFailureProducesZeroPersistenceCalls() {
        stubPlan(List.of(request("q1")));
        jobSearchPort.respondWith("q1", List.of(reference("https://example.com/jobs/1")));
        jobPageFetchPort.respondWith("https://example.com/jobs/1",
                new JobPageContent(URI.create("https://example.com/jobs/1"), "content"));
        when(vacancyExtractionService.extract(any())).thenThrow(new VacancyExtractionException("extraction failed"));
        JobDiscoveryService service = service(execution(3, 5, 5, 30, 50));

        JobDiscoveryRunResult result = service.runDiscovery();

        assertThat(result.extractionFailures()).isEqualTo(1);
        assertThat(result.persistenceAttempts()).isZero();
        verify(vacancyIngestionService, never()).persist(any(VacancyCreationCommand.class));
        assertThat(result.issues().get(0).category()).isEqualTo(JobDiscoveryIssueCategory.EXTRACTION_FAILED);
    }

    // --- Persistence ---------------------------------------------------------------------------

    @Test
    void run_mappingToVacancyCreationCommandPreservesAllSupportedFields() {
        stubPlan(List.of(request("q1")));
        jobSearchPort.respondWith("q1", List.of(reference("https://example.com/jobs/1")));
        jobPageFetchPort.respondWith("https://example.com/jobs/1",
                new JobPageContent(URI.create("https://example.com/jobs/1"), "# Full page markdown"));
        ExtractedVacancyData extracted = new ExtractedVacancyData(
                "Senior Java Backend Engineer", "Acme Corp", "Berlin, Germany", RemotePolicy.HYBRID,
                List.of("B2B"), List.of("Java", "Kafka"),
                new BigDecimal("10000"), new BigDecimal("15000"), "PLN", "10000-15000 PLN monthly",
                LocalDate.of(2026, 1, 15));
        when(vacancyExtractionService.extract(any())).thenReturn(extracted);
        stubPersistCreated();
        JobDiscoveryService service = service(execution(3, 5, 5, 30, 50));

        service.runDiscovery();

        ArgumentCaptor<VacancyCreationCommand> captor = ArgumentCaptor.forClass(VacancyCreationCommand.class);
        verify(vacancyIngestionService).persist(captor.capture());
        VacancyCreationCommand command = captor.getValue();
        assertThat(command.companyName()).isEqualTo("Acme Corp");
        assertThat(command.title()).isEqualTo("Senior Java Backend Engineer");
        assertThat(command.description()).isEqualTo("# Full page markdown");
        assertThat(command.url()).isEqualTo("https://example.com/jobs/1");
        assertThat(command.location()).isEqualTo("Berlin, Germany");
        assertThat(command.remoteMode()).isEqualTo(RemotePolicy.HYBRID);
        assertThat(command.salaryMin()).isEqualByComparingTo("10000");
        assertThat(command.salaryMax()).isEqualByComparingTo("15000");
        assertThat(command.currency()).isEqualTo("PLN");
        assertThat(command.salaryText()).isEqualTo("10000-15000 PLN monthly");
        assertThat(command.postedAt()).isEqualTo(LocalDate.of(2026, 1, 15));
    }

    @Test
    void run_sourceIsProviderNeutral_neverFirecrawl() {
        stubPlan(List.of(request("q1")));
        jobSearchPort.respondWith("q1", List.of(reference("https://example.com/jobs/1")));
        stubScrapeAndExtractSuccess("https://example.com/jobs/1");
        stubPersistCreated();
        JobDiscoveryService service = service(execution(3, 5, 5, 30, 50));

        service.runDiscovery();

        ArgumentCaptor<VacancyCreationCommand> captor = ArgumentCaptor.forClass(VacancyCreationCommand.class);
        verify(vacancyIngestionService).persist(captor.capture());
        assertThat(captor.getValue().source()).isEqualToIgnoringCase("web_discovery");
        assertThat(captor.getValue().source()).doesNotContainIgnoringCase("firecrawl");
    }

    @Test
    void run_originalSourceUrlIsPreserved() {
        stubPlan(List.of(request("q1")));
        String rawUrl = "https://example.com/jobs/1?utm_source=x&id=42";
        jobSearchPort.respondWith("q1", List.of(reference(rawUrl)));
        stubScrapeAndExtractSuccess(rawUrl);
        stubPersistCreated();
        JobDiscoveryService service = service(execution(3, 5, 5, 30, 50));

        service.runDiscovery();

        ArgumentCaptor<VacancyCreationCommand> captor = ArgumentCaptor.forClass(VacancyCreationCommand.class);
        verify(vacancyIngestionService).persist(captor.capture());
        assertThat(captor.getValue().url()).isEqualTo(rawUrl);
    }

    @Test
    void run_unknownRemotePolicy_isNotDefaultedToRemote() {
        stubPlan(List.of(request("q1")));
        jobSearchPort.respondWith("q1", List.of(reference("https://example.com/jobs/1")));
        jobPageFetchPort.respondWith("https://example.com/jobs/1",
                new JobPageContent(URI.create("https://example.com/jobs/1"), "content"));
        ExtractedVacancyData extracted = new ExtractedVacancyData(
                "Title", "Company", null, RemotePolicy.UNSPECIFIED, List.of(), List.of(),
                null, null, null, null, null);
        when(vacancyExtractionService.extract(any())).thenReturn(extracted);
        stubPersistCreated();
        JobDiscoveryService service = service(execution(3, 5, 5, 30, 50));

        service.runDiscovery();

        ArgumentCaptor<VacancyCreationCommand> captor = ArgumentCaptor.forClass(VacancyCreationCommand.class);
        verify(vacancyIngestionService).persist(captor.capture());
        assertThat(captor.getValue().remoteMode()).isEqualTo(RemotePolicy.UNSPECIFIED);
    }

    @Test
    void run_missingSalaryLocationAndDate_remainNull() {
        stubPlan(List.of(request("q1")));
        jobSearchPort.respondWith("q1", List.of(reference("https://example.com/jobs/1")));
        jobPageFetchPort.respondWith("https://example.com/jobs/1",
                new JobPageContent(URI.create("https://example.com/jobs/1"), "content"));
        ExtractedVacancyData extracted = new ExtractedVacancyData(
                "Title", "Company", null, RemotePolicy.UNSPECIFIED, List.of(), List.of(),
                null, null, null, null, null);
        when(vacancyExtractionService.extract(any())).thenReturn(extracted);
        stubPersistCreated();
        JobDiscoveryService service = service(execution(3, 5, 5, 30, 50));

        service.runDiscovery();

        ArgumentCaptor<VacancyCreationCommand> captor = ArgumentCaptor.forClass(VacancyCreationCommand.class);
        verify(vacancyIngestionService).persist(captor.capture());
        VacancyCreationCommand command = captor.getValue();
        assertThat(command.location()).isNull();
        assertThat(command.salaryMin()).isNull();
        assertThat(command.salaryMax()).isNull();
        assertThat(command.currency()).isNull();
        assertThat(command.postedAt()).isNull();
    }

    @Test
    void run_created_addsExactlyOneVacancyUuid() {
        stubPlan(List.of(request("q1")));
        jobSearchPort.respondWith("q1", List.of(reference("https://example.com/jobs/1")));
        stubScrapeAndExtractSuccess("https://example.com/jobs/1");
        UUID vacancyId = UUID.randomUUID();
        when(vacancyIngestionService.persist(any(VacancyCreationCommand.class)))
                .thenReturn(new VacancyCreationResult(Vacancy.builder().id(vacancyId).build(), true));
        JobDiscoveryService service = service(execution(3, 5, 5, 30, 50));

        JobDiscoveryRunResult result = service.runDiscovery();

        assertThat(result.createdVacancies()).isEqualTo(1);
        assertThat(result.createdVacancyIds()).containsExactly(vacancyId);
    }

    @Test
    void run_alreadyExistsAfterRace_isNotTreatedAsFailure() {
        stubPlan(List.of(request("q1")));
        jobSearchPort.respondWith("q1", List.of(reference("https://example.com/jobs/1")));
        stubScrapeAndExtractSuccess("https://example.com/jobs/1");
        when(vacancyIngestionService.persist(any(VacancyCreationCommand.class)))
                .thenReturn(new VacancyCreationResult(Vacancy.builder().id(UUID.randomUUID()).build(), false));
        JobDiscoveryService service = service(execution(3, 5, 5, 30, 50));

        JobDiscoveryRunResult result = service.runDiscovery();

        assertThat(result.alreadyExistingAfterRace()).isEqualTo(1);
        assertThat(result.persistenceFailures()).isZero();
        assertThat(result.createdVacancyIds()).isEmpty();
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void run_persistenceFailure_isIsolated() {
        stubPlan(List.of(request("q1")));
        jobSearchPort.respondWith("q1", List.of(
                reference("https://example.com/jobs/1"), reference("https://example.com/jobs/2")));
        stubScrapeAndExtractSuccess("https://example.com/jobs/1");
        stubScrapeAndExtractSuccess("https://example.com/jobs/2");
        UUID secondVacancyId = UUID.randomUUID();
        when(vacancyIngestionService.persist(any(VacancyCreationCommand.class)))
                .thenThrow(new RuntimeException("db failure"))
                .thenReturn(new VacancyCreationResult(Vacancy.builder().id(secondVacancyId).build(), true));
        JobDiscoveryService service = service(execution(3, 5, 5, 30, 50));

        JobDiscoveryRunResult result = service.runDiscovery();

        assertThat(result.persistenceFailures()).isEqualTo(1);
        assertThat(result.createdVacancies()).isEqualTo(1);
        assertThat(result.createdVacancyIds()).containsExactly(secondVacancyId);
    }

    // --- Run result ------------------------------------------------------------------------

    @Test
    void run_resultCollectionsAreImmutable() {
        stubPlan(List.of(request("q1")));
        jobSearchPort.respondWith("q1", List.of());
        JobDiscoveryService service = service(execution(3, 5, 5, 30, 50));

        JobDiscoveryRunResult result = service.runDiscovery();

        assertThatThrownBy(() -> result.createdVacancyIds().add(UUID.randomUUID()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.issues().add(null)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void run_issueDetailsRespectMaxReportedIssuesAndOmittedCountIsAccurate() {
        stubPlan(List.of(request("q1")));
        jobSearchPort.respondWith("q1", List.of(
                reference("https://example.com/jobs/1"), reference("https://example.com/jobs/2"),
                reference("https://example.com/jobs/3")));
        jobPageFetchPort.failFor("https://example.com/jobs/1", new JobDiscoveryException("failed"));
        jobPageFetchPort.failFor("https://example.com/jobs/2", new JobDiscoveryException("failed"));
        jobPageFetchPort.failFor("https://example.com/jobs/3", new JobDiscoveryException("failed"));
        JobDiscoveryService service = service(execution(3, 5, 5, 30, 1));

        JobDiscoveryRunResult result = service.runDiscovery();

        assertThat(result.issues()).hasSize(1);
        assertThat(result.omittedIssueCount()).isEqualTo(2);
    }

    @Test
    void run_normalSkipOutcomesDoNotCreateIssues() {
        stubPlan(List.of(request("q1")));
        jobSearchPort.respondWith("q1", List.of(
                reference("https://example.com/jobs/1"), reference("https://example.com/jobs/1")));
        when(vacancyRepository.existsByCanonicalUrl(any())).thenReturn(true);
        JobDiscoveryService service = service(execution(3, 5, 5, 30, 50));

        JobDiscoveryRunResult result = service.runDiscovery();

        assertThat(result.duplicateReferencesInRun()).isEqualTo(1);
        assertThat(result.existingVacanciesSkipped()).isEqualTo(1);
        assertThat(result.issues()).isEmpty();
    }

    // --- Unrecoverable orchestration failure ------------------------------------------------

    @Test
    void run_candidateProfileLoadFailure_failsWholeRun() {
        when(candidateProfileProvider.getProfile()).thenThrow(new RuntimeException("profile unavailable"));
        JobDiscoveryService service = service(execution(3, 5, 5, 30, 50));

        assertThatThrownBy(service::runDiscovery).isInstanceOf(JobDiscoveryOrchestrationException.class);
        verify(queryPlanner, never()).plan(any());
    }

    // --- Transaction safety --------------------------------------------------------------------

    @Test
    void jobDiscoveryService_hasNoTransactionalAnnotation() throws NoSuchMethodException {
        assertThat(JobDiscoveryService.class.isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(JobDiscoveryService.class.getMethod("runDiscovery").isAnnotationPresent(Transactional.class)).isFalse();
    }

    @Test
    void jobDiscoveryServiceClass_isNotAbstractAndHasNoInterfaceCounterpart() {
        // Convention check: a single-implementation orchestration service, no port introduced.
        assertThat(Modifier.isAbstract(JobDiscoveryService.class.getModifiers())).isFalse();
        assertThat(JobDiscoveryService.class.getInterfaces()).isEmpty();
    }

    @Test
    void persistence_delegatesToVacancyIngestionServiceOwnTransactionBoundary() {
        stubPlan(List.of(request("q1")));
        jobSearchPort.respondWith("q1", List.of(reference("https://example.com/jobs/1")));
        stubScrapeAndExtractSuccess("https://example.com/jobs/1");
        stubPersistCreated();
        JobDiscoveryService service = service(execution(3, 5, 5, 30, 50));

        service.runDiscovery();

        // JobDiscoveryService never opens a transaction of its own - the only DB write happens
        // through VacancyIngestionService.persist(VacancyCreationCommand), which owns its own
        // short REQUIRES_NEW transaction and canonical-conflict retry (see that class's own tests).
        verify(vacancyIngestionService, times(1)).persist(any(VacancyCreationCommand.class));
    }

    // --- Budget gate -------------------------------------------------------------------------

    @Test
    void run_callsBudgetPortExactlyOnceBeforeFirstSearch() {
        stubPlan(List.of(request("q1")));
        jobSearchPort.respondWith("q1", List.of());
        JobDiscoveryService service = service(execution(3, 5, 5, 30, 50));

        service.runDiscovery();

        verify(budgetPort, times(1)).assessBudget(any());
    }

    @Test
    void run_budgetPortReceivesExactBoundedQueryList() {
        stubPlan(List.of(request("q1"), request("q2"), request("q3"), request("q4")));
        for (String q : List.of("q1", "q2", "q3")) {
            jobSearchPort.respondWith(q, List.of());
        }
        JobDiscoveryService service = service(execution(3, 5, 5, 30, 50));

        service.runDiscovery();

        ArgumentCaptor<JobDiscoveryBudgetRequest> captor = ArgumentCaptor.forClass(JobDiscoveryBudgetRequest.class);
        verify(budgetPort).assessBudget(captor.capture());
        assertThat(captor.getValue().searchRequests()).extracting(JobSearchRequest::query)
                .containsExactly("q1", "q2", "q3");
        assertThat(captor.getValue().maxScrapesPerRun()).isEqualTo(5);
    }

    @Test
    void run_allowedDecision_executesExistingFlow() {
        stubPlan(List.of(request("q1")));
        jobSearchPort.respondWith("q1", List.of(reference("https://example.com/jobs/1")));
        stubScrapeAndExtractSuccess("https://example.com/jobs/1");
        stubPersistCreated();
        JobDiscoveryService service = service(execution(3, 5, 5, 30, 50));

        JobDiscoveryRunResult result = service.runDiscovery();

        assertThat(result.createdVacancies()).isEqualTo(1);
        assertThat(result.budgetDecision().status()).isEqualTo(JobDiscoveryBudgetStatus.ALLOWED);
    }

    @Test
    void run_deniedPerRunLimit_executesZeroProcessing() {
        stubPlan(List.of(request("q1")));
        when(budgetPort.assessBudget(any())).thenReturn(deniedDecision(JobDiscoveryBudgetStatus.DENIED_PER_RUN_LIMIT));
        JobDiscoveryService service = service(execution(3, 5, 5, 30, 50));

        JobDiscoveryRunResult result = service.runDiscovery();

        assertZeroProcessing(result, JobDiscoveryBudgetStatus.DENIED_PER_RUN_LIMIT);
        assertThat(jobSearchPort.queriesReceived).isEmpty();
    }

    @Test
    void run_deniedMonthlyLimit_executesZeroProcessing() {
        stubPlan(List.of(request("q1")));
        when(budgetPort.assessBudget(any())).thenReturn(deniedDecision(JobDiscoveryBudgetStatus.DENIED_MONTHLY_LIMIT));
        JobDiscoveryService service = service(execution(3, 5, 5, 30, 50));

        JobDiscoveryRunResult result = service.runDiscovery();

        assertZeroProcessing(result, JobDiscoveryBudgetStatus.DENIED_MONTHLY_LIMIT);
        assertThat(jobSearchPort.queriesReceived).isEmpty();
    }

    @Test
    void run_deniedReserveLimit_executesZeroProcessing() {
        stubPlan(List.of(request("q1")));
        when(budgetPort.assessBudget(any())).thenReturn(deniedDecision(JobDiscoveryBudgetStatus.DENIED_RESERVE_LIMIT));
        JobDiscoveryService service = service(execution(3, 5, 5, 30, 50));

        JobDiscoveryRunResult result = service.runDiscovery();

        assertZeroProcessing(result, JobDiscoveryBudgetStatus.DENIED_RESERVE_LIMIT);
        assertThat(jobSearchPort.queriesReceived).isEmpty();
    }

    @Test
    void run_unavailableBudget_executesZeroProcessing() {
        stubPlan(List.of(request("q1")));
        JobDiscoveryBudgetDecision unavailable = JobDiscoveryBudgetDecision.unavailable(
                6, 5, 800, 200, 15, "HTTP_500");
        when(budgetPort.assessBudget(any())).thenReturn(unavailable);
        JobDiscoveryService service = service(execution(3, 5, 5, 30, 50));

        JobDiscoveryRunResult result = service.runDiscovery();

        assertZeroProcessing(result, JobDiscoveryBudgetStatus.UNAVAILABLE);
        assertThat(jobSearchPort.queriesReceived).isEmpty();
    }

    @Test
    void run_deniedBudget_performsZeroRepositoryPreChecksAndZeroPersistence() {
        stubPlan(List.of(request("q1")));
        when(budgetPort.assessBudget(any())).thenReturn(deniedDecision(JobDiscoveryBudgetStatus.DENIED_PER_RUN_LIMIT));
        JobDiscoveryService service = service(execution(3, 5, 5, 30, 50));

        service.runDiscovery();

        verify(vacancyRepository, never()).existsByCanonicalUrl(any());
        verify(vacancyIngestionService, never()).persist(any(VacancyCreationCommand.class));
    }

    @Test
    void run_budgetDenial_isNotCountedAsSearchFailure() {
        stubPlan(List.of(request("q1")));
        when(budgetPort.assessBudget(any())).thenReturn(deniedDecision(JobDiscoveryBudgetStatus.DENIED_PER_RUN_LIMIT));
        JobDiscoveryService service = service(execution(3, 5, 5, 30, 50));

        JobDiscoveryRunResult result = service.runDiscovery();

        assertThat(result.failedQueries()).isZero();
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void run_emptyPlannedQueries_skipsBudgetEndpoint_statusNotRequired() {
        stubPlan(List.of());
        JobDiscoveryService service = service(execution(3, 5, 5, 30, 50));

        JobDiscoveryRunResult result = service.runDiscovery();

        verify(budgetPort, never()).assessBudget(any());
        assertThat(result.budgetDecision().status()).isEqualTo(JobDiscoveryBudgetStatus.NOT_REQUIRED);
        assertThat(result.plannedQueries()).isZero();
        assertThat(result.executedQueries()).isZero();
    }

    @Test
    void run_budgetDecisionIsEmbeddedImmutablyInResult() {
        stubPlan(List.of(request("q1")));
        jobSearchPort.respondWith("q1", List.of());
        JobDiscoveryService service = service(execution(3, 5, 5, 30, 50));

        JobDiscoveryRunResult result = service.runDiscovery();

        assertThat(result.budgetDecision()).isNotNull();
        assertThat(result.budgetDecision().status()).isEqualTo(JobDiscoveryBudgetStatus.ALLOWED);
    }

    private JobDiscoveryBudgetDecision deniedDecision(JobDiscoveryBudgetStatus status) {
        return new JobDiscoveryBudgetDecision(status, false,
                20, 5, 25, 800, 200, 15,
                1000L, 1000L, 0L, Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-31T23:59:59Z"), null);
    }

    private void assertZeroProcessing(JobDiscoveryRunResult result, JobDiscoveryBudgetStatus expectedStatus) {
        assertThat(result.budgetDecision().status()).isEqualTo(expectedStatus);
        assertThat(result.executedQueries()).isZero();
        assertThat(result.scrapeAttempts()).isZero();
        assertThat(result.extractionAttempts()).isZero();
        assertThat(result.persistenceAttempts()).isZero();
        assertThat(result.createdVacancyIds()).isEmpty();
        assertThat(result.issues()).isEmpty();
    }

    // --- Fixtures and fakes ------------------------------------------------------------------

    private JobDiscoveryService service(JobDiscoveryProperties.Execution execution) {
        JobDiscoveryProperties properties = new JobDiscoveryProperties(true, execution, budget(), disabledScheduler());
        return new JobDiscoveryService(candidateProfileProvider, queryPlanner, jobSearchPort, jobPageFetchPort,
                vacancyExtractionService, vacancyIngestionService, vacancyRepository, budgetPort, properties, CLOCK);
    }

    private JobDiscoveryProperties.Scheduler disabledScheduler() {
        return new JobDiscoveryProperties.Scheduler(false, "0 0 8 * * *", "Europe/Warsaw",
                Duration.ofHours(2), Duration.ofMinutes(1));
    }

    private JobDiscoveryProperties.Execution execution(int maxQueries, int maxScrapes, int maxExtractions,
                                                         int maxUniqueReferences, int maxReportedIssues) {
        return new JobDiscoveryProperties.Execution(maxQueries, maxScrapes, maxExtractions, maxUniqueReferences, maxReportedIssues);
    }

    private JobDiscoveryProperties.Budget budget() {
        return new JobDiscoveryProperties.Budget(800, 200, 15);
    }

    private JobDiscoveryBudgetDecision allowedDecision() {
        return new JobDiscoveryBudgetDecision(JobDiscoveryBudgetStatus.ALLOWED, true,
                6, 5, 11, 800, 200, 15,
                1000L, 1000L, 0L, Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-31T23:59:59Z"), null);
    }

    private void stubPlan(List<JobSearchRequest> requests) {
        when(queryPlanner.plan(profile)).thenReturn(requests);
    }

    private void stubScrapeAndExtractSuccess(String url) {
        jobPageFetchPort.respondWith(url, new JobPageContent(URI.create(url), "content"));
        when(vacancyExtractionService.extract(any())).thenReturn(validExtractedData());
    }

    private void stubPersistCreated() {
        when(vacancyIngestionService.persist(any(VacancyCreationCommand.class)))
                .thenAnswer(inv -> new VacancyCreationResult(Vacancy.builder().id(UUID.randomUUID()).build(), true));
    }

    private JobSearchRequest request(String query) {
        return new JobSearchRequest(query, 10);
    }

    private DiscoveredJobReference reference(String url) {
        return new DiscoveredJobReference(URI.create(url), "Job title", "Job snippet");
    }

    private ExtractedVacancyData validExtractedData() {
        return new ExtractedVacancyData(
                "Backend Engineer", "Acme Corp", "Remote", RemotePolicy.REMOTE, List.of(), List.of(),
                null, null, null, null, null);
    }

    private CandidateProfile validProfile() {
        return new CandidateProfile(
                "Backend Engineer", "Senior", List.of(), List.of("English"), 6,
                new CandidatePreferences(null, "Remote", null, List.of(), false, List.of(), null, null, null, null));
    }

    private static class FakeJobSearchPort implements JobSearchPort {

        private final Map<String, List<DiscoveredJobReference>> responses = new LinkedHashMap<>();
        private final Map<String, RuntimeException> failures = new HashMap<>();
        private final List<String> queriesReceived = new ArrayList<>();

        void respondWith(String query, List<DiscoveredJobReference> references) {
            responses.put(query, references);
        }

        void failFor(String query, RuntimeException exception) {
            failures.put(query, exception);
        }

        @Override
        public List<DiscoveredJobReference> search(JobSearchRequest request) {
            queriesReceived.add(request.query());
            if (failures.containsKey(request.query())) {
                throw failures.get(request.query());
            }
            return responses.getOrDefault(request.query(), List.of());
        }
    }

    private static class FakeJobPageFetchPort implements JobPageFetchPort {

        private final Map<String, JobPageContent> responses = new HashMap<>();
        private final Map<String, RuntimeException> failures = new HashMap<>();
        private final List<URI> urlsReceived = new ArrayList<>();

        void respondWith(String url, JobPageContent content) {
            responses.put(url, content);
        }

        void failFor(String url, RuntimeException exception) {
            failures.put(url, exception);
        }

        @Override
        public JobPageContent fetch(URI sourceUrl) {
            urlsReceived.add(sourceUrl);
            String key = sourceUrl.toString();
            if (failures.containsKey(key)) {
                throw failures.get(key);
            }
            JobPageContent content = responses.get(key);
            if (content == null) {
                throw new JobDiscoveryException("no fixture configured for " + key);
            }
            return content;
        }
    }
}
