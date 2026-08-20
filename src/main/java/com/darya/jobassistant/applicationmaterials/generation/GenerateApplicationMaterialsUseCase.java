package com.darya.jobassistant.applicationmaterials.generation;

import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGeneration;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationConcurrentModificationException;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationNotFoundException;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationRepositoryPort;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationStatus;
import com.darya.jobassistant.applicationmaterials.generation.model.ApplicationMaterialsAiException;
import com.darya.jobassistant.applicationmaterials.generation.model.ApplicationMaterialsAiPort;
import com.darya.jobassistant.applicationmaterials.generation.model.ApplicationMaterialsGenerationFailureCode;
import com.darya.jobassistant.applicationmaterials.generation.model.ApplicationMaterialsGenerationResponse;
import com.darya.jobassistant.applicationmaterials.generation.model.ApplicationMaterialsValidationException;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCoverLetter;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCoverLetterValidator;
import com.darya.jobassistant.applicationmaterials.render.model.RenderModelAssembler;
import com.darya.jobassistant.applicationmaterials.render.model.RenderableApplicationMaterials;
import com.darya.jobassistant.applicationmaterials.render.model.RenderableCoverLetter;
import com.darya.jobassistant.applicationmaterials.render.snapshot.aggregate.ApplicationMaterialRenderSnapshot;
import com.darya.jobassistant.applicationmaterials.render.snapshot.aggregate.ApplicationMaterialRenderSnapshotRepositoryPort;
import com.darya.jobassistant.applicationmaterials.result.aggregate.ApplicationMaterialGenerationResult;
import com.darya.jobassistant.applicationmaterials.result.aggregate.ApplicationMaterialGenerationResultRepositoryPort;
import com.darya.jobassistant.candidatecontext.CandidateContextProvider;
import com.darya.jobassistant.candidatecontext.CandidateContextSnapshot;
import com.darya.jobassistant.candidatecontext.applicationmaterials.ApplicationMaterialsCandidateContextProvider;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.CandidateContextForApplicationMaterials;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.CandidateContextVersionMismatchException;
import com.darya.jobassistant.candidatecontext.cv.CvSourceSnapshotFactory;
import com.darya.jobassistant.candidatecontext.cv.CvTailoringUseCase;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvDocument;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceSnapshot;
import com.darya.jobassistant.candidatecontext.cv.tailoring.ai.CvTailoringAiException;
import com.darya.jobassistant.candidatecontext.cv.tailoring.validation.CvTailoringValidationException;
import com.darya.jobassistant.candidates.runtime.CandidateProfileNotConfiguredException;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.mapper.VacancyJobOfferMapper;
import com.darya.jobassistant.vacancies.service.VacancyQueryService;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sprint 10 Step 3 (Sprint 11 Big Block 7 correction): the application use case orchestrating one
 * {@link ApplicationMaterialGeneration} attempt end to end - load, transition to {@code
 * IN_PROGRESS}, load candidate context, tailor the CV via the Sprint 11 structured pipeline,
 * generate the cover letter via the Sprint 10 AI path, validate, persist the result, and transition
 * to {@code COMPLETED}, or fail safely at any step. The only caller of {@link CvTailoringUseCase}
 * and {@link ApplicationMaterialsAiPort} in this codebase.
 *
 * <h2>Structured CV tailoring vs. cover-letter prose generation (Sprint 11 Big Block 7)</h2>
 *
 * These are two deliberately separate steps, using two structurally different mechanisms - never
 * merged into one call:
 *
 * <ol>
 *   <li>{@link #tailorCv} builds a complete, unbounded {@link CvSourceSnapshot} (via {@link
 *       CandidateContextProvider#loadCurrentContext()} + {@link CvSourceSnapshotFactory#from}) and
 *       calls {@link CvTailoringUseCase#tailor} - which itself calls the AI, source-validates the
 *       result, and assembles the final {@link TailoredCvDocument}. Invalid AI tailoring output can
 *       never reach this use case as a {@link TailoredCvDocument} at all - {@link CvTailoringUseCase}
 *       already enforces that.
 *   <li>{@link #generateCoverLetter} loads the narrower, vacancy-bounded {@link
 *       CandidateContextForApplicationMaterials} (via {@link ApplicationMaterialsCandidateContextProvider})
 *       and calls {@link ApplicationMaterialsAiPort#generate}, then {@link
 *       GeneratedCoverLetterValidator#validate}.
 * </ol>
 *
 * <p>The bounded context load happens first and is reused as the version-consistency gate for both
 * steps: {@link ApplicationMaterialsCandidateContextProvider#loadContext} already enforces that the
 * current Candidate Profile/Career History versions match what this generation recorded at request
 * time (throwing {@link CandidateContextVersionMismatchException} otherwise); the plain {@link
 * CandidateContextSnapshot} loaded immediately afterward for CV tailoring is trusted to reflect the
 * same effective versions, since nothing else concurrently modifies candidate data mid-request in
 * practice - see the Sprint 11 Big Block 7 final report for the narrow theoretical race this does
 * not close.
 *
 * <h2>Transaction boundaries</h2>
 *
 * Built with explicit {@link TransactionTemplate}s ({@code PROPAGATION_REQUIRES_NEW}), never
 * {@code @Transactional} on this class - matching {@code CareerHistoryImportUseCase}'s documented
 * rationale, and critical here specifically: this method is deliberately <em>not</em> transactional
 * itself, so nothing in a future refactor could accidentally cause an AI call (a real network
 * request) to run inside an open PostgreSQL transaction. Three short, independent transactions
 * exist: {@link #startTransition} (the {@code PENDING -> IN_PROGRESS} write), {@link
 * #completionTransaction} (persisting the semantic result, the {@link ApplicationMaterialRenderSnapshot}
 * and the {@code IN_PROGRESS -> COMPLETED} write together, atomically - the system must never commit
 * {@code COMPLETED} without both a persisted result and a persisted render snapshot), and {@link
 * #failureTransaction} (the {@code IN_PROGRESS -> FAILED} write). Every AI call and validation step
 * happens entirely between the first and third of these, with no transaction open.
 *
 * <h2>Render snapshot</h2>
 *
 * The {@link ApplicationMaterialRenderSnapshot} persisted in {@link #completionTransaction} is built
 * directly from the exact validated {@link TailoredCvDocument} and {@link RenderableCoverLetter} -
 * never re-loaded or re-derived later. {@code RenderApplicationMaterialsUseCase} still contains a
 * lazy-creation fallback, kept solely for generations that completed before the original Sprint 10
 * production-readiness fix and therefore have no snapshot of their own.
 *
 * <h2>Idempotency and concurrency</h2>
 *
 * An already-{@code COMPLETED} generation is never regenerated - its existing result is returned as
 * {@link GenerationOutcomeStatus#ALREADY_COMPLETED}. Two concurrent calls for the same {@code
 * PENDING} generation race on {@link ApplicationMaterialGeneration#start}'s save: exactly one wins
 * (the normal {@code ApplicationMaterialGenerationRepositoryPort} optimistic-version check), the
 * other observes {@link ApplicationMaterialGenerationConcurrentModificationException} and reports
 * {@link GenerationOutcomeStatus#ALREADY_IN_PROGRESS} without ever calling an AI provider.
 *
 * <h2>Failure handling</h2>
 *
 * Every controlled failure point maps to a small, safe {@link ApplicationMaterialsGenerationFailureCode}
 * persisted as {@link ApplicationMaterialGeneration#failureCode()}. {@link CvTailoringAiException}'s
 * cause presence distinguishes {@link ApplicationMaterialsGenerationFailureCode#AI_PROVIDER_ERROR}
 * from {@link ApplicationMaterialsGenerationFailureCode#MALFORMED_AI_RESPONSE} exactly the same way
 * {@link ApplicationMaterialsAiException} already does for the cover letter - one shared convention
 * across both AI ports. A raw Spring AI/OpenAI exception message, and any stack trace, is logged
 * (see the {@code log.error} calls below) but never persisted.
 */
@Service
@Slf4j
public class GenerateApplicationMaterialsUseCase {

    private static final int MAX_FAILURE_MESSAGE_LENGTH = 500;
    private static final String GENERIC_AI_FAILURE_MESSAGE = "AI provider request failed";
    private static final String GENERIC_PERSISTENCE_FAILURE_MESSAGE = "Failed to persist the generated result";

    private final ApplicationMaterialGenerationRepositoryPort generationRepositoryPort;
    private final ApplicationMaterialGenerationResultRepositoryPort resultRepositoryPort;
    private final ApplicationMaterialRenderSnapshotRepositoryPort snapshotRepositoryPort;
    private final ApplicationMaterialsCandidateContextProvider applicationMaterialsCandidateContextProvider;
    private final CandidateContextProvider candidateContextProvider;
    private final VacancyQueryService vacancyQueryService;
    private final VacancyJobOfferMapper vacancyJobOfferMapper;
    private final CvTailoringUseCase cvTailoringUseCase;
    private final ApplicationMaterialsAiPort aiPort;
    private final Clock clock;

    private final TransactionTemplate startTransition;
    private final TransactionTemplate completionTransaction;
    private final TransactionTemplate failureTransaction;

    @Autowired
    public GenerateApplicationMaterialsUseCase(
            ApplicationMaterialGenerationRepositoryPort generationRepositoryPort,
            ApplicationMaterialGenerationResultRepositoryPort resultRepositoryPort,
            ApplicationMaterialRenderSnapshotRepositoryPort snapshotRepositoryPort,
            ApplicationMaterialsCandidateContextProvider applicationMaterialsCandidateContextProvider,
            CandidateContextProvider candidateContextProvider,
            VacancyQueryService vacancyQueryService,
            VacancyJobOfferMapper vacancyJobOfferMapper,
            CvTailoringUseCase cvTailoringUseCase,
            ApplicationMaterialsAiPort aiPort,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.generationRepositoryPort = generationRepositoryPort;
        this.resultRepositoryPort = resultRepositoryPort;
        this.snapshotRepositoryPort = snapshotRepositoryPort;
        this.applicationMaterialsCandidateContextProvider = applicationMaterialsCandidateContextProvider;
        this.candidateContextProvider = candidateContextProvider;
        this.vacancyQueryService = vacancyQueryService;
        this.vacancyJobOfferMapper = vacancyJobOfferMapper;
        this.cvTailoringUseCase = cvTailoringUseCase;
        this.aiPort = aiPort;
        this.clock = clock;

        this.startTransition = newRequiresNewTransaction(transactionManager);
        this.completionTransaction = newRequiresNewTransaction(transactionManager);
        this.failureTransaction = newRequiresNewTransaction(transactionManager);
    }

    private static TransactionTemplate newRequiresNewTransaction(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    public GenerateApplicationMaterialsOutcome generate(UUID generationId) {
        ApplicationMaterialGeneration generation = loadGeneration(generationId);
        if (generation.status() != ApplicationMaterialGenerationStatus.PENDING) {
            return outcomeForNonPendingGeneration(generation);
        }

        ApplicationMaterialGeneration started;
        try {
            started = startTransition.execute(status -> generationRepositoryPort.save(generation.start(Instant.now(clock))));
        } catch (ApplicationMaterialGenerationConcurrentModificationException e) {
            return outcomeForNonPendingGeneration(loadGeneration(generationId));
        }

        return runGeneration(started);
    }

    private ApplicationMaterialGeneration loadGeneration(UUID generationId) {
        return generationRepositoryPort.findById(generationId)
                .orElseThrow(() -> new ApplicationMaterialGenerationNotFoundException(generationId));
    }

    private GenerateApplicationMaterialsOutcome outcomeForNonPendingGeneration(ApplicationMaterialGeneration generation) {
        return switch (generation.status()) {
            case COMPLETED -> new GenerateApplicationMaterialsOutcome(
                    GenerationOutcomeStatus.ALREADY_COMPLETED, generation, loadExistingResult(generation.id()));
            case IN_PROGRESS -> new GenerateApplicationMaterialsOutcome(GenerationOutcomeStatus.ALREADY_IN_PROGRESS, generation, null);
            case FAILED -> new GenerateApplicationMaterialsOutcome(GenerationOutcomeStatus.ALREADY_FAILED, generation, null);
            case PENDING -> throw new IllegalStateException(
                    "Application material generation '" + generation.id() + "' is unexpectedly PENDING on the non-pending outcome path");
        };
    }

    private ApplicationMaterialGenerationResult loadExistingResult(UUID generationId) {
        return resultRepositoryPort.findByGenerationId(generationId)
                .orElseThrow(() -> new IllegalStateException(
                        "Application material generation '" + generationId + "' is COMPLETED but has no persisted result"));
    }

    private GenerateApplicationMaterialsOutcome runGeneration(ApplicationMaterialGeneration started) {
        Vacancy vacancy = vacancyQueryService.getById(started.vacancyId());
        JobOffer jobOffer = vacancyJobOfferMapper.toJobOffer(vacancy);

        CandidateContextForApplicationMaterials context;
        try {
            context = applicationMaterialsCandidateContextProvider.loadContext(started, jobOffer);
        } catch (CandidateContextVersionMismatchException e) {
            return failGeneration(started, ApplicationMaterialsGenerationFailureCode.CANDIDATE_CONTEXT_VERSION_MISMATCH, e.getMessage());
        }

        TailoredCvDocument tailoredCv;
        try {
            tailoredCv = tailorCv(jobOffer);
        } catch (CandidateProfileNotConfiguredException e) {
            return failGeneration(started, ApplicationMaterialsGenerationFailureCode.CANDIDATE_CONTEXT_NOT_CONFIGURED, e.getMessage());
        } catch (CvTailoringAiException e) {
            log.error("CV tailoring AI request failed for generation {}", started.id(), e);
            ApplicationMaterialsGenerationFailureCode code = e.getCause() != null
                    ? ApplicationMaterialsGenerationFailureCode.AI_PROVIDER_ERROR
                    : ApplicationMaterialsGenerationFailureCode.MALFORMED_AI_RESPONSE;
            String message = code == ApplicationMaterialsGenerationFailureCode.AI_PROVIDER_ERROR ? GENERIC_AI_FAILURE_MESSAGE : e.getMessage();
            return failGeneration(started, code, message);
        } catch (CvTailoringValidationException e) {
            return failGeneration(started, ApplicationMaterialsGenerationFailureCode.CV_TAILORING_VALIDATION_FAILED, e.getMessage());
        }

        GeneratedCoverLetter validatedCoverLetter;
        ApplicationMaterialsGenerationResponse aiResponse;
        try {
            aiResponse = aiPort.generate(context, jobOffer);
        } catch (ApplicationMaterialsAiException e) {
            log.error("Cover letter AI request failed for generation {}", started.id(), e);
            ApplicationMaterialsGenerationFailureCode code = e.getCause() != null
                    ? ApplicationMaterialsGenerationFailureCode.AI_PROVIDER_ERROR
                    : ApplicationMaterialsGenerationFailureCode.MALFORMED_AI_RESPONSE;
            String message = code == ApplicationMaterialsGenerationFailureCode.AI_PROVIDER_ERROR ? GENERIC_AI_FAILURE_MESSAGE : e.getMessage();
            return failGeneration(started, code, message);
        }
        try {
            validatedCoverLetter = GeneratedCoverLetterValidator.validate(aiResponse.coverLetter(), context);
        } catch (ApplicationMaterialsValidationException e) {
            return failGeneration(started, ApplicationMaterialsGenerationFailureCode.RESULT_VALIDATION_FAILED, e.getMessage());
        }
        log.info("Cover letter generated and validated for generation {}", started.id());

        ApplicationMaterialGenerationResult resultToPersist = ApplicationMaterialGenerationResult.create(
                started.id(), tailoredCv, validatedCoverLetter,
                aiResponse.aiProvider(), aiResponse.aiModel(), aiResponse.promptVersion(), Instant.now(clock));

        RenderableCoverLetter renderableCoverLetter = RenderModelAssembler.assembleCoverLetter(validatedCoverLetter, jobOffer);
        RenderableApplicationMaterials renderable = new RenderableApplicationMaterials(tailoredCv, renderableCoverLetter);
        ApplicationMaterialRenderSnapshot snapshotToPersist = ApplicationMaterialRenderSnapshot.create(started.id(), renderable);

        try {
            CompletionOutcome outcome = completionTransaction.execute(status -> {
                ApplicationMaterialGenerationResult savedResult = resultRepositoryPort.save(resultToPersist);
                snapshotRepositoryPort.save(snapshotToPersist);
                ApplicationMaterialGeneration completedGeneration = generationRepositoryPort.save(started.complete(Instant.now(clock)));
                return new CompletionOutcome(savedResult, completedGeneration);
            });
            log.info("Application material generation {} completed successfully", outcome.generation().id());
            return new GenerateApplicationMaterialsOutcome(GenerationOutcomeStatus.COMPLETED, outcome.generation(), outcome.result());
        } catch (RuntimeException e) {
            log.error("Failed to persist application material generation result for generation {}", started.id(), e);
            return failGeneration(started, ApplicationMaterialsGenerationFailureCode.PERSISTENCE_FAILURE, GENERIC_PERSISTENCE_FAILURE_MESSAGE);
        }
    }

    /**
     * Sprint 11 Big Block 7: builds the complete, unbounded {@link CvSourceSnapshot} and drives it
     * through {@link CvTailoringUseCase#tailor} - the structured CV pipeline, entirely separate from
     * cover-letter prose generation (see class javadoc).
     */
    private TailoredCvDocument tailorCv(JobOffer jobOffer) {
        CandidateContextSnapshot snapshot = candidateContextProvider.loadCurrentContext();
        CvSourceSnapshot cvSourceSnapshot = CvSourceSnapshotFactory.from(snapshot);
        return cvTailoringUseCase.tailor(jobOffer, cvSourceSnapshot);
    }

    /**
     * Runs in its own {@link #failureTransaction} - deliberately not caught by this method's own
     * caller: if persisting the {@code FAILED} transition itself fails (e.g. the database is
     * unavailable), that exception propagates unmasked rather than being folded into a misleading
     * outcome.
     */
    private GenerateApplicationMaterialsOutcome failGeneration(
            ApplicationMaterialGeneration inProgress, ApplicationMaterialsGenerationFailureCode code, String message) {
        String safeMessage = truncate(message);
        ApplicationMaterialGeneration failed = failureTransaction.execute(status ->
                generationRepositoryPort.save(inProgress.fail(code.name(), safeMessage, Instant.now(clock))));
        return new GenerateApplicationMaterialsOutcome(GenerationOutcomeStatus.FAILED, failed, null);
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_FAILURE_MESSAGE_LENGTH ? message : message.substring(0, MAX_FAILURE_MESSAGE_LENGTH);
    }

    private record CompletionOutcome(ApplicationMaterialGenerationResult result, ApplicationMaterialGeneration generation) {
    }
}
