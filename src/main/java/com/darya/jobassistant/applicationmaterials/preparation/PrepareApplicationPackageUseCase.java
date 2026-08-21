package com.darya.jobassistant.applicationmaterials.preparation;

import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGeneration;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationActiveConflictException;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationConcurrentModificationException;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationRepositoryPort;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationStatus;
import com.darya.jobassistant.applicationmaterials.artifact.aggregate.ApplicationMaterialArtifact;
import com.darya.jobassistant.applicationmaterials.config.ApplicationMaterialGenerationProperties;
import com.darya.jobassistant.applicationmaterials.generation.GenerateApplicationMaterialsOutcome;
import com.darya.jobassistant.applicationmaterials.generation.GenerateApplicationMaterialsUseCase;
import com.darya.jobassistant.applicationmaterials.generation.model.ApplicationMaterialsGenerationFailureCode;
import com.darya.jobassistant.applicationmaterials.render.RenderApplicationMaterialsException;
import com.darya.jobassistant.applicationmaterials.render.RenderApplicationMaterialsResult;
import com.darya.jobassistant.applicationmaterials.render.RenderApplicationMaterialsUseCase;
import com.darya.jobassistant.candidatecontext.CandidateContextProvider;
import com.darya.jobassistant.candidatecontext.CandidateContextSnapshot;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import com.darya.jobassistant.exception.VacancyNotFoundException;
import com.darya.jobassistant.integrations.filestorage.FileStorageException;
import com.darya.jobassistant.integrations.filestorage.FileStoragePort;
import com.darya.jobassistant.integrations.filestorage.StoredFileContent;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.mapper.VacancyJobOfferMapper;
import com.darya.jobassistant.vacancies.service.VacancyQueryService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Sprint 10 Step 5: the application-level facade that connects the already-built application-
 * material pipeline (Steps 1-4) to a delivery channel (Telegram) - the only orchestration point a
 * Telegram command/callback is allowed to call for this feature. Telegram itself never touches
 * {@link ApplicationMaterialGenerationRepositoryPort}, {@link GenerateApplicationMaterialsUseCase},
 * {@link RenderApplicationMaterialsUseCase}, an artifact repository, or {@link FileStoragePort}
 * directly - see this class's package javadoc-equivalent note in the Sprint 10 Step 5 report.
 *
 * <h2>Reuse algorithm (Sprint 11 production hardening)</h2>
 *
 * Reuse decisions are keyed on the target Vacancy plus one authoritative, complete-source-state
 * digest - {@link ApplicationMaterialSourceFingerprint#sha256}, computed once per call from the
 * candidate's current {@link CandidateContextSnapshot} (read via {@link
 * CandidateContextProvider#loadCurrentContext()}) and the current {@link JobOffer}. {@link
 * ApplicationMaterialGenerationRepositoryPort#findByVacancyId} returns every generation for the
 * Vacancy newest-requested-first; the first (i.e. most recent) one whose stored {@link
 * ApplicationMaterialGeneration#sourceFingerprint()} exactly equals the freshly computed one is the
 * candidate for reuse - older generations, any generation recorded against a different fingerprint,
 * and any <em>legacy</em> generation (a {@code null} stored fingerprint, from before this field
 * existed) are never considered. {@code candidateProfileVersion}/{@code careerHistoryVersion} are
 * still recorded on every generation and still gate {@code
 * ApplicationMaterialsCandidateContextProvider#loadContext}'s own mid-flight staleness check inside
 * {@code GenerateApplicationMaterialsUseCase}, but they are no longer this class's own reuse
 * authority - a change that does not move either version (e.g. a Personal-Project-only edit) still
 * changes {@link ApplicationMaterialSourceFingerprint#sha256}, and therefore still correctly
 * invalidates a stale {@code COMPLETED} generation, closing exactly the gap those two fields alone
 * could not.
 *
 * <ul>
 *   <li>{@code COMPLETED}: reused outright - no {@link GenerateApplicationMaterialsUseCase#generate}
 *       call, so never another AI request. {@link RenderApplicationMaterialsUseCase#render} still
 *       runs to ensure artifacts exist, but it reuses persisted ones when present (Step 4) and
 *       makes no AI call itself.
 *   <li>{@code IN_PROGRESS}: reported as {@link PrepareApplicationPackageOutcome.AlreadyInProgress}
 *       immediately - no generation or AI call is started - unless {@link
 *       ApplicationMaterialGeneration#isStaleInProgress} reports it has exceeded {@link
 *       ApplicationMaterialGenerationProperties#staleInProgressTimeout()}, in which case {@link
 *       #recoverStaleThenCreateOrJoin} runs instead (see "Stale IN_PROGRESS recovery" below).
 *   <li>{@code PENDING}: driven forward via {@link GenerateApplicationMaterialsUseCase#generate},
 *       which itself owns the {@code PENDING -> IN_PROGRESS} optimistic-locked transition - a
 *       matching {@code PENDING} row found here (rather than freshly created by this same call) is
 *       just as safe to hand to that method, since a concurrent caller racing to start the same row
 *       loses that transition and receives {@code ALREADY_IN_PROGRESS} back, never a duplicate AI
 *       call. This is the "use existing optimistic concurrency/database invariants as the final
 *       protection" rule in practice.
 *   <li>{@code FAILED}: never mutated or retried - a brand-new generation is created instead, then
 *       driven forward exactly like the "no matching generation" case below.
 * </ul>
 *
 * When no generation matches the current versions at all, a brand-new one is created via {@link
 * ApplicationMaterialGeneration#requestNew} and immediately driven forward via {@link
 * GenerateApplicationMaterialsUseCase#generate}.
 *
 * <h2>Concurrency</h2>
 *
 * Two requests racing to drive the very same {@code PENDING} generation are safe by construction
 * (see above). Two requests racing when <em>no</em> generation exists yet for the current versions
 * are closed by V25's {@code uk_amg_active_effective_key} partial unique index - PostgreSQL's own
 * final authority that at most one {@code PENDING}/{@code IN_PROGRESS} generation may exist per
 * (vacancy, candidate versions). {@link #createOrJoinGeneration} is the one place that creates a
 * new generation; when it loses that race, {@link ApplicationMaterialGenerationActiveConflictException}
 * is caught, the winning row is reloaded, and this method continues from whatever state that row is
 * actually in - never surfaced as a raw {@code DataIntegrityViolationException} to a caller.
 *
 * <h2>Stale IN_PROGRESS recovery</h2>
 *
 * A process may transition a generation to {@code IN_PROGRESS} and then crash or be killed before
 * reaching {@code COMPLETED}/{@code FAILED}, leaving the row stuck. {@link #recoverStaleThenCreateOrJoin}
 * never resets it back to {@code PENDING} or reuses it for another AI attempt - the previous process
 * may have completed or partially completed the external AI request before crashing, so retrying
 * the same row would blur execution history. Instead it transitions the stale row to {@code FAILED}
 * (via {@link ApplicationMaterialGeneration#fail} with {@link
 * ApplicationMaterialsGenerationFailureCode#STALE_IN_PROGRESS}) and, only if that transition itself
 * succeeds, creates/joins a brand-new generation through the exact same {@link #createOrJoinGeneration}
 * used for every other "no active generation yet" case - no duplicated generation/rendering logic.
 *
 * <p>The stale transition reuses {@link ApplicationMaterialGenerationRepositoryPort#save}'s existing
 * optimistic-version check as its sole concurrency mechanism - no new query, lock, or lease. Two
 * requests that both observe the same stale row read it at the same version; only one {@code save}
 * can win, and the loser's call throws {@link ApplicationMaterialGenerationConcurrentModificationException},
 * caught here to reload the row (now {@code FAILED}, committed by the winner) and dispatch it
 * through {@link #resolve} exactly like any other reload - which, being {@code FAILED}, itself calls
 * {@link #createOrJoinGeneration}. Both requests therefore always end up calling {@link
 * #createOrJoinGeneration}, and V25's active-uniqueness index (see below) is what actually
 * guarantees only one of them drives a real AI call - the stale-recovery race and the "no matching
 * generation" race are closed by the very same final invariant.
 *
 * <h2>Failure handling</h2>
 *
 * Every controlled failure - a Vacancy that does not exist, a generation that fails, a render or
 * artifact-load failure - is reported as a specific, safe {@link PrepareApplicationPackageOutcome}
 * variant; the underlying exception is logged (never its message surfaced to a caller) via the
 * {@code log.error} calls below. This use case never changes {@link ApplicationMaterialGeneration}
 * lifecycle state itself - it only reads existing generations and delegates every state transition
 * to {@link GenerateApplicationMaterialsUseCase}/{@link RenderApplicationMaterialsUseCase}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PrepareApplicationPackageUseCase {

    private final VacancyQueryService vacancyQueryService;
    private final VacancyJobOfferMapper vacancyJobOfferMapper;
    private final CandidateContextProvider candidateContextProvider;
    private final ApplicationMaterialGenerationRepositoryPort generationRepositoryPort;
    private final GenerateApplicationMaterialsUseCase generateApplicationMaterialsUseCase;
    private final RenderApplicationMaterialsUseCase renderApplicationMaterialsUseCase;
    private final FileStoragePort fileStoragePort;
    private final ApplicationMaterialGenerationProperties generationProperties;
    private final Clock clock;

    /**
     * Sprint 11 production hardening: bundles the three values every reuse/creation decision needs
     * to thread through this class's call graph - {@code candidateProfileVersion}/{@code
     * careerHistoryVersion} (audit metadata only, still persisted on every generation) and {@code
     * sourceFingerprint} ({@link ApplicationMaterialSourceFingerprint#sha256}'s output - the sole
     * reuse-validity authority). Computed exactly once per {@link #prepare} call.
     */
    private record EffectiveSourceState(long candidateProfileVersion, Long careerHistoryVersion, String sourceFingerprint) {
    }

    /**
     * Bounds {@link #createOrJoinGeneration}'s retry loop after losing the V25 active-uniqueness
     * race - one retry resolves the overwhelmingly common case (the winner is now visible), a
     * second covers the narrow window where that winner itself completed/failed between our
     * conflict and our reload; this is a safety margin against a live-locked pathological case, not
     * a distributed lock or backoff policy.
     */
    private static final int MAX_ACTIVE_CONFLICT_ATTEMPTS = 3;

    /**
     * Fixed, safe failure message accompanying {@link ApplicationMaterialsGenerationFailureCode#STALE_IN_PROGRESS}
     * - never a stack trace or other infrastructure detail, matching every other failure message
     * this codebase persists.
     */
    private static final String STALE_IN_PROGRESS_FAILURE_MESSAGE =
            "Generation abandoned: no lifecycle progress observed within the configured stale-IN_PROGRESS timeout";

    public PrepareApplicationPackageOutcome prepare(UUID vacancyId) {
        log.info("Application package preparation requested for vacancy {}", vacancyId);

        Vacancy vacancy;
        try {
            vacancy = vacancyQueryService.getById(vacancyId);
        } catch (VacancyNotFoundException e) {
            log.info("Application package preparation requested for unknown vacancy {}", vacancyId);
            return new PrepareApplicationPackageOutcome.VacancyNotFound(vacancyId);
        }

        CandidateContextSnapshot snapshot = candidateContextProvider.loadCurrentContext();
        JobOffer jobOffer = vacancyJobOfferMapper.toJobOffer(vacancy);
        EffectiveSourceState effectiveSourceState = new EffectiveSourceState(
                snapshot.candidateProfileVersion(),
                snapshot.careerHistory().map(CareerHistoryAggregate::version).orElse(null),
                ApplicationMaterialSourceFingerprint.sha256(snapshot, jobOffer));

        Optional<ApplicationMaterialGeneration> matching = findMatchingGeneration(vacancyId, effectiveSourceState);
        if (matching.isPresent()) {
            return resolve(matching.get(), vacancyId, effectiveSourceState);
        }

        return createOrJoinGeneration(vacancyId, effectiveSourceState);
    }

    /**
     * Dispatches purely on {@code generation}'s current status - the one place both the initial
     * lookup and every post-conflict reload (see {@link #createOrJoinGeneration}) decide what to do
     * next, so the two call sites can never drift into different behavior for the same status.
     * {@code FAILED} is never reused/retried in place - a brand-new generation is requested
     * instead, exactly like the "no matching generation" case.
     */
    private PrepareApplicationPackageOutcome resolve(
            ApplicationMaterialGeneration generation, UUID vacancyId, EffectiveSourceState effectiveSourceState) {
        return switch (generation.status()) {
            case COMPLETED -> {
                log.info("Reusing completed application material generation {} for vacancy {}", generation.id(), vacancyId);
                yield renderAndLoad(generation, true);
            }
            case IN_PROGRESS -> {
                if (generation.isStaleInProgress(Instant.now(clock), generationProperties.staleInProgressTimeout())) {
                    yield recoverStaleThenCreateOrJoin(generation, vacancyId, effectiveSourceState);
                }
                log.info("Application material generation {} for vacancy {} is already in progress", generation.id(), vacancyId);
                yield new PrepareApplicationPackageOutcome.AlreadyInProgress(generation.id());
            }
            case PENDING -> {
                log.info("Continuing pending application material generation {} for vacancy {}", generation.id(), vacancyId);
                yield driveGeneration(generation);
            }
            case FAILED -> createOrJoinGeneration(vacancyId, effectiveSourceState);
        };
    }

    /**
     * Attempts to recover {@code stale} - see this class's "Stale IN_PROGRESS recovery" javadoc for
     * the full algorithm and concurrency argument. Never resets {@code stale} back to {@code
     * PENDING} or reuses it for another AI attempt; on winning the transition to {@code FAILED} it
     * always proceeds to {@link #createOrJoinGeneration} for a brand-new generation.
     */
    private PrepareApplicationPackageOutcome recoverStaleThenCreateOrJoin(
            ApplicationMaterialGeneration stale, UUID vacancyId, EffectiveSourceState effectiveSourceState) {
        try {
            ApplicationMaterialGeneration failed = generationRepositoryPort.save(stale.fail(
                    ApplicationMaterialsGenerationFailureCode.STALE_IN_PROGRESS.name(),
                    STALE_IN_PROGRESS_FAILURE_MESSAGE, Instant.now(clock)));
            log.warn("Recovered stale IN_PROGRESS application material generation {} for vacancy {} (startedAt={})",
                    failed.id(), vacancyId, stale.startedAt());
            return createOrJoinGeneration(vacancyId, effectiveSourceState);
        } catch (ApplicationMaterialGenerationConcurrentModificationException e) {
            log.info("Lost the stale-recovery race for application material generation {} for vacancy {} - reloading", stale.id(), vacancyId);
            ApplicationMaterialGeneration current = generationRepositoryPort.findById(stale.id())
                    .orElseThrow(() -> new IllegalStateException(
                            "Lost a stale-recovery race for generation '" + stale.id() + "' but it no longer exists"));
            return resolve(current, vacancyId, effectiveSourceState);
        }
    }

    /**
     * The sole reuse-validity check (Sprint 11 production hardening): a generation matches only when
     * its stored {@link ApplicationMaterialGeneration#sourceFingerprint()} exactly equals the freshly
     * computed current one. A {@code null} stored fingerprint (a legacy generation, from before this
     * field existed) never matches anything, even if its old {@code candidateProfileVersion}/{@code
     * careerHistoryVersion} happen to equal the current ones - see {@code
     * ApplicationMaterialGeneration#sourceFingerprint()}'s own javadoc.
     */
    private Optional<ApplicationMaterialGeneration> findMatchingGeneration(UUID vacancyId, EffectiveSourceState effectiveSourceState) {
        List<ApplicationMaterialGeneration> generations = generationRepositoryPort.findByVacancyId(vacancyId);
        return generations.stream()
                .filter(g -> effectiveSourceState.sourceFingerprint().equals(g.sourceFingerprint()))
                .findFirst();
    }

    /**
     * Creates a brand-new {@code PENDING} generation and drives it forward. If V31's {@code
     * uk_amg_active_source_fingerprint} rejects the insert - another request created the active
     * generation for this exact (vacancy, source fingerprint) key first - reloads the winning row
     * and, unless it is itself {@code FAILED} (a narrow window where that winner completed/failed
     * between our conflict and our reload - the index never blocks a retry against it, since FAILED
     * is outside its partial index), dispatches it via {@link #resolve} without ever creating a
     * second row. Bounded by {@link #MAX_ACTIVE_CONFLICT_ATTEMPTS} so a pathologically repeating race
     * cannot loop forever; the final attempt is unguarded, exactly like any single first-time call.
     */
    private PrepareApplicationPackageOutcome createOrJoinGeneration(UUID vacancyId, EffectiveSourceState effectiveSourceState) {
        for (int attempt = 1; attempt < MAX_ACTIVE_CONFLICT_ATTEMPTS; attempt++) {
            try {
                return insertAndDrive(vacancyId, effectiveSourceState);
            } catch (ApplicationMaterialGenerationActiveConflictException e) {
                log.info("Lost the active application material generation creation race for vacancy {} (attempt {})", vacancyId, attempt);
                ApplicationMaterialGeneration winner = findMatchingGeneration(vacancyId, effectiveSourceState)
                        .orElseThrow(() -> new IllegalStateException(
                                "Lost an active application material generation creation race for vacancy '" + vacancyId
                                        + "' but no matching generation now exists"));
                if (winner.status() != ApplicationMaterialGenerationStatus.FAILED) {
                    log.info("Joined existing active application material generation {} (status={}) for vacancy {}",
                            winner.id(), winner.status(), vacancyId);
                    // Never FAILED here, so resolve's own FAILED branch (which would call back into
                    // this method) cannot be taken - no risk of the attempt bound being reset.
                    return resolve(winner, vacancyId, effectiveSourceState);
                }
                // winner is FAILED - loop and retry the insert; the active index does not cover FAILED rows.
            }
        }
        // Retries exhausted against a pathologically repeating FAILED-winner race - one final,
        // unguarded attempt, identical to a normal first-time call.
        return insertAndDrive(vacancyId, effectiveSourceState);
    }

    private PrepareApplicationPackageOutcome insertAndDrive(UUID vacancyId, EffectiveSourceState effectiveSourceState) {
        ApplicationMaterialGeneration requested = ApplicationMaterialGeneration.requestNew(
                vacancyId, effectiveSourceState.candidateProfileVersion(), effectiveSourceState.careerHistoryVersion(),
                effectiveSourceState.sourceFingerprint(), Instant.now(clock));
        ApplicationMaterialGeneration created = generationRepositoryPort.save(requested);
        log.info("Created new application material generation {} for vacancy {}", created.id(), vacancyId);
        return driveGeneration(created);
    }

    private PrepareApplicationPackageOutcome driveGeneration(ApplicationMaterialGeneration pending) {
        GenerateApplicationMaterialsOutcome outcome = generateApplicationMaterialsUseCase.generate(pending.id());
        return switch (outcome.status()) {
            case COMPLETED -> {
                log.info("Application material generation {} completed", outcome.generation().id());
                yield renderAndLoad(outcome.generation(), false);
            }
            case ALREADY_COMPLETED -> {
                log.info("Application material generation {} was already completed by a concurrent request", outcome.generation().id());
                yield renderAndLoad(outcome.generation(), true);
            }
            case ALREADY_IN_PROGRESS -> {
                log.info("Application material generation {} is in progress (lost a concurrent start race)", outcome.generation().id());
                yield new PrepareApplicationPackageOutcome.AlreadyInProgress(outcome.generation().id());
            }
            case ALREADY_FAILED, FAILED -> {
                log.warn("Application material generation {} is failed (failureCode={})",
                        outcome.generation().id(), outcome.generation().failureCode());
                yield new PrepareApplicationPackageOutcome.Failed(
                        outcome.generation().id(), failureReasonFor(outcome.generation().failureCode()));
            }
        };
    }

    private PrepareApplicationPackageOutcome renderAndLoad(ApplicationMaterialGeneration generation, boolean reused) {
        RenderApplicationMaterialsResult renderResult;
        try {
            renderResult = renderApplicationMaterialsUseCase.render(generation.id());
        } catch (RenderApplicationMaterialsException e) {
            log.error("Failed to render application materials for generation {}", generation.id(), e);
            return new PrepareApplicationPackageOutcome.Failed(generation.id(), failureReasonFor(e.reason()));
        }

        try {
            PreparedDocument cv = loadDocument(renderResult.cv());
            PreparedDocument coverLetter = loadDocument(renderResult.coverLetter());
            log.info("Application package prepared for generation {} (reused={})", generation.id(), reused);
            return new PrepareApplicationPackageOutcome.Prepared(
                    new PreparedApplicationPackage(generation.id(), reused, cv, coverLetter));
        } catch (FileStorageException e) {
            log.error("Failed to load application material artifact content for generation {}", generation.id(), e);
            return new PrepareApplicationPackageOutcome.Failed(generation.id(), ApplicationPackageFailureReason.DOCUMENT_DELIVERY_FAILED);
        }
    }

    private PreparedDocument loadDocument(ApplicationMaterialArtifact artifact) {
        StoredFileContent content = fileStoragePort.load(artifact.storageKey());
        return new PreparedDocument(artifact.fileName(), artifact.contentType(), content.content());
    }

    /**
     * Sprint 11 Big Block 7 (Part 11): maps a persisted {@link ApplicationMaterialsGenerationFailureCode}
     * name to the safe, Telegram-presentable {@link ApplicationPackageFailureReason} - the only place
     * that translation happens, so {@code ApplicationMaterialsGenerationFailureCode} (internal,
     * persistence-adjacent) never needs to be understood by the Telegram layer directly. An unknown/
     * unparseable code (should not happen for a code this application itself wrote) safely falls back
     * to {@link ApplicationPackageFailureReason#UNKNOWN}.
     */
    private ApplicationPackageFailureReason failureReasonFor(String failureCode) {
        if (failureCode == null) {
            return ApplicationPackageFailureReason.UNKNOWN;
        }
        try {
            return switch (ApplicationMaterialsGenerationFailureCode.valueOf(failureCode)) {
                case CANDIDATE_CONTEXT_NOT_CONFIGURED -> ApplicationPackageFailureReason.CANDIDATE_CONTEXT_NOT_CONFIGURED;
                case CANDIDATE_CONTEXT_VERSION_MISMATCH -> ApplicationPackageFailureReason.CANDIDATE_CONTEXT_VERSION_MISMATCH;
                case AI_PROVIDER_ERROR -> ApplicationPackageFailureReason.AI_PROVIDER_ERROR;
                case MALFORMED_AI_RESPONSE -> ApplicationPackageFailureReason.MALFORMED_AI_RESPONSE;
                case CV_TAILORING_VALIDATION_FAILED -> ApplicationPackageFailureReason.CV_TAILORING_VALIDATION_FAILED;
                case RESULT_VALIDATION_FAILED -> ApplicationPackageFailureReason.COVER_LETTER_VALIDATION_FAILED;
                case PERSISTENCE_FAILURE, STALE_IN_PROGRESS -> ApplicationPackageFailureReason.UNKNOWN;
            };
        } catch (IllegalArgumentException e) {
            return ApplicationPackageFailureReason.UNKNOWN;
        }
    }

    /** Sprint 11 Big Block 7 (Part 11): maps a {@link RenderApplicationMaterialsException.Reason} to the safe, Telegram-presentable category. */
    private ApplicationPackageFailureReason failureReasonFor(RenderApplicationMaterialsException.Reason reason) {
        return switch (reason) {
            case RENDERING_FAILED -> ApplicationPackageFailureReason.RENDERING_FAILED;
            case ATS_VERIFICATION_FAILED -> ApplicationPackageFailureReason.ATS_VERIFICATION_FAILED;
            case GENERATION_NOT_COMPLETED, SEMANTIC_RESULT_MISSING, STORAGE_FAILED,
                    STORAGE_CONTENT_CONFLICT, SNAPSHOT_PERSISTENCE_FAILED, ARTIFACT_METADATA_PERSISTENCE_FAILED ->
                    ApplicationPackageFailureReason.UNKNOWN;
        };
    }
}
