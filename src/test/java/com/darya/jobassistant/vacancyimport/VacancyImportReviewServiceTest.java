package com.darya.jobassistant.vacancyimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.ai.AnalyzeVacancyUseCase;
import com.darya.jobassistant.ai.dto.AnalyzeVacancyResult;
import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.companies.service.CompanyService;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.vacancies.dto.VacancyCreationResult;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.mapper.VacancyJobOfferMapper;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import com.darya.jobassistant.vacancies.service.VacancyCreationService;
import com.darya.jobassistant.vacancyextraction.model.ExtractedVacancyData;
import com.darya.jobassistant.vacancyextraction.model.RemotePolicy;
import com.darya.jobassistant.vacancyimport.dto.AnalyzeImportedVacancyResult;
import com.darya.jobassistant.vacancyimport.dto.ReviewVacancyImportResult;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

@ExtendWith(MockitoExtension.class)
class VacancyImportReviewServiceTest {

    private static final long CHAT_ID = 111L;
    private static final long USER_ID = 222L;
    private static final Instant NOW = Instant.parse("2026-07-24T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Duration TTL = Duration.ofHours(24);
    private static final String URL = "https://example.com/jobs/123";

    @Mock
    private VacancyImportSessionRepository sessionRepository;

    @Mock
    private VacancyImportDraftRepository draftRepository;

    @Mock
    private VacancyRepository vacancyRepository;

    @Mock
    private VacancyCreationService vacancyCreationService;

    @Mock
    private CompanyService companyService;

    @Mock
    private VacancyJobOfferMapper vacancyJobOfferMapper;

    @Mock
    private AnalyzeVacancyUseCase analyzeVacancyUseCase;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private VacancyImportReviewService service;

    @BeforeEach
    void setUp() {
        service = new VacancyImportReviewService(
                sessionRepository, draftRepository, vacancyRepository, vacancyCreationService, companyService,
                vacancyJobOfferMapper, analyzeVacancyUseCase, CLOCK, transactionManager);
    }

    // ---- Save ----

    @Test
    void save_ownedWaitingForConfirmationSession_succeedsAndCompletesSession() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        VacancyImportSession session = sessionAtWaitingForConfirmation();
        VacancyImportDraft draft = draft(session.getId(), validExtractedData());
        Company company = company("Example Company");
        Vacancy inserted = vacancy(company);
        when(sessionRepository.findSessionById(session.getId())).thenReturn(Optional.of(session));
        when(draftRepository.findDraftBySessionId(session.getId())).thenReturn(Optional.of(draft));
        when(companyService.findOrCreateByName("Example Company")).thenReturn(company);
        when(vacancyCreationService.createIfAbsent(any(Vacancy.class))).thenReturn(new VacancyCreationResult(inserted, true));
        when(sessionRepository.completeIfWaitingForConfirmation(session.getId(), inserted.getId(), NOW)).thenReturn(true);
        when(vacancyJobOfferMapper.toJobOffer(inserted)).thenReturn(jobOffer());

        ReviewVacancyImportResult result = service.review(session.getId(), CHAT_ID, USER_ID, VacancyImportAction.SAVE);

        assertThat(result).isEqualTo(new ReviewVacancyImportResult.Saved(session.getId(), jobOffer(), true));
        assertThat(session.getState()).isEqualTo(ImportState.COMPLETED);
        assertThat(session.getVacancyId()).isEqualTo(inserted.getId());
        verify(draftRepository).findDraftBySessionId(session.getId());

        ArgumentCaptor<Vacancy> candidateCaptor = ArgumentCaptor.forClass(Vacancy.class);
        verify(vacancyCreationService).createIfAbsent(candidateCaptor.capture());
        Vacancy candidate = candidateCaptor.getValue();
        assertThat(candidate.getTitle()).isEqualTo("Senior Java Backend Developer");
        assertThat(candidate.getCompany()).isEqualTo(company);
        assertThat(candidate.getUrl()).isEqualTo(URL);
        // The raw description preserved on the session - not any draft field - becomes the
        // persisted Vacancy description; ExtractedVacancyData has no description field at all.
        assertThat(candidate.getDescription()).isEqualTo(session.getRawDescription());
        assertThat(candidate.getDescription()).isEqualTo(VALID_DESCRIPTION);
        // Regression coverage: location, remoteMode and salaryText must survive the
        // draft -> Vacancy conversion on Save, not just title/company/description/url.
        assertThat(candidate.getLocation()).isEqualTo("Europe");
        assertThat(candidate.getRemoteMode()).isEqualTo(RemotePolicy.REMOTE);
        assertThat(candidate.getSalaryText()).isEqualTo("10-15k PLN");
    }

    @Test
    void save_draftWithLocationAndSalaryText_copiesThemVerbatimToTheVacancy() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        VacancyImportSession session = sessionAtWaitingForConfirmation();
        ExtractedVacancyData data = new ExtractedVacancyData(
                "Senior Java Backend Developer",
                "Example Company",
                "Warszawa/ Centrum",
                RemotePolicy.HYBRID,
                List.of("B2B"),
                List.of("Java", "Kafka"),
                "120-175 PLN netto/h +VAT");
        VacancyImportDraft draft = draft(session.getId(), data);
        Company company = company("Example Company");
        Vacancy inserted = vacancy(company);
        when(sessionRepository.findSessionById(session.getId())).thenReturn(Optional.of(session));
        when(draftRepository.findDraftBySessionId(session.getId())).thenReturn(Optional.of(draft));
        when(companyService.findOrCreateByName("Example Company")).thenReturn(company);
        when(vacancyCreationService.createIfAbsent(any(Vacancy.class))).thenReturn(new VacancyCreationResult(inserted, true));
        when(sessionRepository.completeIfWaitingForConfirmation(session.getId(), inserted.getId(), NOW)).thenReturn(true);
        when(vacancyJobOfferMapper.toJobOffer(inserted)).thenReturn(jobOffer());

        service.review(session.getId(), CHAT_ID, USER_ID, VacancyImportAction.SAVE);

        ArgumentCaptor<Vacancy> candidateCaptor = ArgumentCaptor.forClass(Vacancy.class);
        verify(vacancyCreationService).createIfAbsent(candidateCaptor.capture());
        Vacancy candidate = candidateCaptor.getValue();
        assertThat(candidate.getLocation()).isEqualTo("Warszawa/ Centrum");
        assertThat(candidate.getRemoteMode()).isEqualTo(RemotePolicy.HYBRID);
        assertThat(candidate.getSalaryText()).isEqualTo("120-175 PLN netto/h +VAT");
    }

    @Test
    void save_existingMatchingVacancy_reusesItWithoutOverwriting() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        VacancyImportSession session = sessionAtWaitingForConfirmation();
        VacancyImportDraft draft = draft(session.getId(), validExtractedData());
        Company company = company("Example Company");
        Vacancy existing = vacancy(company);
        when(sessionRepository.findSessionById(session.getId())).thenReturn(Optional.of(session));
        when(draftRepository.findDraftBySessionId(session.getId())).thenReturn(Optional.of(draft));
        when(companyService.findOrCreateByName("Example Company")).thenReturn(company);
        when(vacancyCreationService.createIfAbsent(any(Vacancy.class))).thenReturn(new VacancyCreationResult(existing, false));
        when(sessionRepository.completeIfWaitingForConfirmation(session.getId(), existing.getId(), NOW)).thenReturn(true);
        when(vacancyJobOfferMapper.toJobOffer(existing)).thenReturn(jobOffer());

        ReviewVacancyImportResult result = service.review(session.getId(), CHAT_ID, USER_ID, VacancyImportAction.SAVE);

        assertThat(result).isEqualTo(new ReviewVacancyImportResult.Saved(session.getId(), jobOffer(), false));
        verify(vacancyRepository, never()).save(any(Vacancy.class));
    }

    @Test
    void save_repeatedAfterCompletion_returnsTheLinkedVacancyWithoutInsertingAgain() {
        VacancyImportSession session = sessionAtWaitingForConfirmation();
        Vacancy linked = vacancy(company("Example Company"));
        session.complete(linked.getId(), CLOCK);
        when(sessionRepository.findSessionById(session.getId())).thenReturn(Optional.of(session));
        when(vacancyRepository.findByIdWithCompany(linked.getId())).thenReturn(Optional.of(linked));
        when(vacancyJobOfferMapper.toJobOffer(linked)).thenReturn(jobOffer());

        ReviewVacancyImportResult result = service.review(session.getId(), CHAT_ID, USER_ID, VacancyImportAction.SAVE);

        assertThat(result).isEqualTo(new ReviewVacancyImportResult.Saved(session.getId(), jobOffer(), false));
        verify(vacancyCreationService, never()).createIfAbsent(any());
        verify(draftRepository, never()).findDraftBySessionId(any());
        verify(transactionManager, never()).getTransaction(any(TransactionDefinition.class));
    }

    @Test
    void save_anotherUserCannotSaveTheSession() {
        VacancyImportSession session = sessionAtWaitingForConfirmation();
        when(sessionRepository.findSessionById(session.getId())).thenReturn(Optional.of(session));

        ReviewVacancyImportResult result = service.review(session.getId(), CHAT_ID, 999L, VacancyImportAction.SAVE);

        assertThat(result).isEqualTo(new ReviewVacancyImportResult.NotAvailable());
        verify(vacancyCreationService, never()).createIfAbsent(any());
        verify(draftRepository, never()).findDraftBySessionId(any());
    }

    @Test
    void save_anotherChatCannotSaveTheSession() {
        VacancyImportSession session = sessionAtWaitingForConfirmation();
        when(sessionRepository.findSessionById(session.getId())).thenReturn(Optional.of(session));

        ReviewVacancyImportResult result = service.review(session.getId(), 999L, USER_ID, VacancyImportAction.SAVE);

        assertThat(result).isEqualTo(new ReviewVacancyImportResult.NotAvailable());
        verify(vacancyCreationService, never()).createIfAbsent(any());
    }

    @Test
    void save_expiredSession_isNotSavedAndTransitionsToExpired() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        VacancyImportSession session = sessionAtWaitingForConfirmation(Clock.fixed(NOW.minus(Duration.ofHours(48)), ZoneOffset.UTC));
        when(sessionRepository.findSessionById(session.getId())).thenReturn(Optional.of(session));
        when(sessionRepository.expireIfWaitingForConfirmation(eq(session.getId()), any())).thenReturn(true);

        ReviewVacancyImportResult result = service.review(session.getId(), CHAT_ID, USER_ID, VacancyImportAction.SAVE);

        assertThat(result).isEqualTo(new ReviewVacancyImportResult.Expired(session.getId()));
        assertThat(session.getState()).isEqualTo(ImportState.EXPIRED);
        verify(vacancyCreationService, never()).createIfAbsent(any());
        verify(draftRepository, never()).findDraftBySessionId(any());
    }

    @Test
    void save_missingDraft_isReportedSafely() {
        VacancyImportSession session = sessionAtWaitingForConfirmation();
        when(sessionRepository.findSessionById(session.getId())).thenReturn(Optional.of(session));
        when(draftRepository.findDraftBySessionId(session.getId())).thenReturn(Optional.empty());

        ReviewVacancyImportResult result = service.review(session.getId(), CHAT_ID, USER_ID, VacancyImportAction.SAVE);

        assertThat(result).isEqualTo(new ReviewVacancyImportResult.DraftMissing(session.getId()));
        verify(vacancyCreationService, never()).createIfAbsent(any());
    }

    @Test
    void save_invalidState_doesNotCreateAVacancy() {
        VacancyImportSession session = sessionAtWaitingForDescription();
        when(sessionRepository.findSessionById(session.getId())).thenReturn(Optional.of(session));

        ReviewVacancyImportResult result = service.review(session.getId(), CHAT_ID, USER_ID, VacancyImportAction.SAVE);

        assertThat(result).isEqualTo(new ReviewVacancyImportResult.InvalidState(session.getId(), ImportState.WAITING_FOR_DESCRIPTION));
        verify(vacancyCreationService, never()).createIfAbsent(any());
    }

    @Test
    void save_conditionalCompletionLosesRace_rollsBackAndReloadsWinningState() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        VacancyImportSession session = sessionAtWaitingForConfirmation();
        VacancyImportDraft draft = draft(session.getId(), validExtractedData());
        Company company = company("Example Company");
        Vacancy inserted = vacancy(company);
        Vacancy winningVacancy = vacancy(company);
        when(sessionRepository.findSessionById(session.getId()))
                .thenReturn(Optional.of(session))
                .thenReturn(Optional.of(winnerSession(session.getId(), winningVacancy.getId())));
        when(draftRepository.findDraftBySessionId(session.getId())).thenReturn(Optional.of(draft));
        when(companyService.findOrCreateByName("Example Company")).thenReturn(company);
        when(vacancyCreationService.createIfAbsent(any(Vacancy.class))).thenReturn(new VacancyCreationResult(inserted, true));
        when(sessionRepository.completeIfWaitingForConfirmation(eq(session.getId()), eq(inserted.getId()), any())).thenReturn(false);
        when(vacancyJobOfferMapper.toJobOffer(inserted)).thenReturn(jobOffer());
        when(vacancyRepository.findByIdWithCompany(winningVacancy.getId())).thenReturn(Optional.of(winningVacancy));
        when(vacancyJobOfferMapper.toJobOffer(winningVacancy)).thenReturn(jobOffer());

        ReviewVacancyImportResult result = service.review(session.getId(), CHAT_ID, USER_ID, VacancyImportAction.SAVE);

        verify(transactionStatus).setRollbackOnly();
        assertThat(result).isInstanceOf(ReviewVacancyImportResult.Saved.class);
        assertThat(((ReviewVacancyImportResult.Saved) result).newlyCreated()).isFalse();
    }

    @Test
    void save_completedSessionWithoutVacancyId_isAnInvariantFailure() {
        VacancyImportSession corrupted = mock(VacancyImportSession.class);
        UUID sessionId = UUID.randomUUID();
        when(corrupted.getId()).thenReturn(sessionId);
        when(corrupted.getTelegramChatId()).thenReturn(CHAT_ID);
        when(corrupted.getTelegramUserId()).thenReturn(USER_ID);
        when(corrupted.getState()).thenReturn(ImportState.COMPLETED);
        when(corrupted.getVacancyId()).thenReturn(null);
        when(sessionRepository.findSessionById(sessionId)).thenReturn(Optional.of(corrupted));

        ReviewVacancyImportResult result = service.review(sessionId, CHAT_ID, USER_ID, VacancyImportAction.SAVE);

        assertThat(result).isEqualTo(new ReviewVacancyImportResult.Failed());
        verify(vacancyRepository, never()).findByIdWithCompany(any());
    }

    // ---- Retry ----

    @Test
    void retry_movesWaitingForConfirmationToWaitingForDescriptionAndPreservesUrl() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        VacancyImportSession session = sessionAtWaitingForConfirmation();
        when(sessionRepository.findSessionById(session.getId())).thenReturn(Optional.of(session));
        when(sessionRepository.retryIfWaitingForConfirmation(session.getId(), NOW)).thenReturn(true);

        ReviewVacancyImportResult result = service.review(session.getId(), CHAT_ID, USER_ID, VacancyImportAction.RETRY);

        assertThat(result).isEqualTo(new ReviewVacancyImportResult.RetryRequested(session.getId()));
        assertThat(session.getState()).isEqualTo(ImportState.WAITING_FOR_DESCRIPTION);
        assertThat(session.getSourceUrl()).isEqualTo(URL);
        assertThat(session.getRawDescription()).isNull();
        verify(draftRepository).deleteBySessionId(session.getId());
        verify(vacancyCreationService, never()).createIfAbsent(any());
    }

    @Test
    void retry_ownershipMismatch_isRejected() {
        VacancyImportSession session = sessionAtWaitingForConfirmation();
        when(sessionRepository.findSessionById(session.getId())).thenReturn(Optional.of(session));

        ReviewVacancyImportResult result = service.review(session.getId(), CHAT_ID, 999L, VacancyImportAction.RETRY);

        assertThat(result).isEqualTo(new ReviewVacancyImportResult.NotAvailable());
        verify(sessionRepository, never()).retryIfWaitingForConfirmation(any(), any());
    }

    @Test
    void retry_expiredSession_isHandledAsExpiredNotRetried() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        VacancyImportSession session = sessionAtWaitingForConfirmation(Clock.fixed(NOW.minus(Duration.ofHours(48)), ZoneOffset.UTC));
        when(sessionRepository.findSessionById(session.getId())).thenReturn(Optional.of(session));
        when(sessionRepository.expireIfWaitingForConfirmation(eq(session.getId()), any())).thenReturn(true);

        ReviewVacancyImportResult result = service.review(session.getId(), CHAT_ID, USER_ID, VacancyImportAction.RETRY);

        assertThat(result).isEqualTo(new ReviewVacancyImportResult.Expired(session.getId()));
        verify(sessionRepository, never()).retryIfWaitingForConfirmation(any(), any());
        verify(draftRepository, never()).deleteBySessionId(any());
    }

    @Test
    void retry_lostRaceAgainstConcurrentSave_reportsTheWinningSavedResult() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        VacancyImportSession session = sessionAtWaitingForConfirmation();
        UUID winningVacancyId = UUID.randomUUID();
        Vacancy winningVacancy = vacancy(company("Example Company"));
        when(sessionRepository.findSessionById(session.getId()))
                .thenReturn(Optional.of(session))
                .thenReturn(Optional.of(winnerSession(session.getId(), winningVacancy.getId())));
        when(sessionRepository.retryIfWaitingForConfirmation(session.getId(), NOW)).thenReturn(false);
        when(vacancyRepository.findByIdWithCompany(winningVacancy.getId())).thenReturn(Optional.of(winningVacancy));
        when(vacancyJobOfferMapper.toJobOffer(winningVacancy)).thenReturn(jobOffer());

        ReviewVacancyImportResult result = service.review(session.getId(), CHAT_ID, USER_ID, VacancyImportAction.RETRY);

        assertThat(result).isInstanceOf(ReviewVacancyImportResult.Saved.class);
    }

    @Test
    void retry_thenStaleSaveCallback_cannotCreateAVacancy() {
        VacancyImportSession sessionAfterRetry = sessionAtWaitingForDescription();
        when(sessionRepository.findSessionById(sessionAfterRetry.getId())).thenReturn(Optional.of(sessionAfterRetry));

        ReviewVacancyImportResult result = service.review(sessionAfterRetry.getId(), CHAT_ID, USER_ID, VacancyImportAction.SAVE);

        assertThat(result).isEqualTo(
                new ReviewVacancyImportResult.InvalidState(sessionAfterRetry.getId(), ImportState.WAITING_FOR_DESCRIPTION));
        verify(vacancyCreationService, never()).createIfAbsent(any());
    }

    // ---- Cancel ----

    @Test
    void cancel_targetsExactlyTheSuppliedSessionId() {
        VacancyImportSession session = sessionAtWaitingForConfirmation();
        when(sessionRepository.findSessionById(session.getId())).thenReturn(Optional.of(session));
        when(sessionRepository.cancelIfWaitingForConfirmation(session.getId(), NOW)).thenReturn(true);

        service.review(session.getId(), CHAT_ID, USER_ID, VacancyImportAction.CANCEL);

        verify(sessionRepository).findSessionById(session.getId());
        verify(sessionRepository).cancelIfWaitingForConfirmation(session.getId(), NOW);
    }

    @Test
    void cancel_movesSessionToCancelled() {
        VacancyImportSession session = sessionAtWaitingForConfirmation();
        when(sessionRepository.findSessionById(session.getId())).thenReturn(Optional.of(session));
        when(sessionRepository.cancelIfWaitingForConfirmation(session.getId(), NOW)).thenReturn(true);

        ReviewVacancyImportResult result = service.review(session.getId(), CHAT_ID, USER_ID, VacancyImportAction.CANCEL);

        assertThat(result).isEqualTo(new ReviewVacancyImportResult.Cancelled(session.getId()));
        assertThat(session.getState()).isEqualTo(ImportState.CANCELLED);
        verify(vacancyCreationService, never()).createIfAbsent(any());
    }

    @Test
    void cancel_staleCallbackTargetingOldSession_neverTouchesADifferentNewerSession() {
        VacancyImportSession oldSession = sessionAtWaitingForConfirmation();
        oldSession.cancel(CLOCK); // already resolved independently, e.g. by an earlier action
        UUID newerSessionId = UUID.randomUUID();
        when(sessionRepository.findSessionById(oldSession.getId())).thenReturn(Optional.of(oldSession));

        service.review(oldSession.getId(), CHAT_ID, USER_ID, VacancyImportAction.CANCEL);

        verify(sessionRepository, never()).findSessionById(newerSessionId);
        verify(sessionRepository, never()).cancelIfWaitingForConfirmation(eq(newerSessionId), any());
    }

    @Test
    void cancel_repeatedCancel_isSafeAndDoesNotReTouchTheRow() {
        VacancyImportSession session = sessionAtWaitingForConfirmation();
        session.cancel(CLOCK);
        when(sessionRepository.findSessionById(session.getId())).thenReturn(Optional.of(session));

        ReviewVacancyImportResult result = service.review(session.getId(), CHAT_ID, USER_ID, VacancyImportAction.CANCEL);

        assertThat(result).isEqualTo(new ReviewVacancyImportResult.Cancelled(session.getId()));
        verify(sessionRepository, never()).cancelIfWaitingForConfirmation(any(), any());
    }

    @Test
    void cancel_ownershipMismatch_isRejected() {
        VacancyImportSession session = sessionAtWaitingForConfirmation();
        when(sessionRepository.findSessionById(session.getId())).thenReturn(Optional.of(session));

        ReviewVacancyImportResult result = service.review(session.getId(), 999L, USER_ID, VacancyImportAction.CANCEL);

        assertThat(result).isEqualTo(new ReviewVacancyImportResult.NotAvailable());
        verify(sessionRepository, never()).cancelIfWaitingForConfirmation(any(), any());
    }

    @Test
    void cancel_expiredSession_isHandledSafely() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        VacancyImportSession session = sessionAtWaitingForConfirmation(Clock.fixed(NOW.minus(Duration.ofHours(48)), ZoneOffset.UTC));
        when(sessionRepository.findSessionById(session.getId())).thenReturn(Optional.of(session));
        when(sessionRepository.expireIfWaitingForConfirmation(eq(session.getId()), any())).thenReturn(true);

        ReviewVacancyImportResult result = service.review(session.getId(), CHAT_ID, USER_ID, VacancyImportAction.CANCEL);

        assertThat(result).isEqualTo(new ReviewVacancyImportResult.Expired(session.getId()));
        verify(sessionRepository, never()).cancelIfWaitingForConfirmation(any(), any());
    }

    @Test
    void cancel_draftIsRetainedForHistoricalDiagnostics() {
        VacancyImportSession session = sessionAtWaitingForConfirmation();
        when(sessionRepository.findSessionById(session.getId())).thenReturn(Optional.of(session));
        when(sessionRepository.cancelIfWaitingForConfirmation(session.getId(), NOW)).thenReturn(true);

        service.review(session.getId(), CHAT_ID, USER_ID, VacancyImportAction.CANCEL);

        verify(draftRepository, never()).deleteBySessionId(any());
    }

    // ---- AnalyzeImportedVacancy ----

    @Test
    void analyze_ownedCompletedSession_delegatesUsingLinkedVacancyId() {
        VacancyImportSession session = sessionAtWaitingForConfirmation();
        UUID vacancyId = UUID.randomUUID();
        session.complete(vacancyId, CLOCK);
        when(sessionRepository.findSessionById(session.getId())).thenReturn(Optional.of(session));
        JobOffer jobOffer = jobOffer();
        JobAnalysis analysis = jobAnalysis();
        when(analyzeVacancyUseCase.analyze(vacancyId)).thenReturn(new AnalyzeVacancyResult.Available(jobOffer, analysis, true));

        var result = service.analyze(session.getId(), CHAT_ID, USER_ID);

        verify(analyzeVacancyUseCase).analyze(vacancyId);
        assertThat(result).isEqualTo(new AnalyzeImportedVacancyResult.Available(
                session.getId(), vacancyId, jobOffer, analysis, true));
    }

    @Test
    void analyze_chatOwnershipMismatch_returnsNotAvailable() {
        VacancyImportSession session = sessionAtWaitingForConfirmation();
        session.complete(UUID.randomUUID(), CLOCK);
        when(sessionRepository.findSessionById(session.getId())).thenReturn(Optional.of(session));

        var result = service.analyze(session.getId(), 999L, USER_ID);

        assertThat(result).isEqualTo(new AnalyzeImportedVacancyResult.NotAvailable());
        verify(analyzeVacancyUseCase, never()).analyze(any());
    }

    @Test
    void analyze_userOwnershipMismatch_returnsNotAvailable() {
        VacancyImportSession session = sessionAtWaitingForConfirmation();
        session.complete(UUID.randomUUID(), CLOCK);
        when(sessionRepository.findSessionById(session.getId())).thenReturn(Optional.of(session));

        var result = service.analyze(session.getId(), CHAT_ID, 999L);

        assertThat(result).isEqualTo(new AnalyzeImportedVacancyResult.NotAvailable());
        verify(analyzeVacancyUseCase, never()).analyze(any());
    }

    @Test
    void analyze_missingSession_returnsSameNonDisclosingResultAsOwnershipMismatch() {
        UUID sessionId = UUID.randomUUID();
        when(sessionRepository.findSessionById(sessionId)).thenReturn(Optional.empty());

        var result = service.analyze(sessionId, CHAT_ID, USER_ID);

        assertThat(result).isEqualTo(new AnalyzeImportedVacancyResult.NotAvailable());
        verify(analyzeVacancyUseCase, never()).analyze(any());
    }

    @Test
    void analyze_sessionNotCompleted_doesNotAnalyze() {
        VacancyImportSession session = sessionAtWaitingForConfirmation();
        when(sessionRepository.findSessionById(session.getId())).thenReturn(Optional.of(session));

        var result = service.analyze(session.getId(), CHAT_ID, USER_ID);

        assertThat(result).isEqualTo(
                new AnalyzeImportedVacancyResult.InvalidState(ImportState.WAITING_FOR_CONFIRMATION));
        verify(analyzeVacancyUseCase, never()).analyze(any());
    }

    @Test
    void analyze_completedSessionWithoutVacancyId_returnsSafeInvariantResult() {
        VacancyImportSession corrupted = mock(VacancyImportSession.class);
        UUID sessionId = UUID.randomUUID();
        when(corrupted.getTelegramChatId()).thenReturn(CHAT_ID);
        when(corrupted.getTelegramUserId()).thenReturn(USER_ID);
        when(corrupted.getState()).thenReturn(ImportState.COMPLETED);
        when(sessionRepository.findSessionById(sessionId)).thenReturn(Optional.of(corrupted));

        var result = service.analyze(sessionId, CHAT_ID, USER_ID);

        assertThat(result).isEqualTo(new AnalyzeImportedVacancyResult.MissingLinkedVacancy(sessionId));
        verify(analyzeVacancyUseCase, never()).analyze(any());
    }

    @Test
    void analyze_inProgressResult_isMappedCorrectly() {
        VacancyImportSession session = sessionAtWaitingForConfirmation();
        UUID vacancyId = UUID.randomUUID();
        session.complete(vacancyId, CLOCK);
        when(sessionRepository.findSessionById(session.getId())).thenReturn(Optional.of(session));
        when(analyzeVacancyUseCase.analyze(vacancyId)).thenReturn(new AnalyzeVacancyResult.InProgress());

        var result = service.analyze(session.getId(), CHAT_ID, USER_ID);

        assertThat(result).isEqualTo(new AnalyzeImportedVacancyResult.InProgress());
    }

    @Test
    void analyze_failedResult_isMappedSafely() {
        VacancyImportSession session = sessionAtWaitingForConfirmation();
        UUID vacancyId = UUID.randomUUID();
        session.complete(vacancyId, CLOCK);
        when(sessionRepository.findSessionById(session.getId())).thenReturn(Optional.of(session));
        when(analyzeVacancyUseCase.analyze(vacancyId)).thenReturn(new AnalyzeVacancyResult.Failed());

        var result = service.analyze(session.getId(), CHAT_ID, USER_ID);

        assertThat(result).isEqualTo(new AnalyzeImportedVacancyResult.Failed());
    }

    @Test
    void analyze_vacancyNotFoundFromSharedUseCase_isMappedToMissingLinkedVacancy() {
        VacancyImportSession session = sessionAtWaitingForConfirmation();
        UUID vacancyId = UUID.randomUUID();
        session.complete(vacancyId, CLOCK);
        when(sessionRepository.findSessionById(session.getId())).thenReturn(Optional.of(session));
        when(analyzeVacancyUseCase.analyze(vacancyId)).thenReturn(new AnalyzeVacancyResult.VacancyNotFound());

        var result = service.analyze(session.getId(), CHAT_ID, USER_ID);

        assertThat(result).isEqualTo(
                new AnalyzeImportedVacancyResult.MissingLinkedVacancy(session.getId()));
    }

    // ---- helpers ----

    private static final String VALID_DESCRIPTION =
            "We are looking for a Senior Java Backend Engineer with Kafka experience for our team.";

    private VacancyImportSession sessionAtWaitingForDescription() {
        VacancyImportSession session = VacancyImportSession.start(CHAT_ID, USER_ID, CLOCK, TTL);
        session.provideUrl(URL, CLOCK);
        return session;
    }

    private VacancyImportSession sessionAtWaitingForConfirmation() {
        return sessionAtWaitingForConfirmation(CLOCK);
    }

    private VacancyImportSession sessionAtWaitingForConfirmation(Clock startClock) {
        VacancyImportSession session = VacancyImportSession.start(CHAT_ID, USER_ID, startClock, TTL);
        session.provideUrl(URL, startClock);
        session.provideDescriptionAndStartExtraction(VALID_DESCRIPTION, startClock);
        session.markExtractionSucceeded(startClock);
        return session;
    }

    private VacancyImportSession winnerSession(UUID sessionId, UUID vacancyId) {
        VacancyImportSession winner = mock(VacancyImportSession.class);
        when(winner.getId()).thenReturn(sessionId);
        when(winner.getState()).thenReturn(ImportState.COMPLETED);
        when(winner.getVacancyId()).thenReturn(vacancyId);
        return winner;
    }

    private ExtractedVacancyData validExtractedData() {
        return new ExtractedVacancyData(
                "Senior Java Backend Developer",
                "Example Company",
                "Europe",
                RemotePolicy.REMOTE,
                List.of("B2B"),
                List.of("Java", "Kafka"),
                "10-15k PLN");
    }

    private VacancyImportDraft draft(UUID sessionId, ExtractedVacancyData data) {
        return new VacancyImportDraft(UUID.randomUUID(), sessionId, data, NOW, NOW);
    }

    private Company company(String name) {
        return Company.builder().id(UUID.randomUUID()).name(name).build();
    }

    private Vacancy vacancy(Company company) {
        return Vacancy.builder().id(UUID.randomUUID()).company(company).title("Senior Java Backend Developer").url(URL).build();
    }

    private JobOffer jobOffer() {
        return new JobOffer("id", "Senior Java Backend Developer", "Example Company", null, null, VALID_DESCRIPTION, URL, "example.com");
    }

    private JobAnalysis jobAnalysis() {
        return new JobAnalysis(
                85, List.of("Strong Java skills"), List.of(), List.of("Kafka"), List.of(),
                "6 years vs. no stated requirement.", "Remote preference matches.", "Good match");
    }
}
