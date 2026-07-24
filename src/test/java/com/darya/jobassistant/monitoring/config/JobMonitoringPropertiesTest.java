package com.darya.jobassistant.monitoring.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.monitoring.dto.JobMonitoringCommand;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class JobMonitoringPropertiesTest {

    private static final Duration FIXED_DELAY = Duration.ofMinutes(30);
    private static final Duration INITIAL_DELAY = Duration.ofMinutes(1);

    @Test
    void disabledMonitoring_startsWithoutRecipientChatId() {
        assertThatCode(() -> properties(false, FIXED_DELAY, INITIAL_DELAY, "java backend", 70, 5, null))
                .doesNotThrowAnyException();
    }

    @Test
    void enabledMonitoring_acceptsValidConfiguration() {
        assertThatCode(() -> properties(true, FIXED_DELAY, INITIAL_DELAY, "java backend", 70, 5, 12345L))
                .doesNotThrowAnyException();
    }

    @Test
    void enabledMonitoring_rejectsMissingRecipientChatId() {
        assertThatThrownBy(() -> properties(true, FIXED_DELAY, INITIAL_DELAY, "java backend", 70, 5, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabledMonitoring_rejectsZeroChatId() {
        assertThatThrownBy(() -> properties(true, FIXED_DELAY, INITIAL_DELAY, "java backend", 70, 5, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabledMonitoring_rejectsBlankKeyword() {
        assertThatThrownBy(() -> properties(true, FIXED_DELAY, INITIAL_DELAY, "   ", 70, 5, 12345L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabledMonitoring_rejectsScoreBelowZero() {
        assertThatThrownBy(() -> properties(true, FIXED_DELAY, INITIAL_DELAY, "java backend", -1, 5, 12345L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabledMonitoring_rejectsScoreAbove100() {
        assertThatThrownBy(() -> properties(true, FIXED_DELAY, INITIAL_DELAY, "java backend", 101, 5, 12345L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabledMonitoring_rejectsZeroMaxNotifications() {
        assertThatThrownBy(() -> properties(true, FIXED_DELAY, INITIAL_DELAY, "java backend", 70, 0, 12345L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabledMonitoring_rejectsNegativeMaxNotifications() {
        assertThatThrownBy(() -> properties(true, FIXED_DELAY, INITIAL_DELAY, "java backend", 70, -1, 12345L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabledMonitoring_rejectsZeroFixedDelay() {
        assertThatThrownBy(() -> properties(true, Duration.ZERO, INITIAL_DELAY, "java backend", 70, 5, 12345L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabledMonitoring_rejectsNegativeFixedDelay() {
        assertThatThrownBy(() -> properties(true, Duration.ofMinutes(-1), INITIAL_DELAY, "java backend", 70, 5, 12345L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabledMonitoring_rejectsNegativeInitialDelay() {
        assertThatThrownBy(() -> properties(true, FIXED_DELAY, Duration.ofMinutes(-1), "java backend", 70, 5, 12345L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabledMonitoring_acceptsZeroInitialDelay() {
        assertThatCode(() -> properties(true, FIXED_DELAY, Duration.ZERO, "java backend", 70, 5, 12345L))
                .doesNotThrowAnyException();
    }

    @Test
    void toCommand_mapsAllFieldsOntoJobMonitoringCommand() {
        JobMonitoringProperties properties = properties(true, FIXED_DELAY, INITIAL_DELAY, "java backend", 70, 5, 12345L);

        JobMonitoringCommand command = properties.toCommand();

        assertThat(command.keyword()).isEqualTo("java backend");
        assertThat(command.minScore()).isEqualTo(70);
        assertThat(command.maxNotifications()).isEqualTo(5);
        assertThat(command.recipientChatId()).isEqualTo(12345L);
    }

    private JobMonitoringProperties properties(boolean enabled, Duration fixedDelay, Duration initialDelay,
                                                String keyword, int minimumScore, int maxNotifications, Long recipientChatId) {
        return new JobMonitoringProperties(enabled, fixedDelay, initialDelay, keyword, minimumScore, maxNotifications, recipientChatId);
    }
}
