package com.darya.jobassistant.applicationmaterials.preparation;

import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGeneration;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationActiveConflictException;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationRepositoryPort;
import com.darya.jobassistant.applicationmaterials.aggregate.ApplicationMaterialGenerationStatus;
import com.darya.jobassistant.applicationmaterials.artifact.aggregate.ApplicationMaterialArtifact;
import com.darya.jobassistant.applicationmaterials.generation.GenerateApplicationMaterialsOutcome;
import com.darya.jobassistant.applicationmaterials.generation.GenerateApplicationMaterialsUseCase;
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
import com.darya.jobassistant.vacancies.service.VacancyQueryService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
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
 * <h2>Reuse algorithm</h2>
 *
 * Reuse decisions are keyed on <em>both</em> the target Vacancy and the candidate's current
 * effective versions - {@link CandidateContextSnapshot#candidateProfileVersion()} and the current
 * Career History version (or absence thereof), read once per call via {@link
 * CandidateContextProvider#loadCurrentContext()}. {@link ApplicationMaterialGenerationRepositoryPort#findByVacancyId}
 * returns every generation for the Vacancy newest-requested-first; the first (i.e. most recent)
 * one whose recorded versions match the current ones is the candidate for reuse - older
 * generations, and any generation recorded against different versions, are never considered.
 *
 * <ul>
 *   <li>{@code COMPLETED}: reused outright - no {@link GenerateApplicationMaterialsUseCase#generate}
 *       call, so never another AI request. {@link RenderApplicationMaterialsUseCase#render} still
 *       runs to ensure artifacts exist, but it reuses persisted ones when present (Step 4) and
 *       makes no AI call itself.
 *   <li>{@code IN_PROGRESS}: reported as {@link PrepareApplicationPackageOutcome.AlreadyInProgress}
 *       immediately - no generation or AI call is started.
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
    private final CandidateContextProvider candidateContextProvider;
    private final ApplicationMaterialGenerationRepositoryPort generationRepositoryPort;
    private final GenerateApplicationMaterialsUseCase generateApplicationMaterialsUseCase;
    private final RenderApplicationMaterialsUseCase renderApplicationMaterialsUseCase;
    private final FileStoragePort fileStoragePort;
    private final Clock clock;

    /**
     * Bounds {@link #createOrJoinGeneration}'s retry loop after losing the V25 active-uniqueness
     * race - one retry resolves the overwhelmingly common case (the winner is now visible), a
     * second covers the narrow window where that winner itself completed/failed between our
     * conflict and our reload; this is a safety margin against a live-locked pathological case, not
     * a distributed lock or backoff policy.
     */
    private static final int MAX_ACTIVE_CONFLICT_ATTEMPTS = 3;

    public PrepareApplicationPackageOutcome prepare(UUID vacancyId) {
        log.info("Application package preparation requested for vacancy {}", vacancyId);

        try {
            vacancyQueryService.getById(vacancyId);
        } catch (VacancyNotFoundException e) {
            log.info("Application package preparation requested for unknown vacancy {}", vacancyId);
            return new PrepareApplicationPackageOutcome.VacancyNotFound(vacancyId);
        }

        CandidateContextSnapshot snapshot = candidateContextProvider.loadCurrentContext();
        long candidateProfileVersion = snapshot.candidateProfileVersion();
        Long careerHistoryVersion = snapshot.careerHistory().map(CareerHistoryAggregate::version).orElse(null);

        Optional<ApplicationMaterialGeneration> matching = findMatchingGeneration(vacancyId, candidateProfileVersion, careerHistoryVersion);
        if (matching.isPresent()) {
            return resolve(matching.get(), vacancyId, candidateProfileVersion, careerHistoryVersion);
        }

        return createOrJoinGeneration(vacancyId, candidateProfileVersion, careerHistoryVersion);
    }

    /**
     * Dispatches purely on {@code generation}'s current status - the one place both the initial
     * lookup and every post-conflict reload (see {@link #createOrJoinGeneration}) decide what to do
     * next, so the two call sites can never drift into different behavior for the same status.
     * {@code FAILED} is never reused/retried in place - a brand-new generation is requested
     * instead, exactly like the "no matching generation" case.
     */
    private PrepareApplicationPackageOutcome resolve(
            ApplicationMaterialGeneration generation, UUID vacancyId, long candidateProfileVersion, Long careerHistoryVersion) {
        return switch (generation.status()) {
            case COMPLETED -> {
                log.info("Reusing completed application material generation {} for vacancy {}", generation.id(), vacancyId);
                yield renderAndLoad(generation, true);
            }
            case IN_PROGRESS -> {
                log.info("Application material generation {} for vacancy {} is already in progress", generation.id(), vacancyId);
                yield new PrepareApplicationPackageOutcome.AlreadyInProgress(generation.id());
            }
            case PENDING -> {
                log.info("Continuing pending application material generation {} for vacancy {}", generation.id(), vacancyId);
                yield driveGeneration(generation);
            }
            case FAILED -> createOrJoinGeneration(vacancyId, candidateProfileVersion, careerHistoryVersion);
        };
    }

    private Optional<ApplicationMaterialGeneration> findMatchingGeneration(
            UUID vacancyId, long candidateProfileVersion, Long careerHistoryVersion) {
        List<ApplicationMaterialGeneration> generations = generationRepositoryPort.findByVacancyId(vacancyId);
        return generations.stream()
                .filter(g -> versionsMatch(g, candidateProfileVersion, careerHistoryVersion))
                .findFirst();
    }

    private boolean versionsMatch(ApplicationMaterialGeneration generation, long candidateProfileVersion, Long careerHistoryVersion) {
        return generation.candidateProfileVersion() == candidateProfileVersion
                && Objects.equals(generation.careerHistoryVersion(), careerHistoryVersion);
    }

    /**
     * Creates a brand-new {@code PENDING} generation and drives it forward. If V25's {@code
     * uk_amg_active_effective_key} rejects the insert - another request created the active
     * generation for this exact effective key first - reloads the winning row and, unless it is
     * itself {@code FAILED} (a narrow window where that winner completed/failed between our
     * conflict and our reload - V25 never blocks a retry against it, since FAILED is outside its
     * partial index), dispatches it via {@link #resolve} without ever creating a second row.
     * Bounded by {@link #MAX_ACTIVE_CONFLICT_ATTEMPTS} so a pathologically repeating race cannot
     * loop forever; the final attempt is unguarded, exactly like any single first-time call.
     */
    private PrepareApplicationPackageOutcome createOrJoinGeneration(
            UUID vacancyId, long candidateProfileVersion, Long careerHistoryVersion) {
        for (int attempt = 1; attempt < MAX_ACTIVE_CONFLICT_ATTEMPTS; attempt++) {
            try {
                return insertAndDrive(vacancyId, candidateProfileVersion, careerHistoryVersion);
            } catch (ApplicationMaterialGenerationActiveConflictException e) {
                log.info("Lost the active application material generation creation race for vacancy {} (attempt {})", vacancyId, attempt);
                ApplicationMaterialGeneration winner = findMatchingGeneration(vacancyId, candidateProfileVersion, careerHistoryVersion)
                        .orElseThrow(() -> new IllegalStateException(
                                "Lost an active application material generation creation race for vacancy '" + vacancyId
                                        + "' but no matching generation now exists"));
                if (winner.status() != ApplicationMaterialGenerationStatus.FAILED) {
                    // Never FAILED here, so resolve's own FAILED branch (which would call back into
                    // this method) cannot be taken - no risk of the attempt bound being reset.
                    return resolve(winner, vacancyId, candidateProfileVersion, careerHistoryVersion);
                }
                // winner is FAILED - loop and retry the insert; V25 does not cover FAILED rows.
            }
        }
        // Retries exhausted against a pathologically repeating FAILED-winner race - one final,
        // unguarded attempt, identical to a normal first-time call.
        return insertAndDrive(vacancyId, candidateProfileVersion, careerHistoryVersion);
    }

    private PrepareApplicationPackageOutcome insertAndDrive(UUID vacancyId, long candidateProfileVersion, Long careerHistoryVersion) {
        ApplicationMaterialGeneration requested =
                ApplicationMaterialGeneration.requestNew(vacancyId, candidateProfileVersion, careerHistoryVersion, Instant.now(clock));
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
                yield new PrepareApplicationPackageOutcome.Failed(outcome.generation().id());
            }
        };
    }

    private PrepareApplicationPackageOutcome renderAndLoad(ApplicationMaterialGeneration generation, boolean reused) {
        RenderApplicationMaterialsResult renderResult;
        try {
            renderResult = renderApplicationMaterialsUseCase.render(generation.id());
        } catch (RenderApplicationMaterialsException e) {
            log.error("Failed to render application materials for generation {}", generation.id(), e);
            return new PrepareApplicationPackageOutcome.Failed(generation.id());
        }

        try {
            PreparedDocument cv = loadDocument(renderResult.cv());
            PreparedDocument coverLetter = loadDocument(renderResult.coverLetter());
            log.info("Application package prepared for generation {} (reused={})", generation.id(), reused);
            return new PrepareApplicationPackageOutcome.Prepared(
                    new PreparedApplicationPackage(generation.id(), reused, cv, coverLetter));
        } catch (FileStorageException e) {
            log.error("Failed to load application material artifact content for generation {}", generation.id(), e);
            return new PrepareApplicationPackageOutcome.Failed(generation.id());
        }
    }

    private PreparedDocument loadDocument(ApplicationMaterialArtifact artifact) {
        StoredFileContent content = fileStoragePort.load(artifact.storageKey());
        return new PreparedDocument(artifact.fileName(), artifact.contentType(), content.content());
    }
}
