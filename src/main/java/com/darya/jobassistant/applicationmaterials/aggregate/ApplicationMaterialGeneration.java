package com.darya.jobassistant.applicationmaterials.aggregate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Framework-free domain aggregate for one application-material (tailored CV + cover letter)
 * generation attempt for a {@code Vacancy} - Sprint 10 Step 1's persistence foundation, backed by
 * V21's {@code application_material_generation} table. A Vacancy may have many generations over
 * time:
 *
 * <pre>{@code
 * Vacancy
 *     -> Generation #1
 *     -> Generation #2
 *     -> Generation #3
 * }</pre>
 *
 * so the generated CV/cover-letter content this eventually produces is never modeled as fields on
 * {@code Vacancy} itself - the user may regenerate materials later (e.g. after Candidate Profile
 * or Career History changes), and each attempt is its own row.
 *
 * <p>This step stores lifecycle and metadata only - {@link #candidateProfileVersion}/{@link
 * #careerHistoryVersion} record which Candidate Profile/Career History revision this attempt used
 * or was requested against (never the complete profile/history graph, which stays sourced from
 * {@code candidate_profile}/{@code career_history} - PostgreSQL remains the single source of
 * truth). No generated CV/cover-letter content, structured or otherwise, is modeled yet - that is
 * later Sprint 10 work, once the AI generation contract exists.
 *
 * <h2>{@link #sourceFingerprint} - the authoritative reuse-validity key (Sprint 11 production hardening)</h2>
 *
 * {@link #candidateProfileVersion}/{@link #careerHistoryVersion} alone could not detect every source
 * change that can affect generated materials - a production incident showed a Personal-Project-only
 * edit leaving a stale {@code COMPLETED} generation looking "current" forever, since neither version
 * field changes for that edit. {@link #sourceFingerprint} is {@code
 * applicationmaterials.preparation.ApplicationMaterialSourceFingerprint#sha256}'s output: a SHA-256
 * digest of the <em>complete</em> semantic source state (candidate profile facts, skills, education,
 * languages, Career History, Personal Projects, and the vacancy fields AI generation reads) at the
 * moment this generation was requested. {@code PrepareApplicationPackageUseCase} now decides reuse by
 * comparing this field alone, exact equality - never {@link #candidateProfileVersion}/{@link
 * #careerHistoryVersion}, which remain persisted purely as audit/debug metadata (see each field's own
 * javadoc) and are never again a competing definition of "is this generation current."
 *
 * <p>{@code null} for a generation created before this field existed ("legacy") - see {@link
 * #requestNew(UUID, long, Long, Instant)}. A legacy generation is deliberately never reusable: {@code
 * PrepareApplicationPackageUseCase}'s fingerprint-equality check can never match a {@code null}
 * stored value against a freshly computed non-null current fingerprint, so the first {@code
 * prepare()} call after this feature ships always creates a fresh, fingerprinted generation rather
 * than trusting old {@code candidateProfileVersion}/{@code careerHistoryVersion} coincidentally
 * matching. Every newly *requested* generation (via {@link #requestNew(UUID, long, Long, String,
 * Instant)}) always carries a real, non-blank fingerprint - enforced by this record's own compact
 * constructor whenever one is supplied, and by that factory method requiring a non-null argument.
 *
 * <h2>Lifecycle</h2>
 *
 * Legal transitions are {@code PENDING -> IN_PROGRESS -> COMPLETED} or {@code PENDING ->
 * IN_PROGRESS -> FAILED} (see {@link #start}/{@link #complete}/{@link #fail}). The compact
 * constructor enforces the invariant between {@link #status} and {@link #startedAt}/{@link
 * #completedAt}/{@link #failureCode} for every instance, constructed via a transition method or
 * not:
 *
 * <ul>
 *   <li>{@code PENDING}: {@code startedAt} null, {@code completedAt} null, {@code failureCode} null.
 *   <li>{@code IN_PROGRESS}: {@code startedAt} set, {@code completedAt} null, {@code failureCode} null.
 *   <li>{@code COMPLETED}: {@code startedAt} set, {@code completedAt} set, {@code failureCode} null.
 *   <li>{@code FAILED}: {@code startedAt} set, {@code completedAt} set, {@code failureCode} set.
 * </ul>
 *
 * <p>{@link #id} is {@code null} for a not-yet-persisted generation (see {@link #requestNew}).
 * {@link #version} is the optimistic-locking token a caller must supply unchanged from a prior
 * load to update that exact revision - see {@code ApplicationMaterialGenerationRepositoryPort#save}.
 *
 * <p>This step does not implement any background worker, atomic claim SQL, or retry policy -
 * {@link #start}/{@link #complete}/{@link #fail} are plain, synchronous state transitions a future
 * caller applies one at a time; concurrent claiming is later Sprint 10 work.
 */
public record ApplicationMaterialGeneration(
        UUID id,
        UUID vacancyId,
        ApplicationMaterialGenerationStatus status,
        long candidateProfileVersion,
        Long careerHistoryVersion,
        String sourceFingerprint,
        Instant requestedAt,
        Instant startedAt,
        Instant completedAt,
        String failureCode,
        String failureMessage,
        long version
) {

    public ApplicationMaterialGeneration {
        if (vacancyId == null) {
            throw new IllegalArgumentException("Application material generation vacancy id must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("Application material generation status must not be null");
        }
        if (candidateProfileVersion < 0) {
            throw new IllegalArgumentException("Application material generation candidate profile version must not be negative");
        }
        if (careerHistoryVersion != null && careerHistoryVersion < 0) {
            throw new IllegalArgumentException("Application material generation career history version must not be negative");
        }
        sourceFingerprint = blankToNull(sourceFingerprint);
        if (requestedAt == null) {
            throw new IllegalArgumentException("Application material generation requestedAt must not be null");
        }
        if (version < 0) {
            throw new IllegalArgumentException("Application material generation version must not be negative");
        }
        failureCode = blankToNull(failureCode);
        failureMessage = blankToNull(failureMessage);
        if (failureMessage != null && failureCode == null) {
            throw new IllegalArgumentException("Application material generation failureMessage requires a failureCode");
        }
        requireLifecycleMatchesStatus(status, startedAt, completedAt, failureCode);
        if (startedAt != null && startedAt.isBefore(requestedAt)) {
            throw new IllegalArgumentException("Application material generation startedAt must not precede requestedAt");
        }
        if (completedAt != null && startedAt != null && completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("Application material generation completedAt must not precede startedAt");
        }
    }

    /**
     * Legacy-shape convenience constructor matching this type's pre-fingerprint canonical
     * constructor (11 components, no {@link #sourceFingerprint}) - defaults it to {@code null}, so
     * every pre-existing direct-construction call site throughout this codebase's tests (lifecycle/
     * persistence/concurrency tests unrelated to reuse/fingerprint behavior) keeps compiling
     * unchanged. Mirrors {@link #requestNew(UUID, long, Long, Instant)}'s identical rationale.
     */
    public ApplicationMaterialGeneration(
            UUID id, UUID vacancyId, ApplicationMaterialGenerationStatus status,
            long candidateProfileVersion, Long careerHistoryVersion, Instant requestedAt,
            Instant startedAt, Instant completedAt, String failureCode, String failureMessage, long version) {
        this(id, vacancyId, status, candidateProfileVersion, careerHistoryVersion, null, requestedAt,
                startedAt, completedAt, failureCode, failureMessage, version);
    }

    /**
     * Creates a newly requested generation in {@link ApplicationMaterialGenerationStatus#PENDING},
     * carrying the authoritative {@code sourceFingerprint} that will decide this generation's future
     * reuse validity - the only domain-safe way to start one going forward. {@link #id} is {@code
     * null} (not yet persisted) and {@link #version} is {@code 0}, matching {@code
     * CandidateProfileAggregate}/{@code CareerHistoryAggregate}'s "not yet persisted" convention.
     *
     * @param careerHistoryVersion the Career History revision to associate, or {@code null} when
     *     no Career History exists for the candidate yet ({@code CareerHistoryAvailability.NOT_PROVIDED});
     *     audit metadata only - see this record's {@link #sourceFingerprint} javadoc
     * @param sourceFingerprint the complete-source-state fingerprint this generation was requested
     *     against - must not be null or blank; use {@code
     *     applicationmaterials.preparation.ApplicationMaterialSourceFingerprint#sha256} to compute it
     */
    public static ApplicationMaterialGeneration requestNew(
            UUID vacancyId, long candidateProfileVersion, Long careerHistoryVersion, String sourceFingerprint, Instant requestedAt) {
        if (sourceFingerprint == null || sourceFingerprint.isBlank()) {
            throw new IllegalArgumentException("Application material generation sourceFingerprint must not be null or blank when requesting a new generation");
        }
        return new ApplicationMaterialGeneration(
                null, vacancyId, ApplicationMaterialGenerationStatus.PENDING,
                candidateProfileVersion, careerHistoryVersion, sourceFingerprint, requestedAt,
                null, null, null, null, 0);
    }

    /**
     * Legacy convenience constructor matching this type's pre-fingerprint shape - defaults {@link
     * #sourceFingerprint} to {@code null}. Exists only so pre-existing test fixtures unrelated to
     * reuse/fingerprint behavior keep compiling; production code must always call {@link
     * #requestNew(UUID, long, Long, String, Instant)} with a real fingerprint - a generation created
     * through this overload is, by construction, a "legacy" row that {@code
     * PrepareApplicationPackageUseCase} can never consider reusable (see this record's own {@link
     * #sourceFingerprint} javadoc).
     */
    public static ApplicationMaterialGeneration requestNew(
            UUID vacancyId, long candidateProfileVersion, Long careerHistoryVersion, Instant requestedAt) {
        return new ApplicationMaterialGeneration(
                null, vacancyId, ApplicationMaterialGenerationStatus.PENDING,
                candidateProfileVersion, careerHistoryVersion, null, requestedAt,
                null, null, null, null, 0);
    }

    /**
     * Transitions a {@code PENDING} generation to {@code IN_PROGRESS}.
     *
     * @throws IllegalStateException if {@link #status} is not currently {@code PENDING}
     */
    public ApplicationMaterialGeneration start(Instant startedAt) {
        requireStatus(ApplicationMaterialGenerationStatus.PENDING, "start");
        return new ApplicationMaterialGeneration(
                id, vacancyId, ApplicationMaterialGenerationStatus.IN_PROGRESS,
                candidateProfileVersion, careerHistoryVersion, sourceFingerprint, requestedAt,
                startedAt, null, null, null, version);
    }

    /**
     * Transitions an {@code IN_PROGRESS} generation to {@code COMPLETED}.
     *
     * @throws IllegalStateException if {@link #status} is not currently {@code IN_PROGRESS}
     */
    public ApplicationMaterialGeneration complete(Instant completedAt) {
        requireStatus(ApplicationMaterialGenerationStatus.IN_PROGRESS, "complete");
        return new ApplicationMaterialGeneration(
                id, vacancyId, ApplicationMaterialGenerationStatus.COMPLETED,
                candidateProfileVersion, careerHistoryVersion, sourceFingerprint, requestedAt,
                startedAt, completedAt, null, null, version);
    }

    /**
     * Transitions an {@code IN_PROGRESS} generation to {@code FAILED} with safe failure metadata
     * only - never a stack trace, raw provider exception, token, or other sensitive/infrastructure
     * detail; that filtering is this method's caller's responsibility.
     *
     * @throws IllegalStateException if {@link #status} is not currently {@code IN_PROGRESS}
     */
    public ApplicationMaterialGeneration fail(String failureCode, String failureMessage, Instant completedAt) {
        requireStatus(ApplicationMaterialGenerationStatus.IN_PROGRESS, "fail");
        if (blankToNull(failureCode) == null) {
            throw new IllegalArgumentException("Application material generation failureCode must not be null or blank when failing");
        }
        return new ApplicationMaterialGeneration(
                id, vacancyId, ApplicationMaterialGenerationStatus.FAILED,
                candidateProfileVersion, careerHistoryVersion, sourceFingerprint, requestedAt,
                startedAt, completedAt, failureCode, failureMessage, version);
    }

    /**
     * Sprint 10 Step 6: true if this generation is {@code IN_PROGRESS} and has been so for at
     * least {@code staleTimeout} since it entered that state - the bounded recovery signal for a
     * process that transitioned this row to {@code IN_PROGRESS} and then crashed (or was killed)
     * before reaching {@code COMPLETED} or {@code FAILED}. {@link #startedAt} - never JVM process
     * start time, an in-memory timer, or Telegram request timing - is the only authoritative
     * timestamp: it is set exactly once, by {@link #start}, and never changes for the rest of this
     * row's life (see this record's own lifecycle invariants), so it faithfully answers "how long
     * has the abandoned attempt actually been running" regardless of which process or how many
     * requests observe it.
     *
     * <p>A {@code PENDING}/{@code COMPLETED}/{@code FAILED} generation is never stale - staleness
     * is a concept that only applies to unfinished, actively-claimed work.
     */
    public boolean isStaleInProgress(Instant now, Duration staleTimeout) {
        return status == ApplicationMaterialGenerationStatus.IN_PROGRESS
                && startedAt != null
                && startedAt.plus(staleTimeout).isBefore(now);
    }

    private void requireStatus(ApplicationMaterialGenerationStatus required, String action) {
        if (status != required) {
            throw new IllegalStateException(
                    "Cannot " + action + " application material generation '" + id + "' - status is " + status
                            + ", expected " + required);
        }
    }

    private static void requireLifecycleMatchesStatus(
            ApplicationMaterialGenerationStatus status, Instant startedAt, Instant completedAt, String failureCode) {
        boolean expectStarted = status != ApplicationMaterialGenerationStatus.PENDING;
        boolean expectCompleted = status == ApplicationMaterialGenerationStatus.COMPLETED
                || status == ApplicationMaterialGenerationStatus.FAILED;
        boolean expectFailureCode = status == ApplicationMaterialGenerationStatus.FAILED;

        if (expectStarted != (startedAt != null)) {
            throw new IllegalArgumentException(
                    "Application material generation status " + status + " requires startedAt "
                            + (expectStarted ? "to be set" : "to be null"));
        }
        if (expectCompleted != (completedAt != null)) {
            throw new IllegalArgumentException(
                    "Application material generation status " + status + " requires completedAt "
                            + (expectCompleted ? "to be set" : "to be null"));
        }
        if (expectFailureCode != (failureCode != null)) {
            throw new IllegalArgumentException(
                    "Application material generation status " + status + " requires failureCode "
                            + (expectFailureCode ? "to be set" : "to be null"));
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
