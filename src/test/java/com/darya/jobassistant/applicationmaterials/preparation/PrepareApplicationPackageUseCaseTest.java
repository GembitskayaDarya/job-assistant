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
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationConcurrentModificationException;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationRepositoryPort;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationStatus;
import com.darya.jobassistant.applicationmaterials.artifact.aggregate.ApplicationMaterialArtifact;
import com.darya.jobassistant.applicationmaterials.config.ApplicationMaterialGenerationProperties;
import com.darya.jobassistant.applicationmaterials.generation.GenerateApplicationMaterialsOutcome;
import com.darya.jobassistant.applicationmaterials.generation.GenerateApplicationMaterialsUseCase;
import com.darya.jobassistant.applicationmaterials.generation.GenerationOutcomeStatus;
import com.darya.jobassistant.applicationmaterials.generation.model.ApplicationMaterialsGenerationFailureCode;
import com.darya.jobassistant.applicationmaterials.render.RenderApplicationMaterialsException;
import com.darya.jobassistant.applicationmaterials.render.RenderApplicationMaterialsResult;
import com.darya.jobassistant.applicationmaterials.render.RenderApplicationMaterialsUseCase;
import com.darya.jobassistant.applicationmaterials.render.model.ApplicationMaterialFormat;
import com.darya.jobassistant.applicationmaterials.render.model.ApplicationMaterialType;
import com.darya.jobassistant.candidatecontext.CandidateContextProvider;
import com.darya.jobassistant.candidatecontext.CandidateContextSnapshot;
import com.darya.jobassistant.candidates.CandidateEducationFacts;
import com.darya.jobassistant.candidates.CandidateLanguageFacts;
import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfileFacts;
import com.darya.jobassistant.candidates.CandidateSkillFacts;
import com.darya.jobassistant.candidates.SkillProficiency;
import com.darya.jobassistant.careerhistory.aggregate.CareerCompany;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import com.darya.jobassistant.exception.VacancyNotFoundException;
import com.darya.jobassistant.integrations.filestorage.FileStorageNotFoundException;
import com.darya.jobassistant.integrations.filestorage.FileStoragePort;
import com.darya.jobassistant.integrations.filestorage.StoredFileContent;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProject;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProjectHighlight;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProjectTechnology;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrepareApplicationPackageUseCaseTest {

    private static final UUID VACANCY_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Duration STALE_TIMEOUT = Duration.ofMinutes(15);
    private static final JobOffer JOB_OFFER = new JobOffer("job-1", "Backend Engineer", "Acme Corp", "Remote", null,
            "We need a backend engineer.", "https://example.com/job-1", "test");
    /** A syntactically plausible but arbitrary "stale"/legacy fingerprint - never equal to any real computed one. */
    private static final String STALE_FINGERPRINT = "a".repeat(64);

    @Mock
    private VacancyQueryService vacancyQueryService;

    @Mock
    private VacancyJobOfferMapper vacancyJobOfferMapper;

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
                vacancyQueryService, vacancyJobOfferMapper, candidateContextProvider, generationRepositoryPort,
                generateApplicationMaterialsUseCase, renderApplicationMaterialsUseCase, fileStoragePort,
                new ApplicationMaterialGenerationProperties(STALE_TIMEOUT), CLOCK);
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
    void prepare_noExistingGeneration_createsNewGenerationWithCurrentSourceFingerprint() {
        stubVacancyFound();
        String fingerprint = stubCurrentVersions(5L, 2L);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of());

        ApplicationMaterialGeneration pending = pendingGeneration(5L, 2L, fingerprint);
        when(generationRepositoryPort.save(any(ApplicationMaterialGeneration.class))).thenReturn(pending);

        ApplicationMaterialGeneration completed = pending.start(NOW).complete(NOW);
        when(generateApplicationMaterialsUseCase.generate(pending.id()))
                .thenReturn(new GenerateApplicationMaterialsOutcome(GenerationOutcomeStatus.COMPLETED, completed, null));
        stubSuccessfulRenderAndLoad(completed.id());

        PrepareApplicationPackageOutcome outcome = useCase.prepare(VACANCY_ID);

        verify(generationRepositoryPort).save(argThatRequestsNewGeneration(5L, 2L, fingerprint));
        verify(generateApplicationMaterialsUseCase).generate(pending.id());
        assertPrepared(outcome, completed.id(), false);
    }

    @Test
    void prepare_noExistingGeneration_optionalCareerHistoryAbsent_createsGenerationWithNullCareerHistoryVersion() {
        stubVacancyFound();
        String fingerprint = stubCurrentVersions(1L, null);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of());

        ApplicationMaterialGeneration pending = pendingGeneration(1L, null, fingerprint);
        when(generationRepositoryPort.save(any(ApplicationMaterialGeneration.class))).thenReturn(pending);
        ApplicationMaterialGeneration completed = pending.start(NOW).complete(NOW);
        when(generateApplicationMaterialsUseCase.generate(pending.id()))
                .thenReturn(new GenerateApplicationMaterialsOutcome(GenerationOutcomeStatus.COMPLETED, completed, null));
        stubSuccessfulRenderAndLoad(completed.id());

        useCase.prepare(VACANCY_ID);

        verify(generationRepositoryPort).save(argThatRequestsNewGeneration(1L, null, fingerprint));
    }

    @Test
    void prepare_newGeneration_rendersAndLoadsPdfsThroughFileStoragePort() {
        stubVacancyFound();
        String fingerprint = stubCurrentVersions(1L, null);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of());
        ApplicationMaterialGeneration pending = pendingGeneration(1L, null, fingerprint);
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

    // ==================== Matching COMPLETED (same fingerprint): reuse, no AI call ====================

    @Test
    void prepare_matchingCompletedGeneration_reusesWithoutCallingGenerate() {
        stubVacancyFound();
        String fingerprint = stubCurrentVersions(5L, 2L);
        ApplicationMaterialGeneration completed = pendingGeneration(5L, 2L, fingerprint).start(NOW).complete(NOW);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of(completed));
        stubSuccessfulRenderAndLoad(completed.id());

        PrepareApplicationPackageOutcome outcome = useCase.prepare(VACANCY_ID);

        verify(generateApplicationMaterialsUseCase, never()).generate(any());
        verify(generationRepositoryPort, never()).save(any());
        verify(renderApplicationMaterialsUseCase).render(completed.id());
        assertPrepared(outcome, completed.id(), true);
    }

    // ==================== Different fingerprint (source content changed): never reused ====================

    @Test
    void prepare_changedCandidateProfileContent_doesNotReuseCompletedGeneration_createsNew() {
        stubVacancyFound();
        // The stale generation was requested against a fingerprint from before the profile changed.
        ApplicationMaterialGeneration staleCompleted = pendingGeneration(5L, 2L, STALE_FINGERPRINT).start(NOW).complete(NOW);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of(staleCompleted));
        String currentFingerprint = stubCurrentVersions(5L, 2L);

        ApplicationMaterialGeneration freshPending = pendingGeneration(5L, 2L, currentFingerprint);
        when(generationRepositoryPort.save(any(ApplicationMaterialGeneration.class))).thenReturn(freshPending);
        ApplicationMaterialGeneration freshCompleted = freshPending.start(NOW).complete(NOW);
        when(generateApplicationMaterialsUseCase.generate(freshPending.id()))
                .thenReturn(new GenerateApplicationMaterialsOutcome(GenerationOutcomeStatus.COMPLETED, freshCompleted, null));
        stubSuccessfulRenderAndLoad(freshCompleted.id());

        useCase.prepare(VACANCY_ID);

        verify(generationRepositoryPort).save(argThatRequestsNewGeneration(5L, 2L, currentFingerprint));
        verify(generateApplicationMaterialsUseCase).generate(freshPending.id());
    }

    @Test
    void prepare_personalProjectOnlyChange_doesNotReuseCompletedGeneration_createsNew() {
        stubVacancyFound();
        ApplicationMaterialGeneration staleCompleted = pendingGeneration(5L, 2L, STALE_FINGERPRINT).start(NOW).complete(NOW);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of(staleCompleted));
        // Everything else held constant; only the Personal Project list differs from what the stale
        // generation's (unknown to this test, but by construction different) fingerprint reflects -
        // this is exactly the real production gap: neither candidateProfileVersion nor
        // careerHistoryVersion changes for a Personal-Project-only edit.
        CandidateContextSnapshot snapshot = snapshotWithPersonalProject(5L, 2L, "AI Job Search Assistant");
        when(candidateContextProvider.loadCurrentContext()).thenReturn(snapshot);
        String currentFingerprint = ApplicationMaterialSourceFingerprint.sha256(snapshot, JOB_OFFER);

        ApplicationMaterialGeneration freshPending = pendingGeneration(5L, 2L, currentFingerprint);
        when(generationRepositoryPort.save(any(ApplicationMaterialGeneration.class))).thenReturn(freshPending);
        ApplicationMaterialGeneration freshCompleted = freshPending.start(NOW).complete(NOW);
        when(generateApplicationMaterialsUseCase.generate(freshPending.id()))
                .thenReturn(new GenerateApplicationMaterialsOutcome(GenerationOutcomeStatus.COMPLETED, freshCompleted, null));
        stubSuccessfulRenderAndLoad(freshCompleted.id());

        useCase.prepare(VACANCY_ID);

        verify(generationRepositoryPort).save(argThatRequestsNewGeneration(5L, 2L, currentFingerprint));
        verify(generateApplicationMaterialsUseCase).generate(freshPending.id());
    }

    @Test
    void prepare_educationOnlyChange_doesNotReuseCompletedGeneration_createsNew() {
        stubVacancyFound();
        ApplicationMaterialGeneration staleCompleted = pendingGeneration(5L, 2L, STALE_FINGERPRINT).start(NOW).complete(NOW);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of(staleCompleted));
        CandidateContextSnapshot snapshot = snapshotWithEducation(5L, 2L, "State University");
        when(candidateContextProvider.loadCurrentContext()).thenReturn(snapshot);
        String currentFingerprint = ApplicationMaterialSourceFingerprint.sha256(snapshot, JOB_OFFER);

        ApplicationMaterialGeneration freshPending = pendingGeneration(5L, 2L, currentFingerprint);
        when(generationRepositoryPort.save(any(ApplicationMaterialGeneration.class))).thenReturn(freshPending);
        ApplicationMaterialGeneration freshCompleted = freshPending.start(NOW).complete(NOW);
        when(generateApplicationMaterialsUseCase.generate(freshPending.id()))
                .thenReturn(new GenerateApplicationMaterialsOutcome(GenerationOutcomeStatus.COMPLETED, freshCompleted, null));
        stubSuccessfulRenderAndLoad(freshCompleted.id());

        useCase.prepare(VACANCY_ID);

        verify(generateApplicationMaterialsUseCase).generate(freshPending.id());
    }

    @Test
    void prepare_languageOnlyChange_doesNotReuseCompletedGeneration_createsNew() {
        stubVacancyFound();
        ApplicationMaterialGeneration staleCompleted = pendingGeneration(5L, 2L, STALE_FINGERPRINT).start(NOW).complete(NOW);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of(staleCompleted));
        CandidateContextSnapshot snapshot = snapshotWithLanguage(5L, 2L, "German");
        when(candidateContextProvider.loadCurrentContext()).thenReturn(snapshot);
        String currentFingerprint = ApplicationMaterialSourceFingerprint.sha256(snapshot, JOB_OFFER);

        ApplicationMaterialGeneration freshPending = pendingGeneration(5L, 2L, currentFingerprint);
        when(generationRepositoryPort.save(any(ApplicationMaterialGeneration.class))).thenReturn(freshPending);
        ApplicationMaterialGeneration freshCompleted = freshPending.start(NOW).complete(NOW);
        when(generateApplicationMaterialsUseCase.generate(freshPending.id()))
                .thenReturn(new GenerateApplicationMaterialsOutcome(GenerationOutcomeStatus.COMPLETED, freshCompleted, null));
        stubSuccessfulRenderAndLoad(freshCompleted.id());

        useCase.prepare(VACANCY_ID);

        verify(generateApplicationMaterialsUseCase).generate(freshPending.id());
    }

    @Test
    void prepare_careerHistoryOnlyChange_doesNotReuseCompletedGeneration_createsNew() {
        stubVacancyFound();
        ApplicationMaterialGeneration staleCompleted = pendingGeneration(5L, 2L, STALE_FINGERPRINT).start(NOW).complete(NOW);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of(staleCompleted));
        // Same careerHistoryVersion (2) as the stale generation's metadata, but different company
        // content - exactly the "version fields alone can't detect this" scenario.
        CandidateContextSnapshot snapshot = new CandidateContextSnapshot(
                UUID.randomUUID(), "primary", 5L, validProfile(), Optional.of(careerHistoryWithCompany(2L, "New Company Inc")));
        when(candidateContextProvider.loadCurrentContext()).thenReturn(snapshot);
        String currentFingerprint = ApplicationMaterialSourceFingerprint.sha256(snapshot, JOB_OFFER);

        ApplicationMaterialGeneration freshPending = pendingGeneration(5L, 2L, currentFingerprint);
        when(generationRepositoryPort.save(any(ApplicationMaterialGeneration.class))).thenReturn(freshPending);
        ApplicationMaterialGeneration freshCompleted = freshPending.start(NOW).complete(NOW);
        when(generateApplicationMaterialsUseCase.generate(freshPending.id()))
                .thenReturn(new GenerateApplicationMaterialsOutcome(GenerationOutcomeStatus.COMPLETED, freshCompleted, null));
        stubSuccessfulRenderAndLoad(freshCompleted.id());

        useCase.prepare(VACANCY_ID);

        verify(generateApplicationMaterialsUseCase).generate(freshPending.id());
    }

    @Test
    void prepare_candidateSkillOnlyChange_doesNotReuseCompletedGeneration_createsNew() {
        stubVacancyFound();
        ApplicationMaterialGeneration staleCompleted = pendingGeneration(5L, 2L, STALE_FINGERPRINT).start(NOW).complete(NOW);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of(staleCompleted));
        CandidateContextSnapshot snapshot = snapshotWithSkill(5L, 2L, "Kubernetes");
        when(candidateContextProvider.loadCurrentContext()).thenReturn(snapshot);
        String currentFingerprint = ApplicationMaterialSourceFingerprint.sha256(snapshot, JOB_OFFER);

        ApplicationMaterialGeneration freshPending = pendingGeneration(5L, 2L, currentFingerprint);
        when(generationRepositoryPort.save(any(ApplicationMaterialGeneration.class))).thenReturn(freshPending);
        ApplicationMaterialGeneration freshCompleted = freshPending.start(NOW).complete(NOW);
        when(generateApplicationMaterialsUseCase.generate(freshPending.id()))
                .thenReturn(new GenerateApplicationMaterialsOutcome(GenerationOutcomeStatus.COMPLETED, freshCompleted, null));
        stubSuccessfulRenderAndLoad(freshCompleted.id());

        useCase.prepare(VACANCY_ID);

        verify(generateApplicationMaterialsUseCase).generate(freshPending.id());
    }

    @Test
    void prepare_vacancyContentChange_doesNotReuseCompletedGeneration_createsNew() {
        when(vacancyQueryService.getById(VACANCY_ID)).thenReturn(vacancy);
        JobOffer changedVacancy = new JobOffer("job-1", "Staff Backend Engineer", "Acme Corp", "Remote", null,
                "We need a backend engineer.", "https://example.com/job-1", "test");
        when(vacancyJobOfferMapper.toJobOffer(vacancy)).thenReturn(changedVacancy);
        ApplicationMaterialGeneration staleCompleted = pendingGeneration(5L, 2L, STALE_FINGERPRINT).start(NOW).complete(NOW);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of(staleCompleted));
        CandidateContextSnapshot snapshot = new CandidateContextSnapshot(
                UUID.randomUUID(), "primary", 5L, validProfile(), Optional.of(careerHistory(2L)));
        when(candidateContextProvider.loadCurrentContext()).thenReturn(snapshot);
        String currentFingerprint = ApplicationMaterialSourceFingerprint.sha256(snapshot, changedVacancy);

        ApplicationMaterialGeneration freshPending = pendingGeneration(5L, 2L, currentFingerprint);
        when(generationRepositoryPort.save(any(ApplicationMaterialGeneration.class))).thenReturn(freshPending);
        ApplicationMaterialGeneration freshCompleted = freshPending.start(NOW).complete(NOW);
        when(generateApplicationMaterialsUseCase.generate(freshPending.id()))
                .thenReturn(new GenerateApplicationMaterialsOutcome(GenerationOutcomeStatus.COMPLETED, freshCompleted, null));
        stubSuccessfulRenderAndLoad(freshCompleted.id());

        useCase.prepare(VACANCY_ID);

        verify(generateApplicationMaterialsUseCase).generate(freshPending.id());
    }

    @Test
    void prepare_legacyGenerationWithNullFingerprint_isNeverReused_createsNew() {
        stubVacancyFound();
        String currentFingerprint = stubCurrentVersions(5L, 2L);
        // A legacy generation predating this feature - candidateProfileVersion/careerHistoryVersion
        // happen to equal the current ones, but sourceFingerprint is null.
        ApplicationMaterialGeneration legacyCompleted =
                new ApplicationMaterialGeneration(UUID.randomUUID(), VACANCY_ID, ApplicationMaterialGenerationStatus.PENDING,
                        5L, 2L, NOW, null, null, null, null, 0).start(NOW).complete(NOW);
        assertThat(legacyCompleted.sourceFingerprint()).isNull();
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of(legacyCompleted));

        ApplicationMaterialGeneration freshPending = pendingGeneration(5L, 2L, currentFingerprint);
        when(generationRepositoryPort.save(any(ApplicationMaterialGeneration.class))).thenReturn(freshPending);
        ApplicationMaterialGeneration freshCompleted = freshPending.start(NOW).complete(NOW);
        when(generateApplicationMaterialsUseCase.generate(freshPending.id()))
                .thenReturn(new GenerateApplicationMaterialsOutcome(GenerationOutcomeStatus.COMPLETED, freshCompleted, null));
        stubSuccessfulRenderAndLoad(freshCompleted.id());

        useCase.prepare(VACANCY_ID);

        verify(generationRepositoryPort).save(argThatRequestsNewGeneration(5L, 2L, currentFingerprint));
        verify(generateApplicationMaterialsUseCase).generate(freshPending.id());
    }

    // ==================== Matching IN_PROGRESS: no AI call, controlled outcome ====================

    @Test
    void prepare_matchingInProgressGeneration_returnsAlreadyInProgressWithoutAnyGenerationCall() {
        stubVacancyFound();
        String fingerprint = stubCurrentVersions(5L, 2L);
        ApplicationMaterialGeneration inProgress = pendingGeneration(5L, 2L, fingerprint).start(NOW);
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
        String fingerprint = stubCurrentVersions(5L, 2L);
        ApplicationMaterialGeneration existingPending = pendingGeneration(5L, 2L, fingerprint);
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
        String fingerprint = stubCurrentVersions(5L, 2L);
        ApplicationMaterialGeneration existingPending = pendingGeneration(5L, 2L, fingerprint);
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
        String fingerprint = stubCurrentVersions(5L, 2L);
        ApplicationMaterialGeneration existingPending = pendingGeneration(5L, 2L, fingerprint);
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
        String fingerprint = stubCurrentVersions(5L, 2L);
        ApplicationMaterialGeneration failed = pendingGeneration(5L, 2L, fingerprint).start(NOW)
                .fail("AI_PROVIDER_ERROR", "boom", NOW);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of(failed));

        ApplicationMaterialGeneration freshPending = pendingGeneration(5L, 2L, fingerprint);
        when(generationRepositoryPort.save(any(ApplicationMaterialGeneration.class))).thenReturn(freshPending);
        ApplicationMaterialGeneration freshCompleted = freshPending.start(NOW).complete(NOW);
        when(generateApplicationMaterialsUseCase.generate(freshPending.id()))
                .thenReturn(new GenerateApplicationMaterialsOutcome(GenerationOutcomeStatus.COMPLETED, freshCompleted, null));
        stubSuccessfulRenderAndLoad(freshCompleted.id());

        useCase.prepare(VACANCY_ID);

        verify(generationRepositoryPort).save(argThatRequestsNewGeneration(5L, 2L, fingerprint));
        verify(generateApplicationMaterialsUseCase).generate(freshPending.id());
        // The old FAILED row is never passed to generate() or saved again.
        verify(generateApplicationMaterialsUseCase, never()).generate(failed.id());
    }

    // ==================== Generation/render failures map to a safe, generic outcome ====================

    @Test
    void prepare_generationFails_returnsFailedOutcome() {
        stubVacancyFound();
        String fingerprint = stubCurrentVersions(1L, null);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of());
        ApplicationMaterialGeneration pending = pendingGeneration(1L, null, fingerprint);
        when(generationRepositoryPort.save(any(ApplicationMaterialGeneration.class))).thenReturn(pending);
        ApplicationMaterialGeneration failed = pending.start(NOW).fail("AI_PROVIDER_ERROR", "boom", NOW);
        when(generateApplicationMaterialsUseCase.generate(pending.id()))
                .thenReturn(new GenerateApplicationMaterialsOutcome(GenerationOutcomeStatus.FAILED, failed, null));

        PrepareApplicationPackageOutcome outcome = useCase.prepare(VACANCY_ID);

        assertThat(outcome).isEqualTo(new PrepareApplicationPackageOutcome.Failed(failed.id(), ApplicationPackageFailureReason.AI_PROVIDER_ERROR));
        verify(renderApplicationMaterialsUseCase, never()).render(any());
    }

    @Test
    void prepare_renderThrows_returnsFailedOutcomeWithoutLeakingException() {
        stubVacancyFound();
        String fingerprint = stubCurrentVersions(1L, null);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of());
        ApplicationMaterialGeneration pending = pendingGeneration(1L, null, fingerprint);
        when(generationRepositoryPort.save(any(ApplicationMaterialGeneration.class))).thenReturn(pending);
        ApplicationMaterialGeneration completed = pending.start(NOW).complete(NOW);
        when(generateApplicationMaterialsUseCase.generate(pending.id()))
                .thenReturn(new GenerateApplicationMaterialsOutcome(GenerationOutcomeStatus.COMPLETED, completed, null));
        when(renderApplicationMaterialsUseCase.render(completed.id())).thenThrow(new RenderApplicationMaterialsException(
                RenderApplicationMaterialsException.Reason.RENDERING_FAILED, completed.id(), "boom"));

        PrepareApplicationPackageOutcome outcome = useCase.prepare(VACANCY_ID);

        assertThat(outcome).isEqualTo(new PrepareApplicationPackageOutcome.Failed(completed.id(), ApplicationPackageFailureReason.RENDERING_FAILED));
    }

    @Test
    void prepare_renderThrowsAtsVerificationFailed_returnsFailedOutcomeWithAtsVerificationFailedReason() {
        // Sprint 11 Big Block 7 (Part 15): the ATS delivery gate (RenderApplicationMaterialsUseCase
        // throwing Reason.ATS_VERIFICATION_FAILED) must surface all the way through prepare() as its
        // own distinct ApplicationPackageFailureReason - never collapsed into the generic RENDERING_FAILED.
        stubVacancyFound();
        String fingerprint = stubCurrentVersions(1L, null);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of());
        ApplicationMaterialGeneration pending = pendingGeneration(1L, null, fingerprint);
        when(generationRepositoryPort.save(any(ApplicationMaterialGeneration.class))).thenReturn(pending);
        ApplicationMaterialGeneration completed = pending.start(NOW).complete(NOW);
        when(generateApplicationMaterialsUseCase.generate(pending.id()))
                .thenReturn(new GenerateApplicationMaterialsOutcome(GenerationOutcomeStatus.COMPLETED, completed, null));
        when(renderApplicationMaterialsUseCase.render(completed.id())).thenThrow(new RenderApplicationMaterialsException(
                RenderApplicationMaterialsException.Reason.ATS_VERIFICATION_FAILED, completed.id(), "missing section heading"));

        PrepareApplicationPackageOutcome outcome = useCase.prepare(VACANCY_ID);

        assertThat(outcome).isEqualTo(
                new PrepareApplicationPackageOutcome.Failed(completed.id(), ApplicationPackageFailureReason.ATS_VERIFICATION_FAILED));
    }

    @Test
    void prepare_fileStorageLoadThrows_returnsFailedOutcomeWithoutLeakingException() {
        stubVacancyFound();
        String fingerprint = stubCurrentVersions(1L, null);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of());
        ApplicationMaterialGeneration pending = pendingGeneration(1L, null, fingerprint);
        when(generationRepositoryPort.save(any(ApplicationMaterialGeneration.class))).thenReturn(pending);
        ApplicationMaterialGeneration completed = pending.start(NOW).complete(NOW);
        when(generateApplicationMaterialsUseCase.generate(pending.id()))
                .thenReturn(new GenerateApplicationMaterialsOutcome(GenerationOutcomeStatus.COMPLETED, completed, null));
        when(renderApplicationMaterialsUseCase.render(completed.id())).thenReturn(
                new RenderApplicationMaterialsResult(artifact(completed.id(), ApplicationMaterialType.CV, "cv-key"),
                        artifact(completed.id(), ApplicationMaterialType.COVER_LETTER, "cl-key")));
        when(fileStoragePort.load("cv-key")).thenThrow(new FileStorageNotFoundException("cv-key"));

        PrepareApplicationPackageOutcome outcome = useCase.prepare(VACANCY_ID);

        assertThat(outcome).isEqualTo(new PrepareApplicationPackageOutcome.Failed(completed.id(), ApplicationPackageFailureReason.DOCUMENT_DELIVERY_FAILED));
    }

    // ==================== Repeated ("duplicate click") requests reuse, never re-call AI ====================

    @Test
    void prepare_repeatedRequestAfterCompletion_reusesAndNeverCallsGenerateAgain() {
        stubVacancyFound();
        String fingerprint = stubCurrentVersions(5L, 2L);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of());
        ApplicationMaterialGeneration pending = pendingGeneration(5L, 2L, fingerprint);
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

    // ==================== Lost the active-uniqueness creation race ====================

    @Test
    void prepare_activeConflictOnCreate_joinsPendingWinnerWithoutInsertingASecondRow() {
        stubVacancyFound();
        String fingerprint = stubCurrentVersions(5L, 2L);
        ApplicationMaterialGeneration winner = pendingGeneration(5L, 2L, fingerprint);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of(), List.of(winner));
        when(generationRepositoryPort.save(any(ApplicationMaterialGeneration.class)))
                .thenThrow(new ApplicationMaterialGenerationActiveConflictException(VACANCY_ID, fingerprint));
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
        String fingerprint = stubCurrentVersions(5L, 2L);
        ApplicationMaterialGeneration winner = pendingGeneration(5L, 2L, fingerprint).start(NOW);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of(), List.of(winner));
        when(generationRepositoryPort.save(any(ApplicationMaterialGeneration.class)))
                .thenThrow(new ApplicationMaterialGenerationActiveConflictException(VACANCY_ID, fingerprint));

        PrepareApplicationPackageOutcome outcome = useCase.prepare(VACANCY_ID);

        assertThat(outcome).isEqualTo(new PrepareApplicationPackageOutcome.AlreadyInProgress(winner.id()));
        verify(generationRepositoryPort, times(1)).save(any(ApplicationMaterialGeneration.class));
        verify(generateApplicationMaterialsUseCase, never()).generate(any());
    }

    @Test
    void prepare_activeConflictOnCreate_winnerAlreadyCompleted_reusesWithoutSecondInsertOrAiCall() {
        stubVacancyFound();
        String fingerprint = stubCurrentVersions(5L, 2L);
        ApplicationMaterialGeneration winner = pendingGeneration(5L, 2L, fingerprint).start(NOW).complete(NOW);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of(), List.of(winner));
        when(generationRepositoryPort.save(any(ApplicationMaterialGeneration.class)))
                .thenThrow(new ApplicationMaterialGenerationActiveConflictException(VACANCY_ID, fingerprint));
        stubSuccessfulRenderAndLoad(winner.id());

        PrepareApplicationPackageOutcome outcome = useCase.prepare(VACANCY_ID);

        assertPrepared(outcome, winner.id(), true);
        verify(generationRepositoryPort, times(1)).save(any(ApplicationMaterialGeneration.class));
        verify(generateApplicationMaterialsUseCase, never()).generate(any());
    }

    @Test
    void prepare_activeConflictOnCreate_winnerAlreadyFailed_retriesInsertRatherThanJoiningIt() {
        stubVacancyFound();
        String fingerprint = stubCurrentVersions(5L, 2L);
        ApplicationMaterialGeneration failedWinner = pendingGeneration(5L, 2L, fingerprint).start(NOW).fail("AI_PROVIDER_ERROR", "boom", NOW);
        // First lookup: nothing. After losing the race: the winner, but it already failed. The
        // subsequent successful retry insert is never itself blocked (the active index excludes FAILED rows).
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of(), List.of(failedWinner));
        ApplicationMaterialGeneration freshPending = pendingGeneration(5L, 2L, fingerprint);
        when(generationRepositoryPort.save(any(ApplicationMaterialGeneration.class)))
                .thenThrow(new ApplicationMaterialGenerationActiveConflictException(VACANCY_ID, fingerprint))
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

    // ==================== Stale IN_PROGRESS recovery (Sprint 10 Step 6) ====================

    @Test
    void prepare_matchingStaleInProgressGeneration_recoversWithSafeFailureCodeAndCreatesNewGeneration() {
        stubVacancyFound();
        String fingerprint = stubCurrentVersions(5L, 2L);
        ApplicationMaterialGeneration stale = staleInProgressGeneration(5L, 2L, fingerprint);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of(stale));
        ApplicationMaterialGeneration failedStale =
                stale.fail(ApplicationMaterialsGenerationFailureCode.STALE_IN_PROGRESS.name(), "recovered", NOW);
        when(generationRepositoryPort.save(argThat(g -> g != null && stale.id().equals(g.id())))).thenReturn(failedStale);

        ApplicationMaterialGeneration freshPending = pendingGeneration(5L, 2L, fingerprint);
        when(generationRepositoryPort.save(argThat(g -> g != null && g.id() == null))).thenReturn(freshPending);
        ApplicationMaterialGeneration completed = freshPending.start(NOW).complete(NOW);
        when(generateApplicationMaterialsUseCase.generate(freshPending.id()))
                .thenReturn(new GenerateApplicationMaterialsOutcome(GenerationOutcomeStatus.COMPLETED, completed, null));
        stubSuccessfulRenderAndLoad(completed.id());

        PrepareApplicationPackageOutcome outcome = useCase.prepare(VACANCY_ID);

        ArgumentCaptor<ApplicationMaterialGeneration> saveCaptor = ArgumentCaptor.forClass(ApplicationMaterialGeneration.class);
        verify(generationRepositoryPort, times(2)).save(saveCaptor.capture());
        ApplicationMaterialGeneration staleTransition = saveCaptor.getAllValues().get(0);
        assertThat(staleTransition.id()).isEqualTo(stale.id());
        assertThat(staleTransition.status()).isEqualTo(ApplicationMaterialGenerationStatus.FAILED);
        assertThat(staleTransition.failureCode()).isEqualTo(ApplicationMaterialsGenerationFailureCode.STALE_IN_PROGRESS.name());
        // Never a raw exception message or infrastructure detail - a fixed, safe constant.
        assertThat(staleTransition.failureMessage()).doesNotContain("Exception", "at com.darya", "SQLState");

        verify(generateApplicationMaterialsUseCase, times(1)).generate(freshPending.id());
        assertPrepared(outcome, completed.id(), false);
    }

    @Test
    void prepare_freshInProgressGeneration_isNeverTreatedAsStale() {
        stubVacancyFound();
        String fingerprint = stubCurrentVersions(5L, 2L);
        ApplicationMaterialGeneration fresh = pendingGeneration(5L, 2L, fingerprint).start(NOW);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of(fresh));

        PrepareApplicationPackageOutcome outcome = useCase.prepare(VACANCY_ID);

        assertThat(outcome).isEqualTo(new PrepareApplicationPackageOutcome.AlreadyInProgress(fresh.id()));
        verify(generationRepositoryPort, never()).save(any());
        verify(generateApplicationMaterialsUseCase, never()).generate(any());
    }

    @Test
    void prepare_staleRecoveryLosesRace_reloadsWinnerAndJoinsWithoutDuplicateWork() {
        stubVacancyFound();
        String fingerprint = stubCurrentVersions(5L, 2L);
        ApplicationMaterialGeneration stale = staleInProgressGeneration(5L, 2L, fingerprint);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of(stale));
        when(generationRepositoryPort.save(argThat(g -> g != null && stale.id().equals(g.id()))))
                .thenThrow(new ApplicationMaterialGenerationConcurrentModificationException(stale.id(), VACANCY_ID, stale.version()));

        // The concurrent winner already committed the FAILED transition and moved on to complete a
        // brand-new generation by the time this call reloads.
        ApplicationMaterialGeneration winnerFailedStale =
                stale.fail(ApplicationMaterialsGenerationFailureCode.STALE_IN_PROGRESS.name(), "recovered", NOW);
        when(generationRepositoryPort.findById(stale.id())).thenReturn(Optional.of(winnerFailedStale));

        ApplicationMaterialGeneration freshPending = pendingGeneration(5L, 2L, fingerprint);
        when(generationRepositoryPort.save(argThat(g -> g != null && g.id() == null))).thenReturn(freshPending);
        ApplicationMaterialGeneration completed = freshPending.start(NOW).complete(NOW);
        when(generateApplicationMaterialsUseCase.generate(freshPending.id()))
                .thenReturn(new GenerateApplicationMaterialsOutcome(GenerationOutcomeStatus.COMPLETED, completed, null));
        stubSuccessfulRenderAndLoad(completed.id());

        PrepareApplicationPackageOutcome outcome = useCase.prepare(VACANCY_ID);

        // Exactly one save() actually created a new row (the failed-stale save() attempt threw,
        // so only the fresh-generation insert counts as a successful save here).
        verify(generationRepositoryPort, times(1)).save(argThat(g -> g != null && g.id() == null));
        verify(generateApplicationMaterialsUseCase, times(1)).generate(any());
        assertPrepared(outcome, completed.id(), false);
    }

    @Test
    void prepare_staleInProgressForDifferentSourceState_isIgnored_newGenerationCreatedIndependently() {
        stubVacancyFound();
        // Stale row was requested against a fingerprint that no longer matches current source state,
        // so it is never even considered a match - no recovery attempt, no interaction with it at all.
        ApplicationMaterialGeneration staleForOldState = staleInProgressGeneration(5L, 2L, STALE_FINGERPRINT);
        when(generationRepositoryPort.findByVacancyId(VACANCY_ID)).thenReturn(List.of(staleForOldState));
        String currentFingerprint = stubCurrentVersions(9L, null);

        ApplicationMaterialGeneration freshPending = pendingGeneration(9L, null, currentFingerprint);
        when(generationRepositoryPort.save(any(ApplicationMaterialGeneration.class))).thenReturn(freshPending);
        ApplicationMaterialGeneration completed = freshPending.start(NOW).complete(NOW);
        when(generateApplicationMaterialsUseCase.generate(freshPending.id()))
                .thenReturn(new GenerateApplicationMaterialsOutcome(GenerationOutcomeStatus.COMPLETED, completed, null));
        stubSuccessfulRenderAndLoad(completed.id());

        PrepareApplicationPackageOutcome outcome = useCase.prepare(VACANCY_ID);

        verify(generationRepositoryPort, never()).findById(any());
        verify(generationRepositoryPort).save(argThatRequestsNewGeneration(9L, null, currentFingerprint));
        assertPrepared(outcome, completed.id(), false);
    }

    // ==================== Helpers ====================

    private void stubVacancyFound() {
        when(vacancyQueryService.getById(VACANCY_ID)).thenReturn(vacancy);
        when(vacancyJobOfferMapper.toJobOffer(vacancy)).thenReturn(JOB_OFFER);
    }

    /** Stubs the current candidate context and returns the resulting source fingerprint (computed the same way production code computes it). */
    private String stubCurrentVersions(long candidateProfileVersion, Long careerHistoryVersion) {
        CandidateContextSnapshot snapshot = new CandidateContextSnapshot(
                UUID.randomUUID(), "primary", candidateProfileVersion, validProfile(),
                careerHistoryVersion == null ? Optional.empty() : Optional.of(careerHistory(careerHistoryVersion)));
        when(candidateContextProvider.loadCurrentContext()).thenReturn(snapshot);
        return ApplicationMaterialSourceFingerprint.sha256(snapshot, JOB_OFFER);
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

    /** An IN_PROGRESS generation whose startedAt is safely past STALE_TIMEOUT relative to NOW. */
    private ApplicationMaterialGeneration staleInProgressGeneration(long candidateProfileVersion, Long careerHistoryVersion, String sourceFingerprint) {
        Instant oldStartedAt = NOW.minus(STALE_TIMEOUT).minusSeconds(1);
        Instant oldRequestedAt = oldStartedAt.minusSeconds(1);
        ApplicationMaterialGeneration pending = new ApplicationMaterialGeneration(UUID.randomUUID(), VACANCY_ID,
                ApplicationMaterialGenerationStatus.PENDING, candidateProfileVersion, careerHistoryVersion, sourceFingerprint, oldRequestedAt,
                null, null, null, null, 0);
        return pending.start(oldStartedAt);
    }

    private ApplicationMaterialGeneration pendingGeneration(long candidateProfileVersion, Long careerHistoryVersion, String sourceFingerprint) {
        ApplicationMaterialGeneration requested = ApplicationMaterialGeneration.requestNew(
                VACANCY_ID, candidateProfileVersion, careerHistoryVersion, sourceFingerprint, NOW);
        // Simulate persistence assigning an id, matching what generationRepositoryPort.save would do.
        return new ApplicationMaterialGeneration(UUID.randomUUID(), requested.vacancyId(), requested.status(),
                requested.candidateProfileVersion(), requested.careerHistoryVersion(), requested.sourceFingerprint(), requested.requestedAt(),
                null, null, null, null, 0);
    }

    private ApplicationMaterialGeneration argThatRequestsNewGeneration(long candidateProfileVersion, Long careerHistoryVersion, String sourceFingerprint) {
        return argThat(g -> g != null && g.id() == null
                && g.vacancyId().equals(VACANCY_ID)
                && g.candidateProfileVersion() == candidateProfileVersion
                && java.util.Objects.equals(g.careerHistoryVersion(), careerHistoryVersion)
                && sourceFingerprint.equals(g.sourceFingerprint()));
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

    private CandidateProfileFacts validProfile() {
        return new CandidateProfileFacts("Senior Java Backend Engineer", "Senior", List.of(), List.of(), 5,
                new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null));
    }

    private CareerHistoryAggregate careerHistory(long version) {
        return new CareerHistoryAggregate(UUID.randomUUID(), UUID.randomUUID(), List.of(), version);
    }

    private CareerHistoryAggregate careerHistoryWithCompany(long version, String companyName) {
        CareerCompany company = new CareerCompany(UUID.randomUUID(), companyName, null, null, null, null, 0, List.of());
        return new CareerHistoryAggregate(UUID.randomUUID(), UUID.randomUUID(), List.of(company), version);
    }

    // ==================== Source-content-variation fixtures (fingerprint-change tests) ====================

    private CandidateContextSnapshot snapshotWithPersonalProject(long candidateProfileVersion, Long careerHistoryVersion, String projectName) {
        PersonalProject project = new PersonalProject(UUID.randomUUID(), UUID.randomUUID(), projectName, "A hobby project",
                null, null, null, 0,
                List.of(new PersonalProjectHighlight(UUID.randomUUID(), "Built something", 0)),
                List.of(new PersonalProjectTechnology(UUID.randomUUID(), "Java", null, 0)), 0);
        return new CandidateContextSnapshot(UUID.randomUUID(), "primary", candidateProfileVersion, validProfile(),
                careerHistoryVersion == null ? Optional.empty() : Optional.of(careerHistory(careerHistoryVersion)), List.of(project));
    }

    private CandidateContextSnapshot snapshotWithEducation(long candidateProfileVersion, Long careerHistoryVersion, String institution) {
        CandidateProfileFacts profile = new CandidateProfileFacts(
                "Senior Java Backend Engineer", "Senior", List.of(), List.of(), 5,
                new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null),
                null, null, null, null, null, List.of(new CandidateEducationFacts(null, institution, null, null, null, null, null, null, 0)));
        return new CandidateContextSnapshot(UUID.randomUUID(), "primary", candidateProfileVersion, profile,
                careerHistoryVersion == null ? Optional.empty() : Optional.of(careerHistory(careerHistoryVersion)));
    }

    private CandidateContextSnapshot snapshotWithLanguage(long candidateProfileVersion, Long careerHistoryVersion, String language) {
        CandidateProfileFacts profile = new CandidateProfileFacts(
                "Senior Java Backend Engineer", "Senior", List.of(), List.of(new CandidateLanguageFacts(language, "Native")), 5,
                new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null));
        return new CandidateContextSnapshot(UUID.randomUUID(), "primary", candidateProfileVersion, profile,
                careerHistoryVersion == null ? Optional.empty() : Optional.of(careerHistory(careerHistoryVersion)));
    }

    private CandidateContextSnapshot snapshotWithSkill(long candidateProfileVersion, Long careerHistoryVersion, String skillName) {
        CandidateProfileFacts profile = new CandidateProfileFacts(
                "Senior Java Backend Engineer", "Senior", List.of(new CandidateSkillFacts(skillName, null, null, SkillProficiency.STRONG)),
                List.of(), 5, new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null));
        return new CandidateContextSnapshot(UUID.randomUUID(), "primary", candidateProfileVersion, profile,
                careerHistoryVersion == null ? Optional.empty() : Optional.of(careerHistory(careerHistoryVersion)));
    }
}
