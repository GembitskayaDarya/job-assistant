package com.darya.jobassistant.vacancyrecommendation.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class VacancyRecommendationPropertiesTest {

    private static final Long RECIPIENT_CHAT_ID = 555L;
    private static final VacancyRecommendationProperties.Processing VALID_PROCESSING =
            new VacancyRecommendationProperties.Processing(
                    5, 3, Duration.ofMinutes(20), Duration.ofMinutes(10), Duration.ofHours(2));
    private static final VacancyRecommendationProperties.Scheduler DISABLED_SCHEDULER =
            new VacancyRecommendationProperties.Scheduler(
                    false, Duration.ofMinutes(10), Duration.ofMinutes(1), Duration.ofHours(1), Duration.ofSeconds(10));
    private static final VacancyRecommendationProperties.Scheduler ENABLED_SCHEDULER =
            new VacancyRecommendationProperties.Scheduler(
                    true, Duration.ofMinutes(10), Duration.ofMinutes(1), Duration.ofHours(1), Duration.ofSeconds(10));

    @Test
    void validEnabledConfiguration_isAccepted() {
        assertThatCode(() -> new VacancyRecommendationProperties(true, RECIPIENT_CHAT_ID, VALID_PROCESSING, DISABLED_SCHEDULER))
                .doesNotThrowAnyException();
    }

    @Test
    void disabled_doesNotRequireRecipientOrProcessingToBeValid() {
        assertThatCode(() -> new VacancyRecommendationProperties(false, null, null, null)).doesNotThrowAnyException();
        assertThatCode(() -> new VacancyRecommendationProperties(false, 0L,
                new VacancyRecommendationProperties.Processing(0, 0, Duration.ZERO, Duration.ZERO, Duration.ZERO), null))
                .doesNotThrowAnyException();
    }

    // --- enabled: recipientChatId ----------------------------------------------------------------

    @Test
    void enabled_rejectsNullRecipientChatId() {
        assertThatThrownBy(() -> new VacancyRecommendationProperties(true, null, VALID_PROCESSING, DISABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsZeroRecipientChatId() {
        assertThatThrownBy(() -> new VacancyRecommendationProperties(true, 0L, VALID_PROCESSING, DISABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- enabled: processing ---------------------------------------------------------------------

    @Test
    void enabled_rejectsNullProcessing() {
        assertThatThrownBy(() -> new VacancyRecommendationProperties(true, RECIPIENT_CHAT_ID, null, DISABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsBatchSizeBelowOne() {
        assertThatThrownBy(() -> new VacancyRecommendationProperties(true, RECIPIENT_CHAT_ID,
                new VacancyRecommendationProperties.Processing(0, 3, Duration.ofMinutes(20), Duration.ofMinutes(10), Duration.ofHours(2)),
                DISABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsBatchSizeAboveFifty() {
        assertThatThrownBy(() -> new VacancyRecommendationProperties(true, RECIPIENT_CHAT_ID,
                new VacancyRecommendationProperties.Processing(51, 3, Duration.ofMinutes(20), Duration.ofMinutes(10), Duration.ofHours(2)),
                DISABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_acceptsBatchSizeBoundaries() {
        assertThatCode(() -> new VacancyRecommendationProperties(true, RECIPIENT_CHAT_ID,
                new VacancyRecommendationProperties.Processing(1, 3, Duration.ofMinutes(20), Duration.ofMinutes(10), Duration.ofHours(2)),
                DISABLED_SCHEDULER))
                .doesNotThrowAnyException();
        assertThatCode(() -> new VacancyRecommendationProperties(true, RECIPIENT_CHAT_ID,
                new VacancyRecommendationProperties.Processing(50, 3, Duration.ofMinutes(20), Duration.ofMinutes(10), Duration.ofHours(2)),
                DISABLED_SCHEDULER))
                .doesNotThrowAnyException();
    }

    @Test
    void enabled_rejectsMaxAttemptsBelowOne() {
        assertThatThrownBy(() -> new VacancyRecommendationProperties(true, RECIPIENT_CHAT_ID,
                new VacancyRecommendationProperties.Processing(5, 0, Duration.ofMinutes(20), Duration.ofMinutes(10), Duration.ofHours(2)),
                DISABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsMaxAttemptsAboveTen() {
        assertThatThrownBy(() -> new VacancyRecommendationProperties(true, RECIPIENT_CHAT_ID,
                new VacancyRecommendationProperties.Processing(5, 11, Duration.ofMinutes(20), Duration.ofMinutes(10), Duration.ofHours(2)),
                DISABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_acceptsMaxAttemptsBoundaries() {
        assertThatCode(() -> new VacancyRecommendationProperties(true, RECIPIENT_CHAT_ID,
                new VacancyRecommendationProperties.Processing(5, 1, Duration.ofMinutes(20), Duration.ofMinutes(10), Duration.ofHours(2)),
                DISABLED_SCHEDULER))
                .doesNotThrowAnyException();
        assertThatCode(() -> new VacancyRecommendationProperties(true, RECIPIENT_CHAT_ID,
                new VacancyRecommendationProperties.Processing(5, 10, Duration.ofMinutes(20), Duration.ofMinutes(10), Duration.ofHours(2)),
                DISABLED_SCHEDULER))
                .doesNotThrowAnyException();
    }

    @Test
    void enabled_rejectsNullLeaseDuration() {
        assertThatThrownBy(() -> new VacancyRecommendationProperties(true, RECIPIENT_CHAT_ID,
                new VacancyRecommendationProperties.Processing(5, 3, null, Duration.ofMinutes(10), Duration.ofHours(2)),
                DISABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsLeaseDurationBelowOneMinute() {
        assertThatThrownBy(() -> new VacancyRecommendationProperties(true, RECIPIENT_CHAT_ID,
                new VacancyRecommendationProperties.Processing(5, 3, Duration.ofSeconds(59), Duration.ofMinutes(10), Duration.ofHours(2)),
                DISABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsLeaseDurationAboveTwoHours() {
        assertThatThrownBy(() -> new VacancyRecommendationProperties(true, RECIPIENT_CHAT_ID,
                new VacancyRecommendationProperties.Processing(5, 3, Duration.ofHours(2).plusSeconds(1), Duration.ofMinutes(10), Duration.ofHours(3)),
                DISABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_acceptsLeaseDurationBoundaries() {
        assertThatCode(() -> new VacancyRecommendationProperties(true, RECIPIENT_CHAT_ID,
                new VacancyRecommendationProperties.Processing(5, 3, Duration.ofMinutes(1), Duration.ofMinutes(10), Duration.ofHours(2)),
                DISABLED_SCHEDULER))
                .doesNotThrowAnyException();
        assertThatCode(() -> new VacancyRecommendationProperties(true, RECIPIENT_CHAT_ID,
                new VacancyRecommendationProperties.Processing(5, 3, Duration.ofHours(2), Duration.ofMinutes(10), Duration.ofHours(2)),
                DISABLED_SCHEDULER))
                .doesNotThrowAnyException();
    }

    @Test
    void enabled_rejectsNonPositiveRetryInitialDelay() {
        assertThatThrownBy(() -> new VacancyRecommendationProperties(true, RECIPIENT_CHAT_ID,
                new VacancyRecommendationProperties.Processing(5, 3, Duration.ofMinutes(20), Duration.ZERO, Duration.ofHours(2)),
                DISABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsNullRetryInitialDelay() {
        assertThatThrownBy(() -> new VacancyRecommendationProperties(true, RECIPIENT_CHAT_ID,
                new VacancyRecommendationProperties.Processing(5, 3, Duration.ofMinutes(20), null, Duration.ofHours(2)),
                DISABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsRetryMaxDelayBelowRetryInitialDelay() {
        assertThatThrownBy(() -> new VacancyRecommendationProperties(true, RECIPIENT_CHAT_ID,
                new VacancyRecommendationProperties.Processing(5, 3, Duration.ofMinutes(20), Duration.ofMinutes(10), Duration.ofMinutes(5)),
                DISABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_acceptsRetryMaxDelayEqualToRetryInitialDelay() {
        assertThatCode(() -> new VacancyRecommendationProperties(true, RECIPIENT_CHAT_ID,
                new VacancyRecommendationProperties.Processing(5, 3, Duration.ofMinutes(20), Duration.ofMinutes(10), Duration.ofMinutes(10)),
                DISABLED_SCHEDULER))
                .doesNotThrowAnyException();
    }

    @Test
    void enabled_rejectsRetryMaxDelayAboveTwentyFourHours() {
        assertThatThrownBy(() -> new VacancyRecommendationProperties(true, RECIPIENT_CHAT_ID,
                new VacancyRecommendationProperties.Processing(5, 3, Duration.ofMinutes(20), Duration.ofMinutes(10), Duration.ofHours(24).plusSeconds(1)),
                DISABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_acceptsRetryMaxDelayUpperBound() {
        assertThatCode(() -> new VacancyRecommendationProperties(true, RECIPIENT_CHAT_ID,
                new VacancyRecommendationProperties.Processing(5, 3, Duration.ofMinutes(20), Duration.ofMinutes(10), Duration.ofHours(24)),
                DISABLED_SCHEDULER))
                .doesNotThrowAnyException();
    }

    // --- scheduler -------------------------------------------------------------------------------

    @Test
    void schedulerDisabled_doesNotRequireItsOwnFieldsToBeValid() {
        VacancyRecommendationProperties.Scheduler invalidButDisabled =
                new VacancyRecommendationProperties.Scheduler(false, Duration.ZERO, Duration.ofMinutes(-1), null, null);
        assertThatCode(() -> new VacancyRecommendationProperties(true, RECIPIENT_CHAT_ID, VALID_PROCESSING, invalidButDisabled))
                .doesNotThrowAnyException();
    }

    @Test
    void schedulerNull_isAcceptedAsDisabled() {
        assertThatCode(() -> new VacancyRecommendationProperties(true, RECIPIENT_CHAT_ID, VALID_PROCESSING, null))
                .doesNotThrowAnyException();
    }

    @Test
    void schedulerEnabled_validConfiguration_isAccepted() {
        assertThatCode(() -> new VacancyRecommendationProperties(true, RECIPIENT_CHAT_ID, VALID_PROCESSING, ENABLED_SCHEDULER))
                .doesNotThrowAnyException();
    }

    @Test
    void schedulerEnabled_whileFeatureDisabled_failsClearly() {
        assertThatThrownBy(() -> new VacancyRecommendationProperties(false, RECIPIENT_CHAT_ID, VALID_PROCESSING, ENABLED_SCHEDULER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vacancy-recommendation.enabled=true");
    }

    @Test
    void schedulerEnabled_rejectsFixedDelayBelowOneMinute() {
        assertThatThrownBy(() -> new VacancyRecommendationProperties(true, RECIPIENT_CHAT_ID, VALID_PROCESSING,
                new VacancyRecommendationProperties.Scheduler(
                        true, Duration.ofSeconds(59), Duration.ofMinutes(1), Duration.ofHours(1), Duration.ofSeconds(10))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void schedulerEnabled_acceptsFixedDelayLowerBound() {
        assertThatCode(() -> new VacancyRecommendationProperties(true, RECIPIENT_CHAT_ID, VALID_PROCESSING,
                new VacancyRecommendationProperties.Scheduler(
                        true, Duration.ofMinutes(1), Duration.ofMinutes(1), Duration.ofHours(1), Duration.ofSeconds(10))))
                .doesNotThrowAnyException();
    }

    @Test
    void schedulerEnabled_rejectsNegativeInitialDelay() {
        assertThatThrownBy(() -> new VacancyRecommendationProperties(true, RECIPIENT_CHAT_ID, VALID_PROCESSING,
                new VacancyRecommendationProperties.Scheduler(
                        true, Duration.ofMinutes(10), Duration.ofMinutes(-1), Duration.ofHours(1), Duration.ofSeconds(10))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void schedulerEnabled_acceptsZeroInitialDelay() {
        assertThatCode(() -> new VacancyRecommendationProperties(true, RECIPIENT_CHAT_ID, VALID_PROCESSING,
                new VacancyRecommendationProperties.Scheduler(
                        true, Duration.ofMinutes(10), Duration.ZERO, Duration.ofHours(1), Duration.ofSeconds(10))))
                .doesNotThrowAnyException();
    }

    @Test
    void schedulerEnabled_rejectsNegativeLockAtLeastFor() {
        assertThatThrownBy(() -> new VacancyRecommendationProperties(true, RECIPIENT_CHAT_ID, VALID_PROCESSING,
                new VacancyRecommendationProperties.Scheduler(
                        true, Duration.ofMinutes(10), Duration.ofMinutes(1), Duration.ofHours(1), Duration.ofSeconds(-1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void schedulerEnabled_rejectsLockAtMostForAtOrBelowLeaseDuration() {
        // leaseDuration in VALID_PROCESSING is 20 minutes - lockAtMostFor must exceed it.
        assertThatThrownBy(() -> new VacancyRecommendationProperties(true, RECIPIENT_CHAT_ID, VALID_PROCESSING,
                new VacancyRecommendationProperties.Scheduler(
                        true, Duration.ofMinutes(10), Duration.ofMinutes(1), Duration.ofMinutes(20), Duration.ofSeconds(10))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void schedulerEnabled_acceptsLockAtMostForJustAboveLeaseDuration() {
        assertThatCode(() -> new VacancyRecommendationProperties(true, RECIPIENT_CHAT_ID, VALID_PROCESSING,
                new VacancyRecommendationProperties.Scheduler(
                        true, Duration.ofMinutes(10), Duration.ofMinutes(1), Duration.ofMinutes(20).plusSeconds(1), Duration.ofSeconds(10))))
                .doesNotThrowAnyException();
    }

    @Test
    void schedulerEnabled_rejectsLockAtLeastForEqualToLockAtMostFor() {
        assertThatThrownBy(() -> new VacancyRecommendationProperties(true, RECIPIENT_CHAT_ID, VALID_PROCESSING,
                new VacancyRecommendationProperties.Scheduler(
                        true, Duration.ofMinutes(10), Duration.ofMinutes(1), Duration.ofHours(1), Duration.ofHours(1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void schedulerEnabled_rejectsLockAtLeastForAboveLockAtMostFor() {
        assertThatThrownBy(() -> new VacancyRecommendationProperties(true, RECIPIENT_CHAT_ID, VALID_PROCESSING,
                new VacancyRecommendationProperties.Scheduler(
                        true, Duration.ofMinutes(10), Duration.ofMinutes(1), Duration.ofMinutes(30), Duration.ofMinutes(31))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
