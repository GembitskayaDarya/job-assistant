package com.darya.jobassistant.vacancyimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.ai.AnalyzeVacancyService;
import com.darya.jobassistant.ai.config.JobAnalysisProperties;
import com.darya.jobassistant.ai.entity.AnalysisStatus;
import com.darya.jobassistant.ai.entity.JobAnalysisEntity;
import com.darya.jobassistant.ai.model.AnalysisOrigin;
import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.ai.model.JobAnalysisModelVersion;
import com.darya.jobassistant.ai.port.JobAnalysisAiPort;
import com.darya.jobassistant.ai.repository.JobAnalysisRepository;
import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidatecontext.CandidateContextProvider;
import com.darya.jobassistant.candidatecontext.CandidateContextSnapshot;
import com.darya.jobassistant.candidatecontext.analysis.CandidateContextAnalysisProperties;
import com.darya.jobassistant.candidatecontext.analysis.CandidateContextForAnalysisSelector;
import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.CandidateSkill;
import com.darya.jobassistant.candidates.SkillProficiency;
import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.companies.mapper.CompanyMapper;
import com.darya.jobassistant.companies.repository.CompanyRepository;
import com.darya.jobassistant.companies.service.CompanyService;
import com.darya.jobassistant.integrations.ai.openai.JobAnalysisService;
import com.darya.jobassistant.vacancies.dto.VacancyPersistenceResult;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.mapper.VacancyJobOfferMapper;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import com.darya.jobassistant.vacancies.service.VacancyCreationService;
import com.darya.jobassistant.vacancies.service.VacancyQueryService;
import com.darya.jobassistant.vacancyextraction.VacancyExtractionContentPreparer;
import com.darya.jobassistant.vacancyextraction.VacancyExtractionService;
import com.darya.jobassistant.vacancyextraction.config.VacancyExtractionProperties;
import com.darya.jobassistant.vacancyextraction.model.ExtractedVacancyData;
import com.darya.jobassistant.vacancyextraction.model.RemotePolicy;
import com.darya.jobassistant.vacancyextraction.model.VacancyExtractionRequest;
import com.darya.jobassistant.vacancyextraction.port.VacancyExtractionPort;
import com.darya.jobassistant.vacancyimport.config.VacancyImportProperties;
import com.darya.jobassistant.vacancyimport.dto.AnalyzeImportedVacancyResult;
import com.darya.jobassistant.vacancyimport.dto.ContinueVacancyImportResult;
import com.darya.jobassistant.vacancyimport.dto.ReviewVacancyImportResult;
import com.darya.jobassistant.vacancyimport.dto.StartVacancyImportResult;
import com.darya.jobassistant.vacancyimport.model.ImportState;
import com.darya.jobassistant.vacancyimport.model.VacancyImportAction;
import com.darya.jobassistant.vacancyimport.model.VacancyImportDraft;
import com.darya.jobassistant.vacancyimport.model.VacancyImportSession;
import com.darya.jobassistant.vacancyimport.repository.VacancyImportDraftRepository;
import com.darya.jobassistant.vacancyimport.repository.VacancyImportSessionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

/**
 * One focused, non-network test proving the complete guided-import workflow end to end:
 * {@code /add} -&gt; URL -&gt; description -&gt; extraction -&gt; Save -&gt; Analyze -&gt; repeated Analyze.
 *
 * <p>Composes the real application services ({@link VacancyImportService}, {@link
 * VacancyImportReviewService}, {@link AnalyzeVacancyService}) and real, dependency-free
 * collaborators ({@link VacancyJobOfferMapper}, {@link CompanyMapper}, {@link CompanyService},
 * {@link VacancyQueryService}, {@link JobAnalysisService}) directly - no Spring context - with
 * only the two AI ports and the JPA-facing repositories replaced by fakes/mocks, per the project's
 * "prefer application-level composition with fakes/mocks over Docker" convention for this kind of
 * coverage. Repository mocks are backed by {@link AtomicReference}s holding the same mutable
 * domain objects the services themselves mutate in place, so a later lookup call naturally
 * reflects an earlier write without needing a hand-rolled in-memory database.
 */
@ExtendWith(MockitoExtension.class)
class VacancyImportWorkflowEndToEndTest {

    private static final long CHAT_ID = 111L;
    private static final long USER_ID = 222L;
    private static final Instant NOW = Instant.parse("2026-07-24T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Duration TTL = Duration.ofHours(24);
    private static final String URL = "https://example.com/jobs/backend-engineer";
    private static final String DESCRIPTION = "We are hiring a Senior Java Backend Engineer with Kafka and AWS "
            + "experience. You will design and build backend services for our platform.";

    @Mock
    private VacancyImportSessionRepository sessionRepository;

    @Mock
    private VacancyImportDraftRepository draftRepository;

    @Mock
    private VacancyExtractionPort vacancyExtractionPort;

    @Mock
    private VacancyRepository vacancyRepository;

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private JobAnalysisRepository jobAnalysisRepository;

    @Mock
    private JobAnalysisAiPort jobAnalysisAiPort;

    @Mock
    private CandidateContextProvider candidateContextProvider;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private final AtomicReference<VacancyImportSession> persistedSession = new AtomicReference<>();
    private final AtomicReference<VacancyImportDraft> persistedDraft = new AtomicReference<>();
    private final AtomicReference<Vacancy> persistedVacancy = new AtomicReference<>();
    private final AtomicReference<JobAnalysisEntity> persistedAnalysis = new AtomicReference<>();

    private VacancyImportService importService;
    private VacancyImportReviewService reviewService;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);

        VacancyExtractionService extractionService = new VacancyExtractionService(
                vacancyExtractionPort, new VacancyExtractionContentPreparer(new VacancyExtractionProperties(40_000, 10_000)));
        importService = new VacancyImportService(
                sessionRepository, draftRepository, extractionService, CLOCK, new VacancyImportProperties(TTL), transactionManager);

        CompanyService companyService = new CompanyService(companyRepository, new CompanyMapper());
        VacancyJobOfferMapper vacancyJobOfferMapper = new VacancyJobOfferMapper();
        VacancyQueryService vacancyQueryService = new VacancyQueryService(vacancyRepository);
        VacancyCreationService vacancyCreationService =
                new VacancyCreationService(vacancyRepository, companyService);
        JobAnalysisService jobAnalysisService = new JobAnalysisService(jobAnalysisAiPort);
        CandidateContextForAnalysisSelector candidateContextForAnalysisSelector =
                new CandidateContextForAnalysisSelector(new CandidateContextAnalysisProperties(4, 6, 4, 4, 4, 4, 12, 1200, 12000));
        AnalyzeVacancyService analyzeVacancyService = new AnalyzeVacancyService(
                vacancyQueryService, vacancyJobOfferMapper, candidateContextProvider, candidateContextForAnalysisSelector,
                jobAnalysisService, jobAnalysisRepository, CLOCK, new JobAnalysisProperties(Duration.ofMinutes(2)), transactionManager);

        reviewService = new VacancyImportReviewService(
                sessionRepository, draftRepository, vacancyRepository, vacancyCreationService,
                vacancyJobOfferMapper, analyzeVacancyService, CLOCK, transactionManager);

        stubSessionRepository();
        stubDraftRepository();
        stubVacancyAndCompanyRepositories();
        stubJobAnalysisRepository();
    }

    @Test
    void fullGuidedImportWorkflow_fromAddThroughSaveAndAnalyze() {
        UUID sessionId = start();
        acceptUrl();
        VacancyImportDraft draft = acceptDescriptionAndExtract();
        UUID vacancyId = save(sessionId, draft);
        JobAnalysis firstAnalysis = analyze(sessionId, vacancyId, true);
        analyzeAgain_isIdempotent(sessionId, vacancyId, firstAnalysis);
        anotherUserCannotAnalyzeThisSession(sessionId);
    }

    private UUID start() {
        StartVacancyImportResult result = importService.start(CHAT_ID, USER_ID);

        assertThat(result).isInstanceOf(StartVacancyImportResult.Started.class);
        assertThat(persistedSession.get().getState()).isEqualTo(ImportState.WAITING_FOR_URL);
        return ((StartVacancyImportResult.Started) result).sessionId();
    }

    private void acceptUrl() {
        ContinueVacancyImportResult result = importService.handleText(CHAT_ID, USER_ID, URL);

        assertThat(result).isInstanceOf(ContinueVacancyImportResult.UrlAccepted.class);
        assertThat(persistedSession.get().getState()).isEqualTo(ImportState.WAITING_FOR_DESCRIPTION);
        assertThat(persistedSession.get().getSourceUrl()).isEqualTo(URL);
    }

    private VacancyImportDraft acceptDescriptionAndExtract() {
        ExtractedVacancyData extracted = new ExtractedVacancyData(
                "Senior Java Backend Engineer", "Acme Corp", "Remote / Europe", RemotePolicy.REMOTE,
                List.of("B2B"), List.of("Java", "Kafka", "AWS"), "20000-25000 PLN");
        when(vacancyExtractionPort.extract(any(VacancyExtractionRequest.class))).thenReturn(extracted);

        ContinueVacancyImportResult result = importService.handleText(CHAT_ID, USER_ID, DESCRIPTION);

        assertThat(result).isInstanceOf(ContinueVacancyImportResult.VacancyRecognized.class);
        VacancyImportDraft draft = ((ContinueVacancyImportResult.VacancyRecognized) result).draft();
        assertThat(draft.data()).isEqualTo(extracted);
        assertThat(persistedSession.get().getState()).isEqualTo(ImportState.WAITING_FOR_CONFIRMATION);
        // The complete raw description is preserved verbatim on the session - never rewritten by AI.
        assertThat(persistedSession.get().getRawDescription()).isEqualTo(DESCRIPTION);
        assertThat(persistedSession.get().getSourceUrl()).isEqualTo(URL);

        // Extraction is a separate AI operation from analysis: only the extraction port has been
        // touched so far.
        verify(vacancyExtractionPort, times(1)).extract(any(VacancyExtractionRequest.class));
        verifyNoInteractions(jobAnalysisAiPort);
        return draft;
    }

    private UUID save(UUID sessionId, VacancyImportDraft draft) {
        ReviewVacancyImportResult result = reviewService.review(sessionId, CHAT_ID, USER_ID, VacancyImportAction.SAVE);

        assertThat(result).isInstanceOf(ReviewVacancyImportResult.Saved.class);
        ReviewVacancyImportResult.Saved saved = (ReviewVacancyImportResult.Saved) result;
        assertThat(saved.newlyCreated()).isTrue();

        ArgumentCaptor<Vacancy> candidateCaptor = ArgumentCaptor.forClass(Vacancy.class);
        verify(vacancyRepository).saveIfAbsent(candidateCaptor.capture());
        Vacancy candidate = candidateCaptor.getValue();
        // The complete raw description becomes the Vacancy description, and the source URL is
        // preserved - both from the session, never from the draft's own (description-less) fields.
        assertThat(candidate.getDescription()).isEqualTo(DESCRIPTION);
        assertThat(candidate.getUrl()).isEqualTo(URL);
        assertThat(candidate.getTitle()).isEqualTo(draft.data().title());

        VacancyImportSession sessionAfterSave = persistedSession.get();
        assertThat(sessionAfterSave.getState()).isEqualTo(ImportState.COMPLETED);
        assertThat(sessionAfterSave.getVacancyId()).isNotNull();

        // Save must never call the analysis AI, and must not call the extraction AI again.
        verifyNoInteractions(jobAnalysisAiPort);
        verify(vacancyExtractionPort, times(1)).extract(any());
        return sessionAfterSave.getVacancyId();
    }

    private JobAnalysis analyze(UUID sessionId, UUID vacancyId, boolean expectNewlyCreated) {
        CandidateProfile profile = new CandidateProfile(
                "Senior Java Backend Developer",
                "Senior",
                List.of(
                        new CandidateSkill("Java", SkillProficiency.WORKING, null),
                        new CandidateSkill("Kafka", SkillProficiency.WORKING, null)),
                List.of("English"),
                6,
                new CandidatePreferences(
                        null, "Remote Europe", null, List.of(), false, List.of(), null, "Product company", null, null));
        CandidateContextSnapshot candidateContext =
                new CandidateContextSnapshot(UUID.randomUUID(), "primary", 0L, profile, Optional.empty());
        lenient().when(candidateContextProvider.loadCurrentContext()).thenReturn(candidateContext);
        JobAnalysis analysis = new JobAnalysis(
                88, List.of("Strong Java and Kafka match"), List.of(), List.of(), List.of(),
                "6 years vs. no stated requirement.", "Remote preference matches.", "Great fit");
        lenient().when(jobAnalysisAiPort.analyze(any(), any())).thenReturn(analysis);

        AnalyzeImportedVacancyResult result = reviewService.analyze(sessionId, CHAT_ID, USER_ID);

        assertThat(result).isInstanceOf(AnalyzeImportedVacancyResult.Available.class);
        AnalyzeImportedVacancyResult.Available available = (AnalyzeImportedVacancyResult.Available) result;
        assertThat(available.sessionId()).isEqualTo(sessionId);
        assertThat(available.vacancyId()).isEqualTo(vacancyId);
        assertThat(available.analysis()).isEqualTo(analysis);
        assertThat(available.newlyCreated()).isEqualTo(expectNewlyCreated);

        // Analyze must never call the extraction AI.
        verify(vacancyExtractionPort, times(1)).extract(any());
        return available.analysis();
    }

    private void analyzeAgain_isIdempotent(UUID sessionId, UUID vacancyId, JobAnalysis firstAnalysis) {
        AnalyzeImportedVacancyResult repeated = reviewService.analyze(sessionId, CHAT_ID, USER_ID);

        assertThat(repeated).isInstanceOf(AnalyzeImportedVacancyResult.Available.class);
        AnalyzeImportedVacancyResult.Available repeatedAvailable = (AnalyzeImportedVacancyResult.Available) repeated;
        assertThat(repeatedAvailable.sessionId()).isEqualTo(sessionId);
        assertThat(repeatedAvailable.vacancyId()).isEqualTo(vacancyId);
        assertThat(repeatedAvailable.analysis()).isEqualTo(firstAnalysis);
        assertThat(repeatedAvailable.newlyCreated()).isFalse();
        // Still exactly one AI analysis call across both Analyze presses.
        verify(jobAnalysisAiPort, times(1)).analyze(any(), any());
    }

    private void anotherUserCannotAnalyzeThisSession(UUID sessionId) {
        AnalyzeImportedVacancyResult result = reviewService.analyze(sessionId, CHAT_ID, 999L);

        assertThat(result).isEqualTo(new AnalyzeImportedVacancyResult.NotAvailable());
    }

    private void stubSessionRepository() {
        lenient().when(sessionRepository.saveAndFlushSession(any())).thenAnswer(inv -> {
            persistedSession.set(inv.getArgument(0));
            return persistedSession.get();
        });
        lenient().when(sessionRepository.saveSession(any())).thenAnswer(inv -> {
            persistedSession.set(inv.getArgument(0));
            return persistedSession.get();
        });
        lenient().when(sessionRepository.findSessionById(any())).thenAnswer(inv -> Optional.ofNullable(persistedSession.get()));
        lenient().when(sessionRepository.findActiveSession(CHAT_ID, USER_ID)).thenAnswer(inv -> {
            VacancyImportSession session = persistedSession.get();
            return session != null && session.isActive() ? Optional.of(session) : Optional.empty();
        });
        lenient().when(sessionRepository.acceptDescriptionIfWaiting(any(), any(), any())).thenReturn(true);
        lenient().when(sessionRepository.moveToWaitingForConfirmationIfExtracting(any(), any())).thenReturn(true);
        lenient().when(sessionRepository.completeIfWaitingForConfirmation(any(), any(), any())).thenReturn(true);
    }

    private void stubDraftRepository() {
        lenient().when(draftRepository.saveDraft(any(), any(), any())).thenAnswer(inv -> {
            UUID sessionId = inv.getArgument(0);
            ExtractedVacancyData data = inv.getArgument(1);
            Instant now = inv.getArgument(2);
            VacancyImportDraft draft = new VacancyImportDraft(UUID.randomUUID(), sessionId, data, now, now);
            persistedDraft.set(draft);
            return draft;
        });
        lenient().when(draftRepository.findDraftBySessionId(any())).thenAnswer(inv -> Optional.ofNullable(persistedDraft.get()));
    }

    private void stubVacancyAndCompanyRepositories() {
        lenient().when(companyRepository.findByNameIgnoreCase(any())).thenReturn(Optional.empty());
        lenient().when(companyRepository.save(any())).thenAnswer(inv -> {
            Company company = inv.getArgument(0);
            company.setId(UUID.randomUUID());
            return company;
        });
        lenient().when(vacancyRepository.findByCanonicalUrl(any())).thenReturn(Optional.empty());
        lenient().when(vacancyRepository.saveIfAbsent(any())).thenAnswer(inv -> {
            Vacancy candidate = inv.getArgument(0);
            candidate.setId(UUID.randomUUID());
            persistedVacancy.set(candidate);
            return VacancyPersistenceResult.inserted(candidate);
        });
        lenient().when(vacancyRepository.findByIdWithCompany(any())).thenAnswer(inv -> Optional.ofNullable(persistedVacancy.get()));
    }

    private void stubJobAnalysisRepository() {
        lenient().when(jobAnalysisRepository.claimIfAbsent(any(), any(), any(), any())).thenAnswer(inv -> {
            if (persistedAnalysis.get() != null) {
                return Optional.empty();
            }
            UUID vacancyId = inv.getArgument(0);
            Instant claimedAt = inv.getArgument(1);
            AnalysisOrigin origin = inv.getArgument(2);
            Instant manuallyReviewedAt = inv.getArgument(3);
            JobAnalysisEntity claim = JobAnalysisEntity.builder()
                    .id(UUID.randomUUID())
                    .vacancyId(vacancyId)
                    .status(AnalysisStatus.IN_PROGRESS)
                    .pros(List.of())
                    .cons(List.of())
                    .missingRequiredSkills(List.of())
                    .missingPreferredSkills(List.of())
                    .analysisOrigin(origin)
                    .manuallyReviewedAt(manuallyReviewedAt)
                    .createdAt(claimedAt)
                    .updatedAt(claimedAt)
                    .build();
            persistedAnalysis.set(claim);
            return Optional.of(claim);
        });
        lenient().when(jobAnalysisRepository.findByVacancyId(any())).thenAnswer(inv -> Optional.ofNullable(persistedAnalysis.get()));
        lenient().when(jobAnalysisRepository.completeClaim(any(), any(), any())).thenAnswer(inv -> {
            UUID vacancyId = inv.getArgument(0);
            JobAnalysis analysis = inv.getArgument(1);
            Instant completedAt = inv.getArgument(2);
            JobAnalysisEntity current = persistedAnalysis.get();
            JobAnalysisEntity completed = JobAnalysisEntity.builder()
                    .id(current.getId())
                    .vacancyId(vacancyId)
                    .status(AnalysisStatus.COMPLETED)
                    .score(analysis.score())
                    .summary(analysis.summary())
                    .pros(analysis.pros())
                    .cons(analysis.cons())
                    .missingRequiredSkills(analysis.missingRequiredSkills())
                    .missingPreferredSkills(analysis.missingPreferredSkills())
                    .experienceAssessment(analysis.experienceAssessment())
                    .preferencesAssessment(analysis.preferencesAssessment())
                    .analysisVersion(JobAnalysisModelVersion.CURRENT)
                    .analysisOrigin(current.getAnalysisOrigin())
                    .createdAt(current.getCreatedAt())
                    .updatedAt(completedAt)
                    .build();
            persistedAnalysis.set(completed);
            return true;
        });
    }
}
