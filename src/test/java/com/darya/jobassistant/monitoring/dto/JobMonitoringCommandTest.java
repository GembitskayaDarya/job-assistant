package com.darya.jobassistant.monitoring.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class JobMonitoringCommandTest {

    @Test
    void createsCommandWithValidValues() {
        JobMonitoringCommand command = new JobMonitoringCommand("java backend", 70, 5, 12345L);

        assertThat(command.keyword()).isEqualTo("java backend");
        assertThat(command.minScore()).isEqualTo(70);
        assertThat(command.maxNotifications()).isEqualTo(5);
        assertThat(command.recipientChatId()).isEqualTo(12345L);
    }

    @Test
    void rejectsNullKeyword() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JobMonitoringCommand(null, 70, 5, 12345L));
    }

    @Test
    void rejectsBlankKeyword() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JobMonitoringCommand("   ", 70, 5, 12345L));
    }

    @Test
    void rejectsMinScoreBelowRange() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JobMonitoringCommand("java", -1, 5, 12345L));
    }

    @Test
    void rejectsMinScoreAboveRange() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JobMonitoringCommand("java", 101, 5, 12345L));
    }

    @Test
    void acceptsBoundaryScores() {
        assertThat(new JobMonitoringCommand("java", 0, 5, 12345L).minScore()).isZero();
        assertThat(new JobMonitoringCommand("java", 100, 5, 12345L).minScore()).isEqualTo(100);
    }

    @Test
    void rejectsZeroMaxNotifications() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JobMonitoringCommand("java", 70, 0, 12345L));
    }

    @Test
    void rejectsNegativeMaxNotifications() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JobMonitoringCommand("java", 70, -1, 12345L));
    }

    @Test
    void rejectsNullRecipientChatId() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JobMonitoringCommand("java", 70, 5, null));
    }

    @Test
    void rejectsZeroRecipientChatId() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JobMonitoringCommand("java", 70, 5, 0L));
    }
}
