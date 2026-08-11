package com.darya.jobassistant.applicationmaterials.preparation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGeneration;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationActiveConflictException;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationRepositoryPort;
import com.darya.jobassistant.applicationmaterials.artifact.aggregate.ApplicationMaterialArtifact;
import com.darya.jobassistant.applicationmaterials.generation.GenerateApplicationMaterialsOutcome;
import com.darya.jobassistant.applicationmaterials.generation.GenerateApplicationMaterialsUseCase;
import com.darya.jobassistant.applicationmaterials.generation.GenerationOutcomeStatus;
import com.darya.jobassistant.applicationmaterials.render.RenderApplicationMaterialsException;
import com.darya.jobassistant.applicationmaterials.render.RenderApplicationMaterialsResult;
import com.darya.jobassistant.applicationmaterials.render.RenderApplicationMaterialsUseCase;
import com.darya.jobassistant.applicationmaterials.render.model.ApplicationMaterialFormat;
import com.darya.jobassistant.applicationmaterials.render.model.ApplicationMaterialType;
import com.darya.jobassistant.candidatecontext.CandidateContextProvider;
import com.darya.jobassistant.candidatecontext.CandidateContextSnapshot;
import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import com.darya.jobassistant.exception.VacancyNotFoundException;
import com.darya.jobassistant.integrations.filestorage.FileStorageNotFoundException;
import com.darya.jobassistant.integrations.filestorage.FileStoragePort;
import com.darya.jobassistant.integrations.filestorage.StoredFileContent;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.service.VacancyQueryService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrepareApplicationPackageUseCaseTest {

    private static final UUID VACANCY_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private VacancyQueryService vacancyQueryService;

    @Mock
    private CandidateContextProvider candidateContextProvider;

    @Mock
    private ApplicationMaterialGenerationRepositoryPort generationRepositoryPort;

    @Mock
    private GenerateApplicationMaterialsUseCase generateApplicationMaterialsUseCase;

    @Mock
    private RenderApplicationMaterialsUseCase renderApplicationMaterialsUseCase;

    @Mock
    private FileStoragePort fileStoragePort;

    @Mock
    private Vacancy vacancy;

    private PrepareApplicationPackageUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new PrepareApplicationPackageUseCase(
                vacancyQueryService, candidateContextProvider, generationRepositoryPort,
                generateApplicationMaterialsUseCase, renderApplicationMaterialsUseCase, fileStoragePort, CLOCK);
    }

    // ==================== Vacancy validation ====================

    @Test
    void prepare_unknownVacancy_returnsVacancyNotFoundWithoutFurtherWork() {
        when(vacancyQueryService.getById(VACANCY_ID)).thenThrow(new VacancyNotFoundException(VACANCY_ID));

        PrepareApplicationPackageOutcome outcome = useCase.prepare(VACANCY_ID);

        assertThat(outcome).isEqualTo(new PrepareApplicationPackageOutcome.VacancyNotFound(VACANCY_ID));
        verifyNoGenerationWork();
    }

    // ==================== No matching generation: create + generate + render ====================

    @Test
    void prepare_noExistingGeneration_createsNewGenerationAgainstCurrentVersions() {
        stubVacancyFound();
        stubCurrentVersions(5L, 2L);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of());

        ApplicationMaterialGeneration pending = pendingGeneration(5L, 2L);
        when(generationRepositoryPort.save(any(ApplicationMaterialGeneration.class))).thenReturn(pending);

        ApplicationMaterialGeneration completed = pending.start(NOW).complete(NOW);
        when(generateApplicationMaterialsUseCase.generate(pending.id()))
                .thenReturn(new GenerateApplicationMaterialsOutcome(GenerationOutcomeStatus.COMPLETED, completed, null));
        stubSuccessfulRenderAndLoad(completed.id());

        PrepareApplicationPackageOutcome outcome = useCase.prepare(VACANCY_ID);

        verify(generationRepositoryPort).save(argThatRequestsNewGeneration(5L, 2L));
        verify(generateApplicationMaterialsUseCase).generate(pending.id());
        assertPrepared(outcome, completed.id(), false);
    }

    @Test
    void prepare_noExistingGeneration_optionalCareerHistoryAbsent_createsGenerationWithNullCareerHistoryVersion() {
        stubVacancyFound();
        stubCurrentVersions(1L, null);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of());

        ApplicationMaterialGeneration pending = pendingGeneration(1L, null);
        when(generationRepositoryPort.save(any(ApplicationMaterialGeneration.class))).thenReturn(pending);
        ApplicationMaterialGeneration completed = pending.start(NOW).complete(NOW);
        when(generateApplicationMaterialsUseCase.generate(pending.id()))
                .thenReturn(new GenerateApplicationMaterialsOutcome(GenerationOutcomeStatus.COMPLETED, completed, null));
        stubSuccessfulRenderAndLoad(completed.id());

        useCase.prepare(VACANCY_ID);

        verify(generationRepositoryPort).save(argThatRequestsNewGeneration(1L, null));
    }

    @Test
    void prepare_newGeneration_rendersAndLoadsPdfsThroughFileStoragePort() {
        stubVacancyFound();
        stubCurrentVersions(1L, null);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of());
        ApplicationMaterialGeneration pending = pendingGeneration(1L, null);
        when(generationRepositoryPort.save(any(ApplicationMaterialGeneration.class))).thenReturn(pending);
        ApplicationMaterialGeneration completed = pending.start(NOW).complete(NOW);
        when(generateApplicationMaterialsUseCase.generate(pending.id()))
                .thenReturn(new GenerateApplicationMaterialsOutcome(GenerationOutcomeStatus.COMPLETED, completed, null));
        stubSuccessfulRenderAndLoad(completed.id());

        PrepareApplicationPackageOutcome outcome = useCase.prepare(VACANCY_ID);

        verify(renderApplicationMaterialsUseCase).render(completed.id());
        verify(fileStoragePort).load("cv-key");
        verify(fileStoragePort).load("cover-letter-key");
        PreparedApplicationPackage prepared = assertPrepared(outcome, completed.id(), false);
        assertThat(prepared.cv().fileName()).isEqualTo("CV.pdf");
        assertThat(prepared.cv().content()).isEqualTo("cv-bytes".getBytes());
        assertThat(prepared.coverLetter().fileName()).isEqualTo("Cover_Letter.pdf");
        assertThat(prepared.coverLetter().content()).isEqualTo("cl-bytes".getBytes());
    }

    // ==================== Matching COMPLETED: reuse, no AI call ====================

    @Test
    void prepare_matchingCompletedGeneration_reusesWithoutCallingGenerate() {
        stubVacancyFound();
        stubCurrentVersions(5L, 2L);
        ApplicationMaterialGeneration completed = pendingGeneration(5L, 2L).start(NOW).complete(NOW);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of(completed));
        stubSuccessfulRenderAndLoad(completed.id());

        PrepareApplicationPackageOutcome outcome = useCase.prepare(VACANCY_ID);

        verify(generateApplicationMaterialsUseCase, never()).generate(any());
        verify(generationRepositoryPort, never()).save(any());
        verify(renderApplicationMaterialsUseCase).render(completed.id());
        assertPrepared(outcome, completed.id(), true);
    }

    @Test
    void prepare_changedCandidateProfileVersion_doesNotReuseCompletedGeneration_createsNew() {
        stubVacancyFound();
        stubCurrentVersions(6L, 2L); // profile moved from 5 -> 6
        ApplicationMaterialGeneration staleCompleted = pendingGeneration(5L, 2L).start(NOW).complete(NOW);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of(staleCompleted));

        ApplicationMaterialGeneration freshPending = pendingGeneration(6L, 2L);
        when(generationRepositoryPort.save(any(ApplicationMaterialGeneration.class))).thenReturn(freshPending);
        ApplicationMaterialGeneration freshCompleted = freshPending.start(NOW).complete(NOW);
        when(generateApplicationMaterialsUseCase.generate(freshPending.id()))
                .thenReturn(new GenerateApplicationMaterialsOutcome(GenerationOutcomeStatus.COMPLETED, freshCompleted, null));
        stubSuccessfulRenderAndLoad(freshCompleted.id());

        useCase.prepare(VACANCY_ID);

        verify(generationRepositoryPort).save(argThatRequestsNewGeneration(6L, 2L));
        verify(generateApplicationMaterialsUseCase).generate(freshPending.id());
    }

    @Test
    void prepare_changedCareerHistoryVersion_doesNotReuseCompletedGeneration_createsNew() {
        stubVacancyFound();
        stubCurrentVersions(5L, 3L); // career history moved from 2 -> 3
        ApplicationMaterialGeneration staleCompleted = pendingGeneration(5L, 2L).start(NOW).complete(NOW);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of(staleCompleted));

        ApplicationMaterialGeneration freshPending = pendingGeneration(5L, 3L);
        when(generationRepositoryPort.save(any(ApplicationMaterialGeneration.class))).thenReturn(freshPending);
        ApplicationMaterialGeneration freshCompleted = freshPending.start(NOW).complete(NOW);
        when(generateApplicationMaterialsUseCase.generate(freshPending.id()))
                .thenReturn(new GenerateApplicationMaterialsOutcome(GenerationOutcomeStatus.COMPLETED, freshCompleted, null));
        stubSuccessfulRenderAndLoad(freshCompleted.id());

        useCase.prepare(VACANCY_ID);

        verify(generationRepositoryPort).save(argThatRequestsNewGeneration(5L, 3L));
    }

    // ==================== Matching IN_PROGRESS: no AI call, controlled outcome ====================

    @Test
    void prepare_matchingInProgressGeneration_returnsAlreadyInProgressWithoutAnyGenerationCall() {
        stubVacancyFound();
        stubCurrentVersions(5L, 2L);
        ApplicationMaterialGeneration inProgress = pendingGeneration(5L, 2L).start(NOW);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of(inProgress));

        PrepareApplicationPackageOutcome outcome = useCase.prepare(VACANCY_ID);

        assertThat(outcome).isEqualTo(new PrepareApplicationPackageOutcome.AlreadyInProgress(inProgress.id()));
        verify(generateApplicationMaterialsUseCase, never()).generate(any());
        verify(generationRepositoryPort, never()).save(any());
        verify(renderApplicationMaterialsUseCase, never()).render(any());
    }

    // ==================== Matching PENDING: continue it via generate() ====================

    @Test
    void prepare_matchingPendingGeneration_continuesItWithoutCreatingDuplicate() {
        stubVacancyFound();
        stubCurrentVersions(5L, 2L);
        ApplicationMaterialGeneration existingPending = pendingGeneration(5L, 2L);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of(existingPending));

        ApplicationMaterialGeneration completed = existingPending.start(NOW).complete(NOW);
        when(generateApplicationMaterialsUseCase.generate(existingPending.id()))
                .thenReturn(new GenerateApplicationMaterialsOutcome(GenerationOutcomeStatus.COMPLETED, completed, null));
        stubSuccessfulRenderAndLoad(completed.id());

        useCase.prepare(VACANCY_ID);

        verify(generationRepositoryPort, never()).save(any());
        verify(generateApplicationMaterialsUseCase).generate(existingPending.id());
    }

    @Test
    void prepare_generateReturnsAlreadyInProgress_returnsAlreadyInProgressOutcome() {
        stubVacancyFound();
        stubCurrentVersions(5L, 2L);
        ApplicationMaterialGeneration existingPending = pendingGeneration(5L, 2L);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of(existingPending));
        ApplicationMaterialGeneration inProgress = existingPending.start(NOW);
        when(generateApplicationMaterialsUseCase.generate(existingPending.id()))
                .thenReturn(new GenerateApplicationMaterialsOutcome(GenerationOutcomeStatus.ALREADY_IN_PROGRESS, inProgress, null));

        PrepareApplicationPackageOutcome outcome = useCase.prepare(VACANCY_ID);

        assertThat(outcome).isEqualTo(new PrepareApplicationPackageOutcome.AlreadyInProgress(inProgress.id()));
        verify(renderApplicationMaterialsUseCase, never()).render(any());
    }

    @Test
    void prepare_generateReturnsAlreadyCompleted_reusesAndMarksAsReused() {
        stubVacancyFound();
        stubCurrentVersions(5L, 2L);
        ApplicationMaterialGeneration existingPending = pendingGeneration(5L, 2L);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of(existingPending));
        ApplicationMaterialGeneration completedByConcurrentRequest = existingPending.start(NOW).complete(NOW);
        when(generateApplicationMaterialsUseCase.generate(existingPending.id()))
                .thenReturn(new GenerateApplicationMaterialsOutcome(
                        GenerationOutcomeStatus.ALREADY_COMPLETED, completedByConcurrentRequest, null));
        stubSuccessfulRenderAndLoad(completedByConcurrentRequest.id());

        PrepareApplicationPackageOutcome outcome = useCase.prepare(VACANCY_ID);

        assertPrepared(outcome, completedByConcurrentRequest.id(), true);
    }

    // ==================== Matching FAILED: new generation, old row untouched ====================

    @Test
    void prepare_matchingFailedGeneration_createsNewGenerationRatherThanRetryingOldRow() {
        stubVacancyFound();
        stubCurrentVersions(5L, 2L);
        ApplicationMaterialGeneration failed = pendingGeneration(5L, 2L).start(NOW)
                .fail("AI_PROVIDER_ERROR", "boom", NOW);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of(failed));

        ApplicationMaterialGeneration freshPending = pendingGeneration(5L, 2L);
        when(generationRepositoryPort.save(any(ApplicationMaterialGeneration.class))).thenReturn(freshPending);
        ApplicationMaterialGeneration freshCompleted = freshPending.start(NOW).complete(NOW);
        when(generateApplicationMaterialsUseCase.generate(freshPending.id()))
                .thenReturn(new GenerateApplicationMaterialsOutcome(GenerationOutcomeStatus.COMPLETED, freshCompleted, null));
        stubSuccessfulRenderAndLoad(freshCompleted.id());

        useCase.prepare(VACANCY_ID);

        verify(generationRepositoryPort).save(argThatRequestsNewGeneration(5L, 2L));
        verify(generateApplicationMaterialsUseCase).generate(freshPending.id());
        // The old FAILED row is never passed to generate() or saved again.
        verify(generateApplicationMaterialsUseCase, never()).generate(failed.id());
    }

    // ==================== Generation/render failures map to a safe, generic outcome ====================

    @Test
    void prepare_generationFails_returnsFailedOutcome() {
        stubVacancyFound();
        stubCurrentVersions(1L, null);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of());
        ApplicationMaterialGeneration pending = pendingGeneration(1L, null);
        when(generationRepositoryPort.save(any(ApplicationMaterialGeneration.class))).thenReturn(pending);
        ApplicationMaterialGeneration failed = pending.start(NOW).fail("AI_PROVIDER_ERROR", "boom", NOW);
        when(generateApplicationMaterialsUseCase.generate(pending.id()))
                .thenReturn(new GenerateApplicationMaterialsOutcome(GenerationOutcomeStatus.FAILED, failed, null));

        PrepareApplicationPackageOutcome outcome = useCase.prepare(VACANCY_ID);

        assertThat(outcome).isEqualTo(new PrepareApplicationPackageOutcome.Failed(failed.id()));
        verify(renderApplicationMaterialsUseCase, never()).render(any());
    }

    @Test
    void prepare_renderThrows_returnsFailedOutcomeWithoutLeakingException() {
        stubVacancyFound();
        stubCurrentVersions(1L, null);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of());
        ApplicationMaterialGeneration pending = pendingGeneration(1L, null);
        when(generationRepositoryPort.save(any(ApplicationMaterialGeneration.class))).thenReturn(pending);
        ApplicationMaterialGeneration completed = pending.start(NOW).complete(NOW);
        when(generateApplicationMaterialsUseCase.generate(pending.id()))
                .thenReturn(new GenerateApplicationMaterialsOutcome(GenerationOutcomeStatus.COMPLETED, completed, null));
        when(renderApplicationMaterialsUseCase.render(completed.id())).thenThrow(new RenderApplicationMaterialsException(
                RenderApplicationMaterialsException.Reason.RENDERING_FAILED, completed.id(), "boom"));

        PrepareApplicationPackageOutcome outcome = useCase.prepare(VACANCY_ID);

        assertThat(outcome).isEqualTo(new PrepareApplicationPackageOutcome.Failed(completed.id()));
    }

    @Test
    void prepare_fileStorageLoadThrows_returnsFailedOutcomeWithoutLeakingException() {
        stubVacancyFound();
        stubCurrentVersions(1L, null);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of());
        ApplicationMaterialGeneration pending = pendingGeneration(1L, null);
        when(generationRepositoryPort.save(any(ApplicationMaterialGeneration.class))).thenReturn(pending);
        ApplicationMaterialGeneration completed = pending.start(NOW).complete(NOW);
        when(generateApplicationMaterialsUseCase.generate(pending.id()))
                .thenReturn(new GenerateApplicationMaterialsOutcome(GenerationOutcomeStatus.COMPLETED, completed, null));
        when(renderApplicationMaterialsUseCase.render(completed.id())).thenReturn(
                new RenderApplicationMaterialsResult(artifact(completed.id(), ApplicationMaterialType.CV, "cv-key"),
                        artifact(completed.id(), ApplicationMaterialType.COVER_LETTER, "cl-key")));
        when(fileStoragePort.load("cv-key")).thenThrow(new FileStorageNotFoundException("cv-key"));

        PrepareApplicationPackageOutcome outcome = useCase.prepare(VACANCY_ID);

        assertThat(outcome).isEqualTo(new PrepareApplicationPackageOutcome.Failed(completed.id()));
    }

    // ==================== Repeated ("duplicate click") requests reuse, never re-call AI ====================

    @Test
    void prepare_repeatedRequestAfterCompletion_reusesAndNeverCallsGenerateAgain() {
        stubVacancyFound();
        stubCurrentVersions(5L, 2L);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of());
        ApplicationMaterialGeneration pending = pendingGeneration(5L, 2L);
        when(generationRepositoryPort.save(any(ApplicationMaterialGeneration.class))).thenReturn(pending);
        ApplicationMaterialGeneration completed = pending.start(NOW).complete(NOW);
        when(generateApplicationMaterialsUseCase.generate(pending.id()))
                .thenReturn(new GenerateApplicationMaterialsOutcome(GenerationOutcomeStatus.COMPLETED, completed, null));
        stubSuccessfulRenderAndLoad(completed.id());

        useCase.prepare(VACANCY_ID); // first click: creates + generates + renders

        // Second click ("accidental repeated Telegram click"): now finds the COMPLETED generation.
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of(completed));

        PrepareApplicationPackageOutcome secondOutcome = useCase.prepare(VACANCY_ID);

        assertPrepared(secondOutcome, completed.id(), true);
        verify(generateApplicationMaterialsUseCase, times(1)).generate(any());
        verify(generationRepositoryPort, times(1)).save(any());
    }

    // ==================== Lost the V25 active-uniqueness creation race ====================

    @Test
    void prepare_activeConflictOnCreate_joinsPendingWinnerWithoutInsertingASecondRow() {
        stubVacancyFound();
        stubCurrentVersions(5L, 2L);
        ApplicationMaterialGeneration winner = pendingGeneration(5L, 2L);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of(), List.of(winner));
        when(generationRepositoryPort.save(any(ApplicationMaterialGeneration.class)))
                .thenThrow(new ApplicationMaterialGenerationActiveConflictException(VACANCY_ID, 5L, 2L));
        ApplicationMaterialGeneration completed = winner.start(NOW).complete(NOW);
        when(generateApplicationMaterialsUseCase.generate(winner.id()))
                .thenReturn(new GenerateApplicationMaterialsOutcome(GenerationOutcomeStatus.COMPLETED, completed, null));
        stubSuccessfulRenderAndLoad(completed.id());

        PrepareApplicationPackageOutcome outcome = useCase.prepare(VACANCY_ID);

        verify(generationRepositoryPort, times(1)).save(any(ApplicationMaterialGeneration.class));
        verify(generateApplicationMaterialsUseCase).generate(winner.id());
        assertPrepared(outcome, completed.id(), false);
    }

    @Test
    void prepare_activeConflictOnCreate_winnerAlreadyInProgress_returnsAlreadyInProgressWithoutSecondInsert() {
        stubVacancyFound();
        stubCurrentVersions(5L, 2L);
        ApplicationMaterialGeneration winner = pendingGeneration(5L, 2L).start(NOW);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of(), List.of(winner));
        when(generationRepositoryPort.save(any(ApplicationMaterialGeneration.class)))
                .thenThrow(new ApplicationMaterialGenerationActiveConflictException(VACANCY_ID, 5L, 2L));

        PrepareApplicationPackageOutcome outcome = useCase.prepare(VACANCY_ID);

        assertThat(outcome).isEqualTo(new PrepareApplicationPackageOutcome.AlreadyInProgress(winner.id()));
        verify(generationRepositoryPort, times(1)).save(any(ApplicationMaterialGeneration.class));
        verify(generateApplicationMaterialsUseCase, never()).generate(any());
    }

    @Test
    void prepare_activeConflictOnCreate_winnerAlreadyCompleted_reusesWithoutSecondInsertOrAiCall() {
        stubVacancyFound();
        stubCurrentVersions(5L, 2L);
        ApplicationMaterialGeneration winner = pendingGeneration(5L, 2L).start(NOW).complete(NOW);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of(), List.of(winner));
        when(generationRepositoryPort.save(any(ApplicationMaterialGeneration.class)))
                .thenThrow(new ApplicationMaterialGenerationActiveConflictException(VACANCY_ID, 5L, 2L));
        stubSuccessfulRenderAndLoad(winner.id());

        PrepareApplicationPackageOutcome outcome = useCase.prepare(VACANCY_ID);

        assertPrepared(outcome, winner.id(), true);
        verify(generationRepositoryPort, times(1)).save(any(ApplicationMaterialGeneration.class));
        verify(generateApplicationMaterialsUseCase, never()).generate(any());
    }

    @Test
    void prepare_activeConflictOnCreate_winnerAlreadyFailed_retriesInsertRatherThanJoiningIt() {
        stubVacancyFound();
        stubCurrentVersions(5L, 2L);
        ApplicationMaterialGeneration failedWinner = pendingGeneration(5L, 2L).start(NOW).fail("AI_PROVIDER_ERROR", "boom", NOW);
        // First lookup: nothing. After losing the race: the winner, but it already failed. The
        // subsequent successful retry insert is never itself blocked (V25 excludes FAILED rows).
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of(), List.of(failedWinner));
        ApplicationMaterialGeneration freshPending = pendingGeneration(5L, 2L);
        when(generationRepositoryPort.save(any(ApplicationMaterialGeneration.class)))
                .thenThrow(new ApplicationMaterialGenerationActiveConflictException(VACANCY_ID, 5L, 2L))
                .thenReturn(freshPending);
        ApplicationMaterialGeneration completed = freshPending.start(NOW).complete(NOW);
        when(generateApplicationMaterialsUseCase.generate(freshPending.id()))
                .thenReturn(new GenerateApplicationMaterialsOutcome(GenerationOutcomeStatus.COMPLETED, completed, null));
        stubSuccessfulRenderAndLoad(completed.id());

        PrepareApplicationPackageOutcome outcome = useCase.prepare(VACANCY_ID);

        verify(generationRepositoryPort, times(2)).save(any(ApplicationMaterialGeneration.class));
        verify(generateApplicationMaterialsUseCase).generate(freshPending.id());
        assertPrepared(outcome, completed.id(), false);
    }

    // ==================== Helpers ====================

    private void stubVacancyFound() {
        when(vacancyQueryService.getById(VACANCY_ID)).thenReturn(vacancy);
    }

    private void stubCurrentVersions(long candidateProfileVersion, Long careerHistoryVersion) {
        CandidateContextSnapshot snapshot = new CandidateContextSnapshot(
                UUID.randomUUID(), "primary", candidateProfileVersion, validProfile(),
                careerHistoryVersion == null ? Optional.empty() : Optional.of(careerHistory(careerHistoryVersion)));
        when(candidateContextProvider.loadCurrentContext()).thenReturn(snapshot);
    }

    private void stubSuccessfulRenderAndLoad(UUID generationId) {
        ApplicationMaterialArtifact cv = artifact(generationId, ApplicationMaterialType.CV, "cv-key");
        ApplicationMaterialArtifact coverLetter = artifact(generationId, ApplicationMaterialType.COVER_LETTER, "cover-letter-key");
        when(renderApplicationMaterialsUseCase.render(generationId)).thenReturn(new RenderApplicationMaterialsResult(cv, coverLetter));
        when(fileStoragePort.load("cv-key")).thenReturn(new StoredFileContent("cv-key", "cv-bytes".getBytes(), 8, "a".repeat(64)));
        when(fileStoragePort.load("cover-letter-key")).thenReturn(
                new StoredFileContent("cover-letter-key", "cl-bytes".getBytes(), 8, "b".repeat(64)));
    }

    private ApplicationMaterialArtifact artifact(UUID generationId, ApplicationMaterialType type, String storageKey) {
        String fileName = type == ApplicationMaterialType.CV ? "CV.pdf" : "Cover_Letter.pdf";
        return new ApplicationMaterialArtifact(UUID.randomUUID(), generationId, type, ApplicationMaterialFormat.PDF, 1,
                storageKey, fileName, "application/pdf", 8, "c".repeat(64), NOW);
    }

    private ApplicationMaterialGeneration pendingGeneration(long candidateProfileVersion, Long careerHistoryVersion) {
        ApplicationMaterialGeneration requested =
                ApplicationMaterialGeneration.requestNew(VACANCY_ID, candidateProfileVersion, careerHistoryVersion, NOW);
        // Simulate persistence assigning an id, matching what generationRepositoryPort.save would do.
        return new ApplicationMaterialGeneration(UUID.randomUUID(), requested.vacancyId(), requested.status(),
                requested.candidateProfileVersion(), requested.careerHistoryVersion(), requested.requestedAt(),
                null, null, null, null, 0);
    }

    private ApplicationMaterialGeneration argThatRequestsNewGeneration(long candidateProfileVersion, Long careerHistoryVersion) {
        return argThat(g -> g.id() == null
                && g.vacancyId().equals(VACANCY_ID)
                && g.candidateProfileVersion() == candidateProfileVersion
                && Objects.equals(g.careerHistoryVersion(), careerHistoryVersion));
    }

    private void verifyNoGenerationWork() {
        verify(generationRepositoryPort, never()).findByVacancyId(any());
        verify(generationRepositoryPort, never()).save(any());
        verify(generateApplicationMaterialsUseCase, never()).generate(any());
        verify(renderApplicationMaterialsUseCase, never()).render(any());
    }

    private PreparedApplicationPackage assertPrepared(PrepareApplicationPackageOutcome outcome, UUID generationId, boolean reused) {
        assertThat(outcome).isInstanceOf(PrepareApplicationPackageOutcome.Prepared.class);
        PreparedApplicationPackage prepared = ((PrepareApplicationPackageOutcome.Prepared) outcome).preparedPackage();
        assertThat(prepared.generationId()).isEqualTo(generationId);
        assertThat(prepared.reusedExistingGeneration()).isEqualTo(reused);
        return prepared;
    }

    private CandidateProfile validProfile() {
        return new CandidateProfile("Senior Java Backend Engineer", "Senior", List.of(), List.of(), 5,
                new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null));
    }

    private CareerHistoryAggregate careerHistory(long version) {
        return new CareerHistoryAggregate(UUID.randomUUID(), UUID.randomUUID(), List.of(), version);
    }
}
