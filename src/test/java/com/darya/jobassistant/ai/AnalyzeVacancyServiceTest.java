package com.darya.jobassistant.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.ai.config.JobAnalysisProperties;
import com.darya.jobassistant.ai.dto.AnalyzeVacancyResult;
import com.darya.jobassistant.ai.entity.AnalysisStatus;
import com.darya.jobassistant.ai.entity.JobAnalysisEntity;
import com.darya.jobassistant.ai.exception.JobAnalysisException;
import com.darya.jobassistant.ai.model.AnalysisOrigin;
import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.ai.model.JobAnalysisModelVersion;
import com.darya.jobassistant.ai.repository.JobAnalysisRepository;
import com.darya.jobassistant.candidatecontext.CandidateContextProvider;
import com.darya.jobassistant.candidatecontext.CandidateContextSnapshot;
import com.darya.jobassistant.candidatecontext.CareerHistoryAvailability;
import com.darya.jobassistant.candidatecontext.analysis.CandidateContextForAnalysis;
import com.darya.jobassistant.candidatecontext.analysis.CandidateContextForAnalysisSelector;
import com.darya.jobassistant.candidatecontext.analysis.CandidateContextSelectionMetadata;
import com.darya.jobassistant.candidates.CandidateLanguageFacts;
import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.CandidateProfileFacts;
import com.darya.jobassistant.candidates.CandidateSkillFacts;
import com.darya.jobassistant.candidates.SkillProficiency;
import com.darya.jobassistant.candidates.migration.CandidateProfileAnalysisAssembler;
import com.darya.jobassistant.exception.VacancyNotFoundException;
import com.darya.jobassistant.integrations.ai.openai.JobAnalysisService;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.mapper.VacancyJobOfferMapper;
import com.darya.jobassistant.vacancies.service.VacancyQueryService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

@ExtendWith(MockitoExtension.class)
class AnalyzeVacancyServiceTest {

    private static final UUID VACANCY_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-07-24T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Duration STALE_AFTER = Duration.ofMinutes(2);

    @Mock
    private VacancyQueryService vacancyQueryService;

    @Mock
    private VacancyJobOfferMapper vacancyJobOfferMapper;

    @Mock
    private CandidateContextProvider candidateContextProvider;

    @Mock
    private CandidateContextForAnalysisSelector candidateContextForAnalysisSelector;

    @Mock
    private JobAnalysisService jobAnalysisService;

    @Mock
    private JobAnalysisRepository jobAnalysisRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private final CandidateContextForAnalysis analysisContext = new CandidateContextForAnalysis(
            candidateProfile(), CareerHistoryAvailability.NOT_PROVIDED, List.of(),
            CandidateContextSelectionMetadata.empty(CareerHistoryAvailability.NOT_PROVIDED, null));

    private AnalyzeVacancyService service;

    @BeforeEach
    void setUp() {
        service = new AnalyzeVacancyService(
                vacancyQueryService,
                vacancyJobOfferMapper,
                candidateContextProvider,
                candidateContextForAnalysisSelector,
                jobAnalysisService,
                jobAnalysisRepository,
                CLOCK,
                new JobAnalysisProperties(STALE_AFTER),
                transactionManager);
        lenient().when(candidateContextForAnalysisSelector.select(any(), any())).thenReturn(analysisContext);
    }

    @Test
    void analyze_existingCompletedAnalysis_returnsWithoutCallingAi() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        Vacancy vacancy = vacancy();
        JobOffer jobOffer = jobOffer();
        JobAnalysis analysis = jobAnalysis();
        when(vacancyQueryService.getById(VACANCY_ID)).thenReturn(vacancy);
        when(vacancyJobOfferMapper.toJobOffer(vacancy)).thenReturn(jobOffer);
        when(jobAnalysisRepository.claimIfAbsent(eq(VACANCY_ID), any(), any(), any())).thenReturn(Optional.empty());
        when(jobAnalysisRepository.findByVacancyId(VACANCY_ID)).thenReturn(Optional.of(completedEntity(analysis)));

        AnalyzeVacancyResult result = service.analyze(VACANCY_ID);

        assertThat(result).isEqualTo(new AnalyzeVacancyResult.Available(jobOffer, analysis, false));
        verify(jobAnalysisService, never()).analyze(any(), any());
        verify(jobAnalysisRepository, never()).completeClaim(any(), any(), any());
    }

    @Test
    void analyze_currentVersionAnalysis_repeatedManualCallsPerformOnlyOneAiCallTotal() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        Vacancy vacancy = vacancy();
        JobOffer jobOffer = jobOffer();
        CandidateContextSnapshot candidateContext = candidateContextSnapshot();
        JobAnalysis analysis = jobAnalysis();
        when(vacancyQueryService.getById(VACANCY_ID)).thenReturn(vacancy);
        when(vacancyJobOfferMapper.toJobOffer(vacancy)).thenReturn(jobOffer);
        when(candidateContextProvider.loadCurrentContext()).thenReturn(candidateContext);
        when(jobAnalysisService.analyze(analysisContext, jobOffer)).thenReturn(analysis);
        // First call: nothing exists yet, so a fresh claim is taken and AI is called once.
        when(jobAnalysisRepository.claimIfAbsent(eq(VACANCY_ID), any(), eq(AnalysisOrigin.MANUAL), any()))
                .thenReturn(Optional.of(inProgressEntity()));
        when(jobAnalysisRepository.completeClaim(VACANCY_ID, analysis, NOW)).thenReturn(true);

        AnalyzeVacancyResult first = service.analyze(VACANCY_ID);

        // Second call: the row now exists, completed at the current version - claimIfAbsent loses
        // and the completed, current-version row is returned without another AI call.
        when(jobAnalysisRepository.claimIfAbsent(eq(VACANCY_ID), any(), eq(AnalysisOrigin.MANUAL), any()))
                .thenReturn(Optional.empty());
        when(jobAnalysisRepository.findByVacancyId(VACANCY_ID))
                .thenReturn(Optional.of(completedEntity(analysis, JobAnalysisModelVersion.CURRENT, AnalysisOrigin.MANUAL)));

        AnalyzeVacancyResult second = service.analyze(VACANCY_ID);

        assertThat(first).isEqualTo(new AnalyzeVacancyResult.Available(jobOffer, analysis, true));
        assertThat(second).isEqualTo(new AnalyzeVacancyResult.Available(jobOffer, analysis, false));
        verify(jobAnalysisService, org.mockito.Mockito.times(1)).analyze(any(), any());
    }

    @Test
    void analyze_missingAnalysis_invokesAiPortAndPersists() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        Vacancy vacancy = vacancy();
        JobOffer jobOffer = jobOffer();
        CandidateContextSnapshot candidateContext = candidateContextSnapshot();
        JobAnalysis analysis = jobAnalysis();
        when(vacancyQueryService.getById(VACANCY_ID)).thenReturn(vacancy);
        when(vacancyJobOfferMapper.toJobOffer(vacancy)).thenReturn(jobOffer);
        when(jobAnalysisRepository.claimIfAbsent(eq(VACANCY_ID), any(), any(), any())).thenReturn(Optional.of(inProgressEntity()));
        when(candidateContextProvider.loadCurrentContext()).thenReturn(candidateContext);
        when(jobAnalysisService.analyze(analysisContext, jobOffer)).thenReturn(analysis);
        when(jobAnalysisRepository.completeClaim(VACANCY_ID, analysis, NOW)).thenReturn(true);

        AnalyzeVacancyResult result = service.analyze(VACANCY_ID);

        assertThat(result).isEqualTo(new AnalyzeVacancyResult.Available(jobOffer, analysis, true));
        verify(jobAnalysisService).analyze(analysisContext, jobOffer);
        verify(jobAnalysisRepository).completeClaim(VACANCY_ID, analysis, NOW);
    }

    @Test
    void analyze_vacancyNotFound_doesNotCallAi() {
        when(vacancyQueryService.getById(VACANCY_ID)).thenThrow(new VacancyNotFoundException(VACANCY_ID));

        AnalyzeVacancyResult result = service.analyze(VACANCY_ID);

        assertThat(result).isEqualTo(new AnalyzeVacancyResult.VacancyNotFound());
        verifyNoInteractions(jobAnalysisService, jobAnalysisRepository);
    }

    @Test
    void analyze_providerFailure_producesSafeResultAndReleasesClaimWithoutLeakingDetails() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        Vacancy vacancy = vacancy();
        JobOffer jobOffer = jobOffer();
        CandidateContextSnapshot candidateContext = candidateContextSnapshot();
        when(vacancyQueryService.getById(VACANCY_ID)).thenReturn(vacancy);
        when(vacancyJobOfferMapper.toJobOffer(vacancy)).thenReturn(jobOffer);
        when(jobAnalysisRepository.claimIfAbsent(eq(VACANCY_ID), any(), any(), any())).thenReturn(Optional.of(inProgressEntity()));
        when(candidateContextProvider.loadCurrentContext()).thenReturn(candidateContext);
        RuntimeException providerFailure = new RuntimeException("HTTP 429 - insufficient_quota");
        when(jobAnalysisService.analyze(analysisContext, jobOffer))
                .thenThrow(new JobAnalysisException("Failed to obtain job analysis from AI provider", providerFailure));

        AnalyzeVacancyResult result = service.analyze(VACANCY_ID);

        assertThat(result).isInstanceOf(AnalyzeVacancyResult.Failed.class);
        assertThat(result.toString()).doesNotContain("429", "insufficient_quota");
        verify(jobAnalysisRepository).releaseClaim(VACANCY_ID);
        verify(jobAnalysisRepository, never()).completeClaim(any(), any(), any());
    }

    @Test
    void analyze_repeatedCalls_returnPersistedAnalysisEachTime() {
        Vacancy vacancy = vacancy();
        JobOffer jobOffer = jobOffer();
        JobAnalysis analysis = jobAnalysis();
        when(vacancyQueryService.getById(VACANCY_ID)).thenReturn(vacancy);
        when(vacancyJobOfferMapper.toJobOffer(vacancy)).thenReturn(jobOffer);
        when(jobAnalysisRepository.claimIfAbsent(eq(VACANCY_ID), any(), any(), any())).thenReturn(Optional.empty());
        when(jobAnalysisRepository.findByVacancyId(VACANCY_ID)).thenReturn(Optional.of(completedEntity(analysis)));

        AnalyzeVacancyResult first = service.analyze(VACANCY_ID);
        AnalyzeVacancyResult second = service.analyze(VACANCY_ID);

        assertThat(first).isEqualTo(second);
        verify(jobAnalysisService, never()).analyze(any(), any());
    }

    @Test
    void analyze_lostInsertRaceAgainstAlreadyCompletedWinner_loadsTheWinningAnalysis() {
        Vacancy vacancy = vacancy();
        JobOffer jobOffer = jobOffer();
        JobAnalysis winningAnalysis = jobAnalysis();
        when(vacancyQueryService.getById(VACANCY_ID)).thenReturn(vacancy);
        when(vacancyJobOfferMapper.toJobOffer(vacancy)).thenReturn(jobOffer);
        // Our own insert lost the unique-constraint race: claimIfAbsent returns empty because
        // another caller's row already exists, and by the time we inspect it, that caller has
        // already completed it.
        when(jobAnalysisRepository.claimIfAbsent(eq(VACANCY_ID), any(), any(), any())).thenReturn(Optional.empty());
        when(jobAnalysisRepository.findByVacancyId(VACANCY_ID)).thenReturn(Optional.of(completedEntity(winningAnalysis)));

        AnalyzeVacancyResult result = service.analyze(VACANCY_ID);

        assertThat(result).isEqualTo(new AnalyzeVacancyResult.Available(jobOffer, winningAnalysis, false));
        verify(jobAnalysisService, never()).analyze(any(), any());
    }

    @Test
    void analyze_freshInProgressClaim_preventsDuplicateAiCallAndReturnsInProgress() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        Vacancy vacancy = vacancy();
        JobOffer jobOffer = jobOffer();
        when(vacancyQueryService.getById(VACANCY_ID)).thenReturn(vacancy);
        when(vacancyJobOfferMapper.toJobOffer(vacancy)).thenReturn(jobOffer);
        when(jobAnalysisRepository.claimIfAbsent(eq(VACANCY_ID), any(), any(), any())).thenReturn(Optional.empty());
        when(jobAnalysisRepository.findByVacancyId(VACANCY_ID)).thenReturn(Optional.of(inProgressEntity()));
        when(jobAnalysisRepository.reclaimStaleClaim(eq(VACANCY_ID), any(), any())).thenReturn(false);

        AnalyzeVacancyResult result = service.analyze(VACANCY_ID);

        assertThat(result).isEqualTo(new AnalyzeVacancyResult.InProgress());
        verify(jobAnalysisService, never()).analyze(any(), any());
        verify(jobAnalysisRepository, never()).completeClaim(any(), any(), any());
    }

    @Test
    void analyze_staleInProgressClaim_isReclaimedAndAiIsCalled() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        Vacancy vacancy = vacancy();
        JobOffer jobOffer = jobOffer();
        CandidateContextSnapshot candidateContext = candidateContextSnapshot();
        JobAnalysis analysis = jobAnalysis();
        when(vacancyQueryService.getById(VACANCY_ID)).thenReturn(vacancy);
        when(vacancyJobOfferMapper.toJobOffer(vacancy)).thenReturn(jobOffer);
        when(jobAnalysisRepository.claimIfAbsent(eq(VACANCY_ID), any(), any(), any())).thenReturn(Optional.empty());
        when(jobAnalysisRepository.findByVacancyId(VACANCY_ID)).thenReturn(Optional.of(inProgressEntity()));
        Instant staleThreshold = NOW.minus(STALE_AFTER);
        when(jobAnalysisRepository.reclaimStaleClaim(VACANCY_ID, NOW, staleThreshold)).thenReturn(true);
        when(candidateContextProvider.loadCurrentContext()).thenReturn(candidateContext);
        when(jobAnalysisService.analyze(analysisContext, jobOffer)).thenReturn(analysis);
        when(jobAnalysisRepository.completeClaim(VACANCY_ID, analysis, NOW)).thenReturn(true);

        AnalyzeVacancyResult result = service.analyze(VACANCY_ID);

        assertThat(result).isEqualTo(new AnalyzeVacancyResult.Available(jobOffer, analysis, true));
        verify(jobAnalysisRepository).reclaimStaleClaim(VACANCY_ID, NOW, staleThreshold);
        verify(jobAnalysisService).analyze(analysisContext, jobOffer);
    }

    @Test
    void analyze_noDatabaseTransactionIsHeldAroundTheAiCall() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        Vacancy vacancy = vacancy();
        JobOffer jobOffer = jobOffer();
        CandidateContextSnapshot candidateContext = candidateContextSnapshot();
        JobAnalysis analysis = jobAnalysis();
        when(vacancyQueryService.getById(VACANCY_ID)).thenReturn(vacancy);
        when(vacancyJobOfferMapper.toJobOffer(vacancy)).thenReturn(jobOffer);
        when(jobAnalysisRepository.claimIfAbsent(eq(VACANCY_ID), any(), any(), any())).thenReturn(Optional.of(inProgressEntity()));
        when(candidateContextProvider.loadCurrentContext()).thenReturn(candidateContext);
        when(jobAnalysisService.analyze(analysisContext, jobOffer)).thenReturn(analysis);
        when(jobAnalysisRepository.completeClaim(VACANCY_ID, analysis, NOW)).thenReturn(true);

        service.analyze(VACANCY_ID);

        // Transaction A (claim) commits, then the AI call happens, then Transaction B (complete)
        // opens - never a transaction wrapping the AI call itself.
        InOrder order = inOrder(transactionManager, jobAnalysisService);
        order.verify(transactionManager).getTransaction(any(TransactionDefinition.class));
        order.verify(jobAnalysisService).analyze(analysisContext, jobOffer);
        order.verify(transactionManager).getTransaction(any(TransactionDefinition.class));
    }

    @Test
    void analyze_outdatedCompletedAnalysis_safelyReanalyzesAndUpgradesVersionAndOrigin() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        Vacancy vacancy = vacancy();
        JobOffer jobOffer = jobOffer();
        CandidateContextSnapshot candidateContext = candidateContextSnapshot();
        JobAnalysis oldAnalysis = jobAnalysis();
        JobAnalysis newAnalysis = new JobAnalysis(
                90, List.of("Even stronger match"), List.of(), List.of(), List.of(),
                "6 years vs. 5+ requested - requirement met.", "Remote preference matches.", "Great match");
        JobAnalysisEntity outdated = completedEntity(oldAnalysis, 1, AnalysisOrigin.MONITORING);
        when(vacancyQueryService.getById(VACANCY_ID)).thenReturn(vacancy);
        when(vacancyJobOfferMapper.toJobOffer(vacancy)).thenReturn(jobOffer);
        when(jobAnalysisRepository.claimIfAbsent(eq(VACANCY_ID), any(), any(), any())).thenReturn(Optional.empty());
        when(jobAnalysisRepository.findByVacancyId(VACANCY_ID)).thenReturn(Optional.of(outdated));
        Instant staleThreshold = NOW.minus(STALE_AFTER);
        when(jobAnalysisRepository.attemptReanalysisClaim(VACANCY_ID, NOW, staleThreshold)).thenReturn(true);
        when(candidateContextProvider.loadCurrentContext()).thenReturn(candidateContext);
        when(jobAnalysisService.analyze(analysisContext, jobOffer)).thenReturn(newAnalysis);
        when(jobAnalysisRepository.completeReanalysis(VACANCY_ID, newAnalysis, NOW, NOW)).thenReturn(true);

        AnalyzeVacancyResult result = service.analyze(VACANCY_ID);

        assertThat(result).isEqualTo(new AnalyzeVacancyResult.Available(jobOffer, newAnalysis, true));
        verify(jobAnalysisService).analyze(analysisContext, jobOffer);
        verify(jobAnalysisRepository).completeReanalysis(VACANCY_ID, newAnalysis, NOW, NOW);
        verify(jobAnalysisRepository, never()).completeClaim(any(), any(), any());
    }

    /**
     * Sprint 9 Step 9 scenario A/D: a real, pre-existing {@code analysisVersion=2} row (exactly the
     * shape production data was left in before this sprint's {@code CURRENT} bump) is eligible for
     * reanalysis and, on a successful mocked AI call, upgrades via exactly one {@code
     * completeReanalysis} call - never a second write, never {@code completeClaim}.
     */
    @Test
    void analyze_existingVersion2CompletedAnalysis_isEligibleForReanalysisAndUpgradesOnSuccess() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        Vacancy vacancy = vacancy();
        JobOffer jobOffer = jobOffer();
        CandidateContextSnapshot candidateContext = candidateContextSnapshot();
        JobAnalysis oldAnalysis = jobAnalysis();
        JobAnalysis newAnalysis = new JobAnalysis(
                90, List.of("Even stronger match"), List.of(), List.of(), List.of(),
                "6 years vs. 5+ requested - requirement met.", "Remote preference matches.", "Great match");
        JobAnalysisEntity existingV2 = completedEntity(oldAnalysis, 2, AnalysisOrigin.MONITORING);
        when(vacancyQueryService.getById(VACANCY_ID)).thenReturn(vacancy);
        when(vacancyJobOfferMapper.toJobOffer(vacancy)).thenReturn(jobOffer);
        when(jobAnalysisRepository.claimIfAbsent(eq(VACANCY_ID), any(), any(), any())).thenReturn(Optional.empty());
        when(jobAnalysisRepository.findByVacancyId(VACANCY_ID)).thenReturn(Optional.of(existingV2));
        Instant staleThreshold = NOW.minus(STALE_AFTER);
        when(jobAnalysisRepository.attemptReanalysisClaim(VACANCY_ID, NOW, staleThreshold)).thenReturn(true);
        when(candidateContextProvider.loadCurrentContext()).thenReturn(candidateContext);
        when(jobAnalysisService.analyze(analysisContext, jobOffer)).thenReturn(newAnalysis);
        when(jobAnalysisRepository.completeReanalysis(VACANCY_ID, newAnalysis, NOW, NOW)).thenReturn(true);

        AnalyzeVacancyResult result = service.analyze(VACANCY_ID);

        assertThat(result).isEqualTo(new AnalyzeVacancyResult.Available(jobOffer, newAnalysis, true));
        verify(jobAnalysisRepository).attemptReanalysisClaim(VACANCY_ID, NOW, staleThreshold);
        verify(jobAnalysisService).analyze(analysisContext, jobOffer);
        // Exactly one replacement write - the repository's own completeReanalysis default method
        // is what stamps the persisted row at JobAnalysisModelVersion.CURRENT (3); see
        // JobAnalysisRepositoryTest for that persisted-version proof against a real database.
        verify(jobAnalysisRepository).completeReanalysis(VACANCY_ID, newAnalysis, NOW, NOW);
        verify(jobAnalysisRepository, never()).completeClaim(any(), any(), any());
    }

    /**
     * Sprint 9 Step 9 scenario B: a row already at {@code JobAnalysisModelVersion.CURRENT} (3) is
     * never reanalyzed solely because of its version - mirrors {@code
     * analyze_currentVersionAnalysis_repeatedManualCallsPerformOnlyOneAiCallTotal} but states the
     * literal current version explicitly for this sprint's regression scenario.
     */
    @Test
    void analyze_existingVersion3CompletedAnalysis_remainsCurrent_isNotReanalyzed() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        Vacancy vacancy = vacancy();
        JobOffer jobOffer = jobOffer();
        JobAnalysis analysis = jobAnalysis();
        JobAnalysisEntity existingV3 = completedEntity(analysis, 3, AnalysisOrigin.MANUAL);
        when(vacancyQueryService.getById(VACANCY_ID)).thenReturn(vacancy);
        when(vacancyJobOfferMapper.toJobOffer(vacancy)).thenReturn(jobOffer);
        when(jobAnalysisRepository.claimIfAbsent(eq(VACANCY_ID), any(), any(), any())).thenReturn(Optional.empty());
        when(jobAnalysisRepository.findByVacancyId(VACANCY_ID)).thenReturn(Optional.of(existingV3));

        AnalyzeVacancyResult result = service.analyze(VACANCY_ID);

        assertThat(result).isEqualTo(new AnalyzeVacancyResult.Available(jobOffer, analysis, false));
        verify(jobAnalysisRepository, never()).attemptReanalysisClaim(any(), any(), any());
        verify(jobAnalysisService, never()).analyze(any(), any());
        verify(jobAnalysisRepository).markManuallyReviewedIfAbsent(eq(VACANCY_ID), any());
    }

    @Test
    void analyze_reanalysisProviderFailure_releasesReanalysisClaimAndLeavesOldResultUntouched() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        Vacancy vacancy = vacancy();
        JobOffer jobOffer = jobOffer();
        CandidateContextSnapshot candidateContext = candidateContextSnapshot();
        JobAnalysis oldAnalysis = jobAnalysis();
        JobAnalysisEntity outdated = completedEntity(oldAnalysis, 1, AnalysisOrigin.MONITORING);
        when(vacancyQueryService.getById(VACANCY_ID)).thenReturn(vacancy);
        when(vacancyJobOfferMapper.toJobOffer(vacancy)).thenReturn(jobOffer);
        when(jobAnalysisRepository.claimIfAbsent(eq(VACANCY_ID), any(), any(), any())).thenReturn(Optional.empty());
        when(jobAnalysisRepository.findByVacancyId(VACANCY_ID)).thenReturn(Optional.of(outdated));
        Instant staleThreshold = NOW.minus(STALE_AFTER);
        when(jobAnalysisRepository.attemptReanalysisClaim(VACANCY_ID, NOW, staleThreshold)).thenReturn(true);
        when(candidateContextProvider.loadCurrentContext()).thenReturn(candidateContext);
        RuntimeException providerFailure = new RuntimeException("HTTP 429 - insufficient_quota");
        when(jobAnalysisService.analyze(analysisContext, jobOffer))
                .thenThrow(new JobAnalysisException("Failed to obtain job analysis from AI provider", providerFailure));

        AnalyzeVacancyResult result = service.analyze(VACANCY_ID);

        assertThat(result).isInstanceOf(AnalyzeVacancyResult.Failed.class);
        assertThat(result.toString()).doesNotContain("429", "insufficient_quota");
        verify(jobAnalysisRepository).releaseReanalysisClaim(VACANCY_ID, NOW);
        verify(jobAnalysisRepository, never()).completeReanalysis(any(), any(), any(), any());
        // The old completed row itself is never touched by this call - no update/delete method on
        // it is invoked besides the claim-field release above.
        verify(jobAnalysisRepository, never()).releaseClaim(any());
    }

    @Test
    void analyze_reanalysisAlreadyClaimedByAnotherCaller_doesNotCallAiAndReturnsOldResult() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        Vacancy vacancy = vacancy();
        JobOffer jobOffer = jobOffer();
        JobAnalysis oldAnalysis = jobAnalysis();
        JobAnalysisEntity outdated = completedEntity(oldAnalysis, 1, AnalysisOrigin.MONITORING);
        when(vacancyQueryService.getById(VACANCY_ID)).thenReturn(vacancy);
        when(vacancyJobOfferMapper.toJobOffer(vacancy)).thenReturn(jobOffer);
        when(jobAnalysisRepository.claimIfAbsent(eq(VACANCY_ID), any(), any(), any())).thenReturn(Optional.empty());
        when(jobAnalysisRepository.findByVacancyId(VACANCY_ID)).thenReturn(Optional.of(outdated));
        Instant staleThreshold = NOW.minus(STALE_AFTER);
        when(jobAnalysisRepository.attemptReanalysisClaim(VACANCY_ID, NOW, staleThreshold)).thenReturn(false);

        AnalyzeVacancyResult result = service.analyze(VACANCY_ID);

        assertThat(result).isEqualTo(new AnalyzeVacancyResult.Available(jobOffer, oldAnalysis, false));
        verify(jobAnalysisService, never()).analyze(any(), any());
        verify(jobAnalysisRepository, never()).completeReanalysis(any(), any(), any(), any());
    }

    @Test
    void analyze_newClaim_stampsManuallyReviewedAtOnTheClaimItself() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        Vacancy vacancy = vacancy();
        JobOffer jobOffer = jobOffer();
        CandidateContextSnapshot candidateContext = candidateContextSnapshot();
        JobAnalysis analysis = jobAnalysis();
        when(vacancyQueryService.getById(VACANCY_ID)).thenReturn(vacancy);
        when(vacancyJobOfferMapper.toJobOffer(vacancy)).thenReturn(jobOffer);
        when(jobAnalysisRepository.claimIfAbsent(eq(VACANCY_ID), any(), any(), any())).thenReturn(Optional.of(inProgressEntity()));
        when(candidateContextProvider.loadCurrentContext()).thenReturn(candidateContext);
        when(jobAnalysisService.analyze(analysisContext, jobOffer)).thenReturn(analysis);
        when(jobAnalysisRepository.completeClaim(VACANCY_ID, analysis, NOW)).thenReturn(true);

        service.analyze(VACANCY_ID);

        // Every /analyze-initiated claim is inherently a manual review action, stamped at the
        // exact moment the claim itself is taken - not left for a later, separate write.
        verify(jobAnalysisRepository).claimIfAbsent(VACANCY_ID, NOW, AnalysisOrigin.MANUAL, NOW);
    }

    @Test
    void analyze_reusingExistingCurrentVersionAnalysis_marksManuallyReviewedWithoutTouchingOrigin() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        Vacancy vacancy = vacancy();
        JobOffer jobOffer = jobOffer();
        JobAnalysis analysis = jobAnalysis();
        // Reused analysis was originally created automatically (AUTOMATIC_DISCOVERY) - reuse must
        // never overwrite that origin, only stamp the manual-review marker.
        JobAnalysisEntity existing = completedEntity(analysis, JobAnalysisModelVersion.CURRENT, AnalysisOrigin.AUTOMATIC_DISCOVERY);
        when(vacancyQueryService.getById(VACANCY_ID)).thenReturn(vacancy);
        when(vacancyJobOfferMapper.toJobOffer(vacancy)).thenReturn(jobOffer);
        when(jobAnalysisRepository.claimIfAbsent(eq(VACANCY_ID), any(), any(), any())).thenReturn(Optional.empty());
        when(jobAnalysisRepository.findByVacancyId(VACANCY_ID)).thenReturn(Optional.of(existing));

        service.analyze(VACANCY_ID);

        verify(jobAnalysisRepository).markManuallyReviewedIfAbsent(VACANCY_ID, NOW);
        verify(jobAnalysisRepository, never()).completeClaim(any(), any(), any());
        verify(jobAnalysisService, never()).analyze(any(), any());
    }

    @Test
    void analyze_reusingExistingLegacyOriginAnalysis_marksManuallyReviewedWithoutPromotingOriginOrCallingAi() {
        // A LEGACY-origin row (backfilled pre-provenance data) must be treated exactly like any
        // other origin here: /analyze stamps the manual-review marker and leaves the origin as
        // LEGACY - it is never rewritten to MANUAL merely because a human viewed it via /analyze.
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        Vacancy vacancy = vacancy();
        JobOffer jobOffer = jobOffer();
        JobAnalysis analysis = jobAnalysis();
        JobAnalysisEntity existing = completedEntity(analysis, JobAnalysisModelVersion.CURRENT, AnalysisOrigin.LEGACY);
        when(vacancyQueryService.getById(VACANCY_ID)).thenReturn(vacancy);
        when(vacancyJobOfferMapper.toJobOffer(vacancy)).thenReturn(jobOffer);
        when(jobAnalysisRepository.claimIfAbsent(eq(VACANCY_ID), any(), any(), any())).thenReturn(Optional.empty());
        when(jobAnalysisRepository.findByVacancyId(VACANCY_ID)).thenReturn(Optional.of(existing));

        service.analyze(VACANCY_ID);

        verify(jobAnalysisRepository).markManuallyReviewedIfAbsent(VACANCY_ID, NOW);
        verify(jobAnalysisRepository, never()).completeClaim(any(), any(), any());
        verify(jobAnalysisService, never()).analyze(any(), any());
    }

    private Vacancy vacancy() {
        return Vacancy.builder().id(VACANCY_ID).build();
    }

    private JobOffer jobOffer() {
        return new JobOffer(
                VACANCY_ID.toString(), "Backend Engineer", "Acme Corp", null, "100000 USD", "desc", "https://example.com/job", "remoteok");
    }

    private CandidateContextSnapshot candidateContextSnapshot() {
        return new CandidateContextSnapshot(UUID.randomUUID(), "primary", 0L, candidateProfileFacts(), Optional.empty());
    }

    private CandidateProfileFacts candidateProfileFacts() {
        return new CandidateProfileFacts(
                "Senior Java Backend Developer",
                "Senior",
                List.of(
                        new CandidateSkillFacts("Java", null, null, SkillProficiency.WORKING),
                        new CandidateSkillFacts("Spring Boot", null, null, SkillProficiency.WORKING)),
                List.of(new CandidateLanguageFacts("English", null)),
                6,
                new CandidatePreferences(
                        null, "Remote Europe", null, List.of(), false, List.of(), null, "Product company", null, null));
    }

    /** Derived from {@link #candidateProfileFacts()} so the field-initializer fixture below can never drift from it. */
    private CandidateProfile candidateProfile() {
        return CandidateProfileAnalysisAssembler.toAnalysisProfile(candidateProfileFacts());
    }

    private JobAnalysis jobAnalysis() {
        return new JobAnalysis(
                85, List.of("Strong Java skills"), List.of(), List.of("Kafka"), List.of(),
                "6 years vs. no stated requirement.", "Remote preference matches.", "Good match");
    }

    private JobAnalysisEntity inProgressEntity() {
        return JobAnalysisEntity.builder()
                .id(UUID.randomUUID())
                .vacancyId(VACANCY_ID)
                .status(AnalysisStatus.IN_PROGRESS)
                .pros(List.of())
                .cons(List.of())
                .missingRequiredSkills(List.of())
                .missingPreferredSkills(List.of())
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }

    private JobAnalysisEntity completedEntity(JobAnalysis analysis) {
        return completedEntity(analysis, JobAnalysisModelVersion.CURRENT, AnalysisOrigin.MANUAL);
    }

    private JobAnalysisEntity completedEntity(JobAnalysis analysis, int version, AnalysisOrigin origin) {
        return JobAnalysisEntity.builder()
                .id(UUID.randomUUID())
                .vacancyId(VACANCY_ID)
                .status(AnalysisStatus.COMPLETED)
                .score(analysis.score())
                .summary(analysis.summary())
                .pros(analysis.pros())
                .cons(analysis.cons())
                .missingRequiredSkills(analysis.missingRequiredSkills())
                .missingPreferredSkills(analysis.missingPreferredSkills())
                .experienceAssessment(analysis.experienceAssessment())
                .preferencesAssessment(analysis.preferencesAssessment())
                .analysisVersion(version)
                .analysisOrigin(origin)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }
}
