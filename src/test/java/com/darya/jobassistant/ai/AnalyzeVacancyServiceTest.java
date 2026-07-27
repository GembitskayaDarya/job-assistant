package com.darya.jobassistant.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.ai.config.JobAnalysisProperties;
import com.darya.jobassistant.ai.dto.AnalyzeVacancyResult;
import com.darya.jobassistant.ai.entity.AnalysisStatus;
import com.darya.jobassistant.ai.entity.JobAnalysisEntity;
import com.darya.jobassistant.ai.exception.JobAnalysisException;
import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.ai.repository.JobAnalysisRepository;
import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.CandidateProfileProvider;
import com.darya.jobassistant.candidates.CandidateSkill;
import com.darya.jobassistant.candidates.SkillProficiency;
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
    private CandidateProfileProvider candidateProfileProvider;

    @Mock
    private JobAnalysisService jobAnalysisService;

    @Mock
    private JobAnalysisRepository jobAnalysisRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private AnalyzeVacancyService service;

    @BeforeEach
    void setUp() {
        service = new AnalyzeVacancyService(
                vacancyQueryService,
                vacancyJobOfferMapper,
                candidateProfileProvider,
                jobAnalysisService,
                jobAnalysisRepository,
                CLOCK,
                new JobAnalysisProperties(STALE_AFTER),
                transactionManager);
    }

    @Test
    void analyze_existingCompletedAnalysis_returnsWithoutCallingAi() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        Vacancy vacancy = vacancy();
        JobOffer jobOffer = jobOffer();
        JobAnalysis analysis = jobAnalysis();
        when(vacancyQueryService.getById(VACANCY_ID)).thenReturn(vacancy);
        when(vacancyJobOfferMapper.toJobOffer(vacancy)).thenReturn(jobOffer);
        when(jobAnalysisRepository.claimIfAbsent(eq(VACANCY_ID), any())).thenReturn(Optional.empty());
        when(jobAnalysisRepository.findByVacancyId(VACANCY_ID)).thenReturn(Optional.of(completedEntity(analysis)));

        AnalyzeVacancyResult result = service.analyze(VACANCY_ID);

        assertThat(result).isEqualTo(new AnalyzeVacancyResult.Available(jobOffer, analysis, false));
        verify(jobAnalysisService, never()).analyze(any(), any());
        verify(jobAnalysisRepository, never()).completeClaim(any(), any(), any());
    }

    @Test
    void analyze_missingAnalysis_invokesAiPortAndPersists() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        Vacancy vacancy = vacancy();
        JobOffer jobOffer = jobOffer();
        CandidateProfile profile = candidateProfile();
        JobAnalysis analysis = jobAnalysis();
        when(vacancyQueryService.getById(VACANCY_ID)).thenReturn(vacancy);
        when(vacancyJobOfferMapper.toJobOffer(vacancy)).thenReturn(jobOffer);
        when(jobAnalysisRepository.claimIfAbsent(eq(VACANCY_ID), any())).thenReturn(Optional.of(inProgressEntity()));
        when(candidateProfileProvider.getProfile()).thenReturn(profile);
        when(jobAnalysisService.analyze(profile, jobOffer)).thenReturn(analysis);
        when(jobAnalysisRepository.completeClaim(VACANCY_ID, analysis, NOW)).thenReturn(true);

        AnalyzeVacancyResult result = service.analyze(VACANCY_ID);

        assertThat(result).isEqualTo(new AnalyzeVacancyResult.Available(jobOffer, analysis, true));
        verify(jobAnalysisService).analyze(profile, jobOffer);
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
        CandidateProfile profile = candidateProfile();
        when(vacancyQueryService.getById(VACANCY_ID)).thenReturn(vacancy);
        when(vacancyJobOfferMapper.toJobOffer(vacancy)).thenReturn(jobOffer);
        when(jobAnalysisRepository.claimIfAbsent(eq(VACANCY_ID), any())).thenReturn(Optional.of(inProgressEntity()));
        when(candidateProfileProvider.getProfile()).thenReturn(profile);
        RuntimeException providerFailure = new RuntimeException("HTTP 429 - insufficient_quota");
        when(jobAnalysisService.analyze(profile, jobOffer))
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
        when(jobAnalysisRepository.claimIfAbsent(eq(VACANCY_ID), any())).thenReturn(Optional.empty());
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
        when(jobAnalysisRepository.claimIfAbsent(eq(VACANCY_ID), any())).thenReturn(Optional.empty());
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
        when(jobAnalysisRepository.claimIfAbsent(eq(VACANCY_ID), any())).thenReturn(Optional.empty());
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
        CandidateProfile profile = candidateProfile();
        JobAnalysis analysis = jobAnalysis();
        when(vacancyQueryService.getById(VACANCY_ID)).thenReturn(vacancy);
        when(vacancyJobOfferMapper.toJobOffer(vacancy)).thenReturn(jobOffer);
        when(jobAnalysisRepository.claimIfAbsent(eq(VACANCY_ID), any())).thenReturn(Optional.empty());
        when(jobAnalysisRepository.findByVacancyId(VACANCY_ID)).thenReturn(Optional.of(inProgressEntity()));
        Instant staleThreshold = NOW.minus(STALE_AFTER);
        when(jobAnalysisRepository.reclaimStaleClaim(VACANCY_ID, NOW, staleThreshold)).thenReturn(true);
        when(candidateProfileProvider.getProfile()).thenReturn(profile);
        when(jobAnalysisService.analyze(profile, jobOffer)).thenReturn(analysis);
        when(jobAnalysisRepository.completeClaim(VACANCY_ID, analysis, NOW)).thenReturn(true);

        AnalyzeVacancyResult result = service.analyze(VACANCY_ID);

        assertThat(result).isEqualTo(new AnalyzeVacancyResult.Available(jobOffer, analysis, true));
        verify(jobAnalysisRepository).reclaimStaleClaim(VACANCY_ID, NOW, staleThreshold);
        verify(jobAnalysisService).analyze(profile, jobOffer);
    }

    @Test
    void analyze_noDatabaseTransactionIsHeldAroundTheAiCall() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        Vacancy vacancy = vacancy();
        JobOffer jobOffer = jobOffer();
        CandidateProfile profile = candidateProfile();
        JobAnalysis analysis = jobAnalysis();
        when(vacancyQueryService.getById(VACANCY_ID)).thenReturn(vacancy);
        when(vacancyJobOfferMapper.toJobOffer(vacancy)).thenReturn(jobOffer);
        when(jobAnalysisRepository.claimIfAbsent(eq(VACANCY_ID), any())).thenReturn(Optional.of(inProgressEntity()));
        when(candidateProfileProvider.getProfile()).thenReturn(profile);
        when(jobAnalysisService.analyze(profile, jobOffer)).thenReturn(analysis);
        when(jobAnalysisRepository.completeClaim(VACANCY_ID, analysis, NOW)).thenReturn(true);

        service.analyze(VACANCY_ID);

        // Transaction A (claim) commits, then the AI call happens, then Transaction B (complete)
        // opens - never a transaction wrapping the AI call itself.
        InOrder order = inOrder(transactionManager, jobAnalysisService);
        order.verify(transactionManager).getTransaction(any(TransactionDefinition.class));
        order.verify(jobAnalysisService).analyze(profile, jobOffer);
        order.verify(transactionManager).getTransaction(any(TransactionDefinition.class));
    }

    private Vacancy vacancy() {
        return Vacancy.builder().id(VACANCY_ID).build();
    }

    private JobOffer jobOffer() {
        return new JobOffer(
                VACANCY_ID.toString(), "Backend Engineer", "Acme Corp", null, "100000 USD", "desc", "https://example.com/job", "remoteok");
    }

    private CandidateProfile candidateProfile() {
        return new CandidateProfile(
                "Senior Java Backend Developer",
                "Senior",
                List.of(
                        new CandidateSkill("Java", SkillProficiency.WORKING, null),
                        new CandidateSkill("Spring Boot", SkillProficiency.WORKING, null)),
                List.of("English"),
                6,
                new CandidatePreferences(
                        null, "Remote Europe", null, List.of(), false, List.of(), null, "Product company", null, null));
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
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }
}
