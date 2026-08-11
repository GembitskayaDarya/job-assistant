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
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedApplicationMaterials;
import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedApplicationMaterialsValidator;
import com.darya.jobassistant.applicationmaterials.render.model.RenderModelAssembler;
import com.darya.jobassistant.applicationmaterials.render.model.RenderableApplicationMaterials;
import com.darya.jobassistant.applicationmaterials.render.snapshot.aggregate.ApplicationMaterialRenderSnapshot;
import com.darya.jobassistant.applicationmaterials.render.snapshot.aggregate.ApplicationMaterialRenderSnapshotRepositoryPort;
import com.darya.jobassistant.applicationmaterials.result.aggregate.ApplicationMaterialGenerationResult;
import com.darya.jobassistant.applicationmaterials.result.aggregate.ApplicationMaterialGenerationResultRepositoryPort;
import com.darya.jobassistant.candidatecontext.applicationmaterials.ApplicationMaterialsCandidateContextProvider;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.CandidateContextForApplicationMaterials;
import com.darya.jobassistant.candidatecontext.applicationmaterials.model.CandidateContextVersionMismatchException;
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
 * Sprint 10 Step 3: the application use case orchestrating one {@link ApplicationMaterialGeneration}
 * attempt end to end - load, transition to {@code IN_PROGRESS}, load/validate candidate context,
 * call the AI provider, validate its response, persist the result, and transition to {@code
 * COMPLETED}, or fail safely at any step. The only caller of {@link ApplicationMaterialsAiPort} in
 * this codebase.
 *
 * <h2>Transaction boundaries</h2>
 *
 * Built with explicit {@link TransactionTemplate}s ({@code PROPAGATION_REQUIRES_NEW}), never
 * {@code @Transactional} on this class - matching {@code CareerHistoryImportUseCase}'s documented
 * rationale, and critical here specifically: this method is deliberately <em>not</em> transactional
 * itself, so nothing in a future refactor could accidentally cause the {@link
 * ApplicationMaterialsAiPort#generate} call (a real network request) to run inside an open
 * PostgreSQL transaction. Three short, independent transactions exist: {@link #startTransition}
 * (the {@code PENDING -> IN_PROGRESS} write), {@link #completionTransaction} (persisting the
 * semantic result, the {@link ApplicationMaterialRenderSnapshot} and the {@code IN_PROGRESS ->
 * COMPLETED} write together, atomically - the system must never commit {@code COMPLETED} without
 * both a persisted result and a persisted render snapshot), and {@link #failureTransaction} (the
 * {@code IN_PROGRESS -> FAILED} write). The AI call and its response validation happen entirely
 * between the first and third of these, with no transaction open.
 *
 * <h2>Render snapshot (production-readiness fix)</h2>
 *
 * The {@link ApplicationMaterialRenderSnapshot} persisted in {@link #completionTransaction} is
 * assembled via {@link RenderModelAssembler#assemble} from the exact same validated {@link
 * GeneratedApplicationMaterials}, the exact same {@link CandidateContextForApplicationMaterials}
 * instance used for the AI call, and the exact same {@link JobOffer} - never re-loaded or
 * re-derived. This closes the reproducibility window that existed when the snapshot was created
 * lazily on first render: Candidate Profile/Career History can now change freely after this
 * generation completes without ever making it unrenderable. {@code RenderApplicationMaterialsUseCase}
 * still contains a lazy-creation fallback, kept solely for generations that completed before this
 * fix and therefore have no snapshot of their own.
 *
 * <h2>Idempotency and concurrency</h2>
 *
 * An already-{@code COMPLETED} generation is never regenerated - its existing result is returned as
 * {@link GenerationOutcomeStatus#ALREADY_COMPLETED}. Two concurrent calls for the same {@code
 * PENDING} generation race on {@link ApplicationMaterialGeneration#start}'s save: exactly one wins
 * (the normal {@code ApplicationMaterialGenerationRepositoryPort} optimistic-version check - see
 * Step 1), the other observes {@link ApplicationMaterialGenerationConcurrentModificationException}
 * and reports {@link GenerationOutcomeStatus#ALREADY_IN_PROGRESS} without ever calling the AI
 * provider. A genuine regeneration is represented by a new {@code ApplicationMaterialGeneration},
 * never by reusing a {@code FAILED} or {@code COMPLETED} one - {@link
 * ApplicationMaterialGeneration#start} itself only accepts a {@code PENDING} generation, so this
 * class never attempts to restart either.
 *
 * <h2>Failure handling</h2>
 *
 * Every controlled failure point maps to a small, safe {@link ApplicationMaterialsGenerationFailureCode}
 * persisted as {@link ApplicationMaterialGeneration#failureCode()}. The accompanying {@code
 * failureMessage} is either this application's own already-safe exception message (candidate
 * context mismatch, result validation) or a fixed, generic message (AI provider/persistence
 * failures) - a raw Spring AI/OpenAI exception message, and any stack trace, is logged (see {@code
 * log.error} calls below) but never persisted, so a provider error can never leak infrastructure
 * detail into the database. If the database itself is unavailable when attempting the {@code
 * FAILED} transition, that failure is not caught here - it propagates to the caller unmasked,
 * exactly as the underlying problem occurred.
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
    private final ApplicationMaterialsCandidateContextProvider candidateContextProvider;
    private final VacancyQueryService vacancyQueryService;
    private final VacancyJobOfferMapper vacancyJobOfferMapper;
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
            ApplicationMaterialsCandidateContextProvider candidateContextProvider,
            VacancyQueryService vacancyQueryService,
            VacancyJobOfferMapper vacancyJobOfferMapper,
            ApplicationMaterialsAiPort aiPort,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.generationRepositoryPort = generationRepositoryPort;
        this.resultRepositoryPort = resultRepositoryPort;
        this.snapshotRepositoryPort = snapshotRepositoryPort;
        this.candidateContextProvider = candidateContextProvider;
        this.vacancyQueryService = vacancyQueryService;
        this.vacancyJobOfferMapper = vacancyJobOfferMapper;
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
        CandidateContextForApplicationMaterials context;
        JobOffer jobOffer;
        try {
            Vacancy vacancy = vacancyQueryService.getById(started.vacancyId());
            jobOffer = vacancyJobOfferMapper.toJobOffer(vacancy);
            context = candidateContextProvider.loadContext(started, jobOffer);
        } catch (CandidateContextVersionMismatchException e) {
            return failGeneration(started, ApplicationMaterialsGenerationFailureCode.CANDIDATE_CONTEXT_VERSION_MISMATCH, e.getMessage());
        }

        ApplicationMaterialsGenerationResponse aiResponse;
        try {
            aiResponse = aiPort.generate(context, jobOffer);
        } catch (ApplicationMaterialsAiException e) {
            log.error("Application materials AI request failed for generation {}", started.id(), e);
            // A cause means SpringAiApplicationMaterialsAdapter wrapped a genuine provider-level
            // RuntimeException (network/auth/rate-limit/provider error); no cause means the
            // adapter itself detected a structurally invalid response (e.g. an unparseable id) -
            // see that adapter's javadoc for the exact distinction.
            ApplicationMaterialsGenerationFailureCode code = e.getCause() != null
                    ? ApplicationMaterialsGenerationFailureCode.AI_PROVIDER_ERROR
                    : ApplicationMaterialsGenerationFailureCode.MALFORMED_AI_RESPONSE;
            String message = code == ApplicationMaterialsGenerationFailureCode.AI_PROVIDER_ERROR ? GENERIC_AI_FAILURE_MESSAGE : e.getMessage();
            return failGeneration(started, code, message);
        }

        GeneratedApplicationMaterials validated;
        try {
            validated = GeneratedApplicationMaterialsValidator.validate(aiResponse.materials(), context);
        } catch (ApplicationMaterialsValidationException e) {
            return failGeneration(started, ApplicationMaterialsGenerationFailureCode.RESULT_VALIDATION_FAILED, e.getMessage());
        }

        ApplicationMaterialGenerationResult resultToPersist = ApplicationMaterialGenerationResult.create(
                started.id(), validated.cv(), validated.coverLetter(),
                aiResponse.aiProvider(), aiResponse.aiModel(), aiResponse.promptVersion(), Instant.now(clock));

        RenderableApplicationMaterials renderable = RenderModelAssembler.assemble(validated, context, jobOffer);
        ApplicationMaterialRenderSnapshot snapshotToPersist = ApplicationMaterialRenderSnapshot.create(started.id(), renderable);

        try {
            CompletionOutcome outcome = completionTransaction.execute(status -> {
                ApplicationMaterialGenerationResult savedResult = resultRepositoryPort.save(resultToPersist);
                snapshotRepositoryPort.save(snapshotToPersist);
                ApplicationMaterialGeneration completedGeneration = generationRepositoryPort.save(started.complete(Instant.now(clock)));
                return new CompletionOutcome(savedResult, completedGeneration);
            });
            return new GenerateApplicationMaterialsOutcome(GenerationOutcomeStatus.COMPLETED, outcome.generation(), outcome.result());
        } catch (RuntimeException e) {
            log.error("Failed to persist application material generation result for generation {}", started.id(), e);
            return failGeneration(started, ApplicationMaterialsGenerationFailureCode.PERSISTENCE_FAILURE, GENERIC_PERSISTENCE_FAILURE_MESSAGE);
        }
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
