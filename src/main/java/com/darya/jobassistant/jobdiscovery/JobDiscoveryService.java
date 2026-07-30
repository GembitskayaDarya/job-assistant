package com.darya.jobassistant.jobdiscovery;

import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.CandidateProfileProvider;
import com.darya.jobassistant.integrations.jobsearch.DiscoveredJobReference;
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
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import com.darya.jobassistant.vacancies.service.VacancyIngestionService;
import com.darya.jobassistant.vacancies.url.CanonicalVacancyUrl;
import com.darya.jobassistant.vacancies.url.InvalidVacancyUrlException;
import com.darya.jobassistant.vacancies.url.VacancyUrlCanonicalizer;
import com.darya.jobassistant.vacancyextraction.VacancyExtractionService;
import com.darya.jobassistant.vacancyextraction.model.ExtractedVacancyData;
import com.darya.jobassistant.vacancyextraction.model.VacancyExtractionRequest;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Orchestrates one bounded, provider-neutral job-discovery run:
 * <pre>
 * candidate profile -&gt; plan queries -&gt; search (sequential, bounded, early-stopping)
 * -&gt; canonicalize + dedup references -&gt; skip already-persisted identities
 * -&gt; scrape -&gt; extract -&gt; persist each candidate in its own short transaction
 * </pre>
 *
 * <p>Knows nothing about Firecrawl, Spring AI, WebClient, Telegram, or any provider API key -
 * only the provider-neutral {@link JobSearchPort}/{@link JobPageFetchPort}/{@link
 * VacancyExtractionService} boundaries and the existing, already-transactional {@link
 * VacancyIngestionService}. Deliberately not {@code @Transactional} and opens no transaction of
 * its own: search, canonicalization, the persisted-identity pre-check, Scrape, and AI Extraction
 * all run outside any write transaction; only {@link VacancyIngestionService#persistDiscovered(VacancyCreationCommand)}
 * opens a transaction, one short {@code REQUIRES_NEW} transaction per candidate (Vacancy and its
 * durable {@code VacancyRecommendationTask} committed atomically together - see that method),
 * entirely inside that call.
 *
 * <p>Conditional on {@code job-discovery.enabled=true}, matching this project's established
 * activation convention ({@code JobMonitoringService}/{@code telegram.enabled}, {@code
 * FirecrawlJobSearchAdapter}/{@code firecrawl.enabled}): a required collaborator with no bean
 * present when the flag is off would otherwise fail to wire. If discovery is enabled but no real
 * {@link JobSearchPort}/{@link JobPageFetchPort}/{@link JobDiscoveryBudgetPort} bean exists (e.g.
 * {@code firecrawl.enabled=false}), Spring fails application startup with its own clear "no
 * qualifying bean" message - deliberately not swallowed or special-cased here.
 *
 * <p>Description policy: {@link ExtractedVacancyData} has no description field (see its javadoc) -
 * this service reuses the fetched {@link JobPageContent#content()} verbatim as {@link
 * VacancyCreationCommand#description()}, the discovery-path equivalent of guided manual import
 * reusing the user's raw pasted text. No second description-normalization algorithm is introduced
 * here.
 *
 * <p>Vacancy source/provenance: Firecrawl is a technical retrieval provider, not the vacancy's
 * business source, so {@code "firecrawl"} is never persisted as {@link
 * VacancyCreationCommand#source()}. {@link #SOURCE} is the provider-neutral value instead, styled
 * consistently with the other freeform, lowercase {@code Vacancy.source} values already in use
 * ({@code "remoteok"}, {@code "manual_telegram"} - see {@code VacancyIngestionService}/{@code
 * VacancyImportReviewService}); {@code source} has no enum type or database CHECK constraint
 * anywhere in this project, so no migration is needed to introduce this value.
 *
 * <p>Budget gate: before any Search, this service plans queries once, bounds them to {@code
 * maxQueriesPerRun}, and - only when that bounded list is non-empty - calls {@link
 * JobDiscoveryBudgetPort#assessBudget} exactly once with that exact list. A non-{@code ALLOWED}
 * decision (including {@code NOT_REQUIRED} for an empty bounded list) short-circuits the run with
 * zero Search/Scrape/Extraction/persistence calls; see {@link JobDiscoveryBudgetStatus}. {@link
 * JobDiscoveryBudgetPort} is a required collaborator, matching {@link JobSearchPort}/{@link
 * JobPageFetchPort}: if discovery is enabled without a real budget adapter bean (today, only when
 * {@code firecrawl.enabled=false}), Spring fails application startup rather than this service
 * silently skipping the budget gate.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "job-discovery", name = "enabled", havingValue = "true")
public class JobDiscoveryService {

    /** Provider-neutral vacancy source for every candidate persisted by this service. */
    static final String SOURCE = "web_discovery";

    private final CandidateProfileProvider candidateProfileProvider;
    private final JobSearchQueryPlanner queryPlanner;
    private final JobSearchPort jobSearchPort;
    private final JobPageFetchPort jobPageFetchPort;
    private final VacancyExtractionService vacancyExtractionService;
    private final VacancyIngestionService vacancyIngestionService;
    private final VacancyRepository vacancyRepository;
    private final JobDiscoveryBudgetPort budgetPort;
    private final JobDiscoveryProperties properties;
    private final Clock clock;

    public JobDiscoveryService(
            CandidateProfileProvider candidateProfileProvider,
            JobSearchQueryPlanner queryPlanner,
            JobSearchPort jobSearchPort,
            JobPageFetchPort jobPageFetchPort,
            VacancyExtractionService vacancyExtractionService,
            VacancyIngestionService vacancyIngestionService,
            VacancyRepository vacancyRepository,
            JobDiscoveryBudgetPort budgetPort,
            JobDiscoveryProperties properties,
            Clock clock) {
        this.candidateProfileProvider = candidateProfileProvider;
        this.queryPlanner = queryPlanner;
        this.jobSearchPort = jobSearchPort;
        this.jobPageFetchPort = jobPageFetchPort;
        this.vacancyExtractionService = vacancyExtractionService;
        this.vacancyIngestionService = vacancyIngestionService;
        this.vacancyRepository = vacancyRepository;
        this.budgetPort = budgetPort;
        this.properties = properties;
        this.clock = clock;
    }

    public JobDiscoveryRunResult runDiscovery() {
        Instant startedAt = clock.instant();
        JobDiscoveryProperties.Execution limits = properties.execution();
        JobDiscoveryProperties.Budget budget = properties.budget();
        RunAccumulator acc = new RunAccumulator(limits.maxReportedIssues());

        CandidateProfile profile = loadProfile();
        List<JobSearchRequest> plannedQueries = queryPlanner.plan(profile);
        acc.plannedQueries = plannedQueries.size();

        int queriesToAttempt = Math.min(plannedQueries.size(), limits.maxQueriesPerRun());
        List<JobSearchRequest> boundedQueries = plannedQueries.subList(0, queriesToAttempt);

        JobDiscoveryBudgetDecision budgetDecision = assessBudget(boundedQueries, limits, budget);
        logBudgetDecision(budgetDecision);

        if (budgetDecision.status() != JobDiscoveryBudgetStatus.ALLOWED) {
            Instant completedAt = clock.instant();
            JobDiscoveryRunResult result =
                    acc.toResult(startedAt, completedAt, Duration.between(startedAt, completedAt), budgetDecision);
            logSummary(result);
            return result;
        }

        for (int queryIndex = 0; queryIndex < boundedQueries.size(); queryIndex++) {
            if (acc.uniqueReferencesAccepted >= limits.maxUniqueReferencesPerRun()) {
                acc.uniqueReferenceLimitReached = true;
                break;
            }
            if (noRemainingPaidCapacity(acc, limits)) {
                break;
            }
            executeQuery(boundedQueries.get(queryIndex), queryIndex, acc, limits);
        }
        acc.queryLimitReached = acc.executedQueries == limits.maxQueriesPerRun()
                && plannedQueries.size() > limits.maxQueriesPerRun();

        Instant completedAt = clock.instant();
        JobDiscoveryRunResult result =
                acc.toResult(startedAt, completedAt, Duration.between(startedAt, completedAt), budgetDecision);
        logSummary(result);
        return result;
    }

    private JobDiscoveryBudgetDecision assessBudget(List<JobSearchRequest> boundedQueries,
                                                      JobDiscoveryProperties.Execution limits,
                                                      JobDiscoveryProperties.Budget budget) {
        if (boundedQueries.isEmpty()) {
            return JobDiscoveryBudgetDecision.notRequired(
                    budget.monthlyCreditLimit(), budget.reserveCredits(), budget.maxEstimatedCreditsPerRun());
        }
        return budgetPort.assessBudget(new JobDiscoveryBudgetRequest(boundedQueries, limits.maxScrapesPerRun()));
    }

    private CandidateProfile loadProfile() {
        try {
            return candidateProfileProvider.getProfile();
        } catch (RuntimeException e) {
            throw new JobDiscoveryOrchestrationException("Unable to load candidate profile for job discovery", e);
        }
    }

    private boolean noRemainingPaidCapacity(RunAccumulator acc, JobDiscoveryProperties.Execution limits) {
        return acc.scrapeAttempts >= limits.maxScrapesPerRun() || acc.extractionAttempts >= limits.maxExtractionsPerRun();
    }

    private void executeQuery(JobSearchRequest request, int queryIndex, RunAccumulator acc, JobDiscoveryProperties.Execution limits) {
        acc.executedQueries++;
        List<DiscoveredJobReference> references;
        try {
            references = jobSearchPort.search(request);
        } catch (RuntimeException e) {
            acc.failedQueries++;
            acc.addIssue(JobDiscoveryIssueCategory.SEARCH_FAILED, queryIndex, null, e.getClass().getSimpleName(), null);
            return;
        }
        acc.discoveredReferences += references.size();

        for (DiscoveredJobReference reference : references) {
            processReference(reference, queryIndex, acc, limits);
        }
    }

    private void processReference(DiscoveredJobReference reference, int queryIndex, RunAccumulator acc,
                                   JobDiscoveryProperties.Execution limits) {
        int referenceOrdinal = acc.nextReferenceOrdinal++;
        URI sourceUrl = reference.sourceUrl();

        CanonicalVacancyUrl canonical;
        try {
            canonical = VacancyUrlCanonicalizer.canonicalize(sourceUrl);
        } catch (InvalidVacancyUrlException e) {
            acc.invalidReferences++;
            acc.addIssue(JobDiscoveryIssueCategory.INVALID_REFERENCE_URL, queryIndex, safeHost(sourceUrl),
                    e.getClass().getSimpleName(), referenceOrdinal);
            return;
        }

        if (!acc.seenCanonicalUrls.add(canonical)) {
            acc.duplicateReferencesInRun++;
            return;
        }

        if (acc.uniqueReferencesAccepted >= limits.maxUniqueReferencesPerRun()) {
            acc.uniqueReferenceLimitReached = true;
            return;
        }
        acc.uniqueReferencesAccepted++;

        if (vacancyRepository.existsByCanonicalUrl(canonical)) {
            acc.existingVacanciesSkipped++;
            return;
        }

        if (acc.scrapeAttempts >= limits.maxScrapesPerRun()) {
            acc.scrapeLimitReached = true;
            return;
        }
        if (acc.extractionAttempts >= limits.maxExtractionsPerRun()) {
            acc.extractionLimitReached = true;
            return;
        }

        JobPageContent content = scrape(reference, queryIndex, referenceOrdinal, acc);
        if (content == null) {
            return;
        }

        ExtractedVacancyData extracted = extract(reference, content, queryIndex, referenceOrdinal, acc);
        if (extracted == null) {
            return;
        }

        persist(sourceUrl, content, extracted, queryIndex, referenceOrdinal, acc);
    }

    private JobPageContent scrape(DiscoveredJobReference reference, int queryIndex, int referenceOrdinal, RunAccumulator acc) {
        acc.scrapeAttempts++;
        try {
            JobPageContent content = jobPageFetchPort.fetch(reference.sourceUrl());
            acc.scrapeSuccesses++;
            return content;
        } catch (RuntimeException e) {
            acc.scrapeFailures++;
            acc.addIssue(JobDiscoveryIssueCategory.SCRAPE_FAILED, queryIndex, safeHost(reference.sourceUrl()),
                    e.getClass().getSimpleName(), referenceOrdinal);
            return null;
        }
    }

    private ExtractedVacancyData extract(DiscoveredJobReference reference, JobPageContent content, int queryIndex,
                                          int referenceOrdinal, RunAccumulator acc) {
        acc.extractionAttempts++;
        try {
            VacancyExtractionRequest request = new VacancyExtractionRequest(
                    reference.sourceUrl(), content.content(), reference.title(), reference.snippet());
            ExtractedVacancyData extracted = vacancyExtractionService.extract(request);
            acc.extractionSuccesses++;
            return extracted;
        } catch (RuntimeException e) {
            acc.extractionFailures++;
            acc.addIssue(JobDiscoveryIssueCategory.EXTRACTION_FAILED, queryIndex, safeHost(reference.sourceUrl()),
                    e.getClass().getSimpleName(), referenceOrdinal);
            return null;
        }
    }

    private void persist(URI sourceUrl, JobPageContent content, ExtractedVacancyData extracted, int queryIndex,
                          int referenceOrdinal, RunAccumulator acc) {
        acc.persistenceAttempts++;
        VacancyCreationCommand command = buildCreationCommand(sourceUrl, content, extracted);
        try {
            VacancyCreationResult result = vacancyIngestionService.persistDiscovered(command);
            if (result.newlyCreated()) {
                acc.createdVacancies++;
                acc.createdVacancyIds.add(result.vacancy().getId());
            } else {
                acc.alreadyExistingAfterRace++;
            }
        } catch (RuntimeException e) {
            acc.persistenceFailures++;
            acc.addIssue(JobDiscoveryIssueCategory.PERSISTENCE_FAILED, queryIndex, safeHost(sourceUrl),
                    e.getClass().getSimpleName(), referenceOrdinal);
        }
    }

    private VacancyCreationCommand buildCreationCommand(URI sourceUrl, JobPageContent content, ExtractedVacancyData extracted) {
        return new VacancyCreationCommand(
                extracted.company(),
                extracted.title(),
                content.content(),
                sourceUrl.toString(),
                extracted.location(),
                extracted.remotePolicy(),
                extracted.salaryMin(),
                extracted.salaryMax(),
                extracted.currency(),
                extracted.salaryText(),
                SOURCE,
                extracted.postedDate());
    }

    private String safeHost(URI uri) {
        return uri == null ? null : uri.getHost();
    }

    private void logBudgetDecision(JobDiscoveryBudgetDecision decision) {
        boolean healthy = decision.status() == JobDiscoveryBudgetStatus.ALLOWED
                || decision.status() == JobDiscoveryBudgetStatus.NOT_REQUIRED;
        if (healthy) {
            log.info("Job discovery budget decision - status: {}, estimatedTotalCredits: {}, remainingCredits: {}, "
                            + "reserveCredits: {}, monthlyLimit: {}, billingPeriod: {}..{}",
                    decision.status(), decision.estimatedTotalCredits(), decision.remainingCredits(),
                    decision.configuredReserveCredits(), decision.configuredMonthlyLimit(),
                    decision.billingPeriodStart(), decision.billingPeriodEnd());
        } else {
            log.warn("Job discovery budget decision - status: {}, estimatedTotalCredits: {}, remainingCredits: {}, "
                            + "reserveCredits: {}, monthlyLimit: {}, billingPeriod: {}..{}, reason: {}",
                    decision.status(), decision.estimatedTotalCredits(), decision.remainingCredits(),
                    decision.configuredReserveCredits(), decision.configuredMonthlyLimit(),
                    decision.billingPeriodStart(), decision.billingPeriodEnd(), decision.reasonCategory());
        }
    }

    private void logSummary(JobDiscoveryRunResult result) {
        log.info("Job discovery run summary - duration: {}, plannedQueries: {}, executedQueries: {}, failedQueries: {}, "
                        + "discoveredReferences: {}, invalidReferences: {}, duplicateReferencesInRun: {}, "
                        + "existingVacanciesSkipped: {}, uniqueReferencesAccepted: {}, scrapeAttempts: {}, "
                        + "scrapeSuccesses: {}, scrapeFailures: {}, extractionAttempts: {}, extractionSuccesses: {}, "
                        + "extractionFailures: {}, persistenceAttempts: {}, createdVacancies: {}, "
                        + "alreadyExistingAfterRace: {}, persistenceFailures: {}, queryLimitReached: {}, "
                        + "scrapeLimitReached: {}, extractionLimitReached: {}, uniqueReferenceLimitReached: {}, "
                        + "omittedIssueCount: {}",
                result.duration(), result.plannedQueries(), result.executedQueries(), result.failedQueries(),
                result.discoveredReferences(), result.invalidReferences(), result.duplicateReferencesInRun(),
                result.existingVacanciesSkipped(), result.uniqueReferencesAccepted(), result.scrapeAttempts(),
                result.scrapeSuccesses(), result.scrapeFailures(), result.extractionAttempts(), result.extractionSuccesses(),
                result.extractionFailures(), result.persistenceAttempts(), result.createdVacancies(),
                result.alreadyExistingAfterRace(), result.persistenceFailures(), result.queryLimitReached(),
                result.scrapeLimitReached(), result.extractionLimitReached(), result.uniqueReferenceLimitReached(),
                result.omittedIssueCount());
    }

    /** Mutable, run-scoped counters/state - never exposed outside this class; {@link #toResult} builds the immutable result. */
    private static final class RunAccumulator {

        private final int maxReportedIssues;
        private final Set<CanonicalVacancyUrl> seenCanonicalUrls = new LinkedHashSet<>();
        private final List<UUID> createdVacancyIds = new ArrayList<>();
        private final List<JobDiscoveryIssue> issues = new ArrayList<>();

        private int nextReferenceOrdinal = 0;
        private int omittedIssueCount = 0;

        private int plannedQueries = 0;
        private int executedQueries = 0;
        private int failedQueries = 0;
        private int discoveredReferences = 0;
        private int invalidReferences = 0;
        private int duplicateReferencesInRun = 0;
        private int existingVacanciesSkipped = 0;
        private int uniqueReferencesAccepted = 0;
        private int scrapeAttempts = 0;
        private int scrapeSuccesses = 0;
        private int scrapeFailures = 0;
        private int extractionAttempts = 0;
        private int extractionSuccesses = 0;
        private int extractionFailures = 0;
        private int persistenceAttempts = 0;
        private int createdVacancies = 0;
        private int alreadyExistingAfterRace = 0;
        private int persistenceFailures = 0;
        private boolean queryLimitReached = false;
        private boolean scrapeLimitReached = false;
        private boolean extractionLimitReached = false;
        private boolean uniqueReferenceLimitReached = false;

        private RunAccumulator(int maxReportedIssues) {
            this.maxReportedIssues = maxReportedIssues;
        }

        private void addIssue(JobDiscoveryIssueCategory category, Integer queryOrdinal, String sourceHost,
                               String sanitizedErrorCategory, Integer referenceOrdinal) {
            if (issues.size() < maxReportedIssues) {
                issues.add(new JobDiscoveryIssue(category, queryOrdinal, sourceHost, sanitizedErrorCategory, referenceOrdinal));
            } else {
                omittedIssueCount++;
            }
        }

        private JobDiscoveryRunResult toResult(Instant startedAt, Instant completedAt, Duration duration,
                                                JobDiscoveryBudgetDecision budgetDecision) {
            return new JobDiscoveryRunResult(
                    startedAt, completedAt, duration,
                    plannedQueries, executedQueries, failedQueries,
                    discoveredReferences, invalidReferences, duplicateReferencesInRun,
                    existingVacanciesSkipped, uniqueReferencesAccepted,
                    scrapeAttempts, scrapeSuccesses, scrapeFailures,
                    extractionAttempts, extractionSuccesses, extractionFailures,
                    persistenceAttempts, createdVacancies, alreadyExistingAfterRace, persistenceFailures,
                    queryLimitReached, scrapeLimitReached, extractionLimitReached, uniqueReferenceLimitReached,
                    createdVacancyIds, issues, omittedIssueCount, budgetDecision);
        }
    }
}
