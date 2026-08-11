package com.darya.jobassistant.applicationmaterials.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApplicationMaterialGenerationTest {

    private final UUID vacancyId = UUID.randomUUID();
    private final Instant requestedAt = Instant.parse("2026-01-01T00:00:00Z");

    // ==================== requestNew ====================

    @Test
    void requestNew_returnsPendingGeneration_withNoStartedCompletedOrFailure() {
        ApplicationMaterialGeneration generation = ApplicationMaterialGeneration.requestNew(vacancyId, 3L, 7L, requestedAt);

        assertThat(generation.id()).isNull();
        assertThat(generation.vacancyId()).isEqualTo(vacancyId);
        assertThat(generation.status()).isEqualTo(ApplicationMaterialGenerationStatus.PENDING);
        assertThat(generation.candidateProfileVersion()).isEqualTo(3L);
        assertThat(generation.careerHistoryVersion()).isEqualTo(7L);
        assertThat(generation.requestedAt()).isEqualTo(requestedAt);
        assertThat(generation.startedAt()).isNull();
        assertThat(generation.completedAt()).isNull();
        assertThat(generation.failureCode()).isNull();
        assertThat(generation.failureMessage()).isNull();
        assertThat(generation.version()).isZero();
    }

    @Test
    void requestNew_withNullCareerHistoryVersion_isAllowed() {
        ApplicationMaterialGeneration generation = ApplicationMaterialGeneration.requestNew(vacancyId, 3L, null, requestedAt);

        assertThat(generation.careerHistoryVersion()).isNull();
    }

    // ==================== Constructor validation ====================

    @Test
    void constructor_nullVacancyId_isRejected() {
        assertThatThrownBy(() -> ApplicationMaterialGeneration.requestNew(null, 0L, null, requestedAt))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_negativeCandidateProfileVersion_isRejected() {
        assertThatThrownBy(() -> ApplicationMaterialGeneration.requestNew(vacancyId, -1L, null, requestedAt))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_negativeCareerHistoryVersion_isRejected() {
        assertThatThrownBy(() -> ApplicationMaterialGeneration.requestNew(vacancyId, 0L, -1L, requestedAt))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_nullRequestedAt_isRejected() {
        assertThatThrownBy(() -> ApplicationMaterialGeneration.requestNew(vacancyId, 0L, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_negativeVersion_isRejected() {
        assertThatThrownBy(() -> new ApplicationMaterialGeneration(
                        null, vacancyId, ApplicationMaterialGenerationStatus.PENDING, 0L, null, requestedAt, null, null, null, null, -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_failureMessageWithoutFailureCode_isRejected() {
        assertThatThrownBy(() -> new ApplicationMaterialGeneration(
                        null, vacancyId, ApplicationMaterialGenerationStatus.PENDING, 0L, null, requestedAt,
                        null, null, null, "some detail", 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== Lifecycle invariants per status ====================

    @Test
    void constructor_pendingWithStartedAt_isRejected() {
        assertThatThrownBy(() -> new ApplicationMaterialGeneration(
                        null, vacancyId, ApplicationMaterialGenerationStatus.PENDING, 0L, null, requestedAt,
                        requestedAt, null, null, null, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_inProgressWithoutStartedAt_isRejected() {
        assertThatThrownBy(() -> new ApplicationMaterialGeneration(
                        null, vacancyId, ApplicationMaterialGenerationStatus.IN_PROGRESS, 0L, null, requestedAt,
                        null, null, null, null, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_inProgressWithCompletedAt_isRejected() {
        Instant startedAt = requestedAt.plusSeconds(1);
        assertThatThrownBy(() -> new ApplicationMaterialGeneration(
                        null, vacancyId, ApplicationMaterialGenerationStatus.IN_PROGRESS, 0L, null, requestedAt,
                        startedAt, startedAt.plusSeconds(1), null, null, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_completedWithoutCompletedAt_isRejected() {
        Instant startedAt = requestedAt.plusSeconds(1);
        assertThatThrownBy(() -> new ApplicationMaterialGeneration(
                        null, vacancyId, ApplicationMaterialGenerationStatus.COMPLETED, 0L, null, requestedAt,
                        startedAt, null, null, null, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_completedWithFailureCode_isRejected() {
        Instant startedAt = requestedAt.plusSeconds(1);
        Instant completedAt = startedAt.plusSeconds(1);
        assertThatThrownBy(() -> new ApplicationMaterialGeneration(
                        null, vacancyId, ApplicationMaterialGenerationStatus.COMPLETED, 0L, null, requestedAt,
                        startedAt, completedAt, "SOME_CODE", null, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_failedWithoutFailureCode_isRejected() {
        Instant startedAt = requestedAt.plusSeconds(1);
        Instant completedAt = startedAt.plusSeconds(1);
        assertThatThrownBy(() -> new ApplicationMaterialGeneration(
                        null, vacancyId, ApplicationMaterialGenerationStatus.FAILED, 0L, null, requestedAt,
                        startedAt, completedAt, null, null, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_failedWithoutCompletedAt_isRejected() {
        Instant startedAt = requestedAt.plusSeconds(1);
        assertThatThrownBy(() -> new ApplicationMaterialGeneration(
                        null, vacancyId, ApplicationMaterialGenerationStatus.FAILED, 0L, null, requestedAt,
                        startedAt, null, "TIMEOUT", null, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_startedAtBeforeRequestedAt_isRejected() {
        Instant startedAt = requestedAt.minus(1, ChronoUnit.SECONDS);
        assertThatThrownBy(() -> new ApplicationMaterialGeneration(
                        null, vacancyId, ApplicationMaterialGenerationStatus.IN_PROGRESS, 0L, null, requestedAt,
                        startedAt, null, null, null, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_completedAtBeforeStartedAt_isRejected() {
        Instant startedAt = requestedAt.plusSeconds(10);
        Instant completedAt = startedAt.minusSeconds(1);
        assertThatThrownBy(() -> new ApplicationMaterialGeneration(
                        null, vacancyId, ApplicationMaterialGenerationStatus.COMPLETED, 0L, null, requestedAt,
                        startedAt, completedAt, null, null, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_validFailedGeneration_isAccepted() {
        Instant startedAt = requestedAt.plusSeconds(1);
        Instant completedAt = startedAt.plusSeconds(1);
        ApplicationMaterialGeneration generation = new ApplicationMaterialGeneration(
                UUID.randomUUID(), vacancyId, ApplicationMaterialGenerationStatus.FAILED, 0L, null, requestedAt,
                startedAt, completedAt, "AI_PROVIDER_ERROR", "The AI provider returned an error", 2L);

        assertThat(generation.status()).isEqualTo(ApplicationMaterialGenerationStatus.FAILED);
        assertThat(generation.failureCode()).isEqualTo("AI_PROVIDER_ERROR");
        assertThat(generation.failureMessage()).isEqualTo("The AI provider returned an error");
    }

    // ==================== Transitions ====================

    @Test
    void start_fromPending_returnsInProgressWithStartedAtSet() {
        ApplicationMaterialGeneration pending = ApplicationMaterialGeneration.requestNew(vacancyId, 1L, null, requestedAt);
        Instant startedAt = requestedAt.plusSeconds(5);

        ApplicationMaterialGeneration started = pending.start(startedAt);

        assertThat(started.status()).isEqualTo(ApplicationMaterialGenerationStatus.IN_PROGRESS);
        assertThat(started.startedAt()).isEqualTo(startedAt);
        assertThat(started.completedAt()).isNull();
        assertThat(started.failureCode()).isNull();
        assertThat(started.candidateProfileVersion()).isEqualTo(1L);
    }

    @Test
    void start_fromNonPending_isRejected() {
        ApplicationMaterialGeneration inProgress = ApplicationMaterialGeneration
                .requestNew(vacancyId, 1L, null, requestedAt)
                .start(requestedAt.plusSeconds(1));

        assertThatThrownBy(() -> inProgress.start(requestedAt.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void complete_fromInProgress_returnsCompletedWithCompletedAtSet() {
        ApplicationMaterialGeneration inProgress = ApplicationMaterialGeneration
                .requestNew(vacancyId, 1L, 2L, requestedAt)
                .start(requestedAt.plusSeconds(1));
        Instant completedAt = requestedAt.plusSeconds(2);

        ApplicationMaterialGeneration completed = inProgress.complete(completedAt);

        assertThat(completed.status()).isEqualTo(ApplicationMaterialGenerationStatus.COMPLETED);
        assertThat(completed.completedAt()).isEqualTo(completedAt);
        assertThat(completed.failureCode()).isNull();
    }

    @Test
    void complete_fromPending_isRejected() {
        ApplicationMaterialGeneration pending = ApplicationMaterialGeneration.requestNew(vacancyId, 1L, null, requestedAt);

        assertThatThrownBy(() -> pending.complete(requestedAt.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fail_fromInProgress_returnsFailedWithFailureMetadataSet() {
        ApplicationMaterialGeneration inProgress = ApplicationMaterialGeneration
                .requestNew(vacancyId, 1L, null, requestedAt)
                .start(requestedAt.plusSeconds(1));
        Instant completedAt = requestedAt.plusSeconds(2);

        ApplicationMaterialGeneration failed = inProgress.fail("AI_PROVIDER_ERROR", "The AI provider returned an error", completedAt);

        assertThat(failed.status()).isEqualTo(ApplicationMaterialGenerationStatus.FAILED);
        assertThat(failed.completedAt()).isEqualTo(completedAt);
        assertThat(failed.failureCode()).isEqualTo("AI_PROVIDER_ERROR");
        assertThat(failed.failureMessage()).isEqualTo("The AI provider returned an error");
    }

    @Test
    void fail_fromPending_isRejected() {
        ApplicationMaterialGeneration pending = ApplicationMaterialGeneration.requestNew(vacancyId, 1L, null, requestedAt);

        assertThatThrownBy(() -> pending.fail("TIMEOUT", null, requestedAt.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fail_withBlankFailureCode_isRejected() {
        ApplicationMaterialGeneration inProgress = ApplicationMaterialGeneration
                .requestNew(vacancyId, 1L, null, requestedAt)
                .start(requestedAt.plusSeconds(1));

        assertThatThrownBy(() -> inProgress.fail("   ", null, requestedAt.plusSeconds(2)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void transitions_preserveIdVacancyIdAndVersion() {
        UUID id = UUID.randomUUID();
        ApplicationMaterialGeneration inProgress = new ApplicationMaterialGeneration(
                id, vacancyId, ApplicationMaterialGenerationStatus.IN_PROGRESS, 1L, null, requestedAt,
                requestedAt.plusSeconds(1), null, null, null, 4L);

        ApplicationMaterialGeneration completed = inProgress.complete(requestedAt.plusSeconds(2));

        assertThat(completed.id()).isEqualTo(id);
        assertThat(completed.vacancyId()).isEqualTo(vacancyId);
        assertThat(completed.version()).isEqualTo(4L);
    }

    // ==================== isStaleInProgress (Sprint 10 Step 6) ====================

    @Test
    void isStaleInProgress_pending_isNeverStale() {
        ApplicationMaterialGeneration pending = ApplicationMaterialGeneration.requestNew(vacancyId, 1L, null, requestedAt);

        assertThat(pending.isStaleInProgress(requestedAt.plus(Duration.ofDays(1)), Duration.ofMinutes(15))).isFalse();
    }

    @Test
    void isStaleInProgress_completed_isNeverStale() {
        ApplicationMaterialGeneration completed = ApplicationMaterialGeneration.requestNew(vacancyId, 1L, null, requestedAt)
                .start(requestedAt.plusSeconds(1))
                .complete(requestedAt.plusSeconds(2));

        assertThat(completed.isStaleInProgress(requestedAt.plus(Duration.ofDays(1)), Duration.ofMinutes(15))).isFalse();
    }

    @Test
    void isStaleInProgress_failed_isNeverStale() {
        ApplicationMaterialGeneration failed = ApplicationMaterialGeneration.requestNew(vacancyId, 1L, null, requestedAt)
                .start(requestedAt.plusSeconds(1))
                .fail("AI_PROVIDER_ERROR", "boom", requestedAt.plusSeconds(2));

        assertThat(failed.isStaleInProgress(requestedAt.plus(Duration.ofDays(1)), Duration.ofMinutes(15))).isFalse();
    }

    @Test
    void isStaleInProgress_inProgressWellWithinTimeout_isNotStale() {
        ApplicationMaterialGeneration inProgress =
                ApplicationMaterialGeneration.requestNew(vacancyId, 1L, null, requestedAt).start(requestedAt.plusSeconds(1));
        Instant now = requestedAt.plus(Duration.ofMinutes(5));

        assertThat(inProgress.isStaleInProgress(now, Duration.ofMinutes(15))).isFalse();
    }

    @Test
    void isStaleInProgress_inProgressPastTimeout_isStale() {
        ApplicationMaterialGeneration inProgress =
                ApplicationMaterialGeneration.requestNew(vacancyId, 1L, null, requestedAt).start(requestedAt.plusSeconds(1));
        Instant now = requestedAt.plusSeconds(1).plus(Duration.ofMinutes(15)).plusSeconds(1);

        assertThat(inProgress.isStaleInProgress(now, Duration.ofMinutes(15))).isTrue();
    }

    @Test
    void isStaleInProgress_exactlyAtTimeoutBoundary_isNotYetStale() {
        ApplicationMaterialGeneration inProgress =
                ApplicationMaterialGeneration.requestNew(vacancyId, 1L, null, requestedAt).start(requestedAt.plusSeconds(1));
        // now == startedAt + timeout exactly: isBefore is a strict comparison, so this instant
        // itself is the boundary and must not yet be reported as stale.
        Instant now = requestedAt.plusSeconds(1).plus(Duration.ofMinutes(15));

        assertThat(inProgress.isStaleInProgress(now, Duration.ofMinutes(15))).isFalse();
    }

    @Test
    void isStaleInProgress_oneMillisecondPastTimeoutBoundary_isStale() {
        ApplicationMaterialGeneration inProgress =
                ApplicationMaterialGeneration.requestNew(vacancyId, 1L, null, requestedAt).start(requestedAt.plusSeconds(1));
        Instant now = requestedAt.plusSeconds(1).plus(Duration.ofMinutes(15)).plusMillis(1);

        assertThat(inProgress.isStaleInProgress(now, Duration.ofMinutes(15))).isTrue();
    }
}
