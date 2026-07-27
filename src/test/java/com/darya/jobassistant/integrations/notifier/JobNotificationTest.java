package com.darya.jobassistant.integrations.notifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.darya.jobassistant.ai.model.JobAnalysis;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JobNotificationTest {

    private final UUID vacancyId = UUID.randomUUID();
    private final JobAnalysis analysis = new JobAnalysis(
            85, List.of("Java"), List.of(), List.of("Kafka"), List.of("Terraform"),
            "6 years vs. no stated requirement.", "Remote preference matches.", "Strong match");

    @Test
    void validNotification_isAccepted() {
        JobNotification notification = new JobNotification(
                vacancyId, 12345L, "Backend Engineer", "Acme Corp", "https://example.com/job-1", analysis);

        assertThat(notification.vacancyId()).isEqualTo(vacancyId);
        assertThat(notification.recipientChatId()).isEqualTo(12345L);
        assertThat(notification.title()).isEqualTo("Backend Engineer");
        assertThat(notification.companyName()).isEqualTo("Acme Corp");
        assertThat(notification.url()).isEqualTo("https://example.com/job-1");
        assertThat(notification.analysis()).isSameAs(analysis);
    }

    @Test
    void rejectsNullVacancyId() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                null, 12345L, "Backend Engineer", "Acme Corp", "https://example.com/job-1", analysis));
    }

    @Test
    void rejectsNullRecipientChatId() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                vacancyId, null, "Backend Engineer", "Acme Corp", "https://example.com/job-1", analysis));
    }

    @Test
    void rejectsZeroRecipientChatId() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                vacancyId, 0L, "Backend Engineer", "Acme Corp", "https://example.com/job-1", analysis));
    }

    @Test
    void rejectsBlankTitle() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                vacancyId, 12345L, "   ", "Acme Corp", "https://example.com/job-1", analysis));
    }

    @Test
    void rejectsBlankCompanyName() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                vacancyId, 12345L, "Backend Engineer", "   ", "https://example.com/job-1", analysis));
    }

    @Test
    void rejectsBlankUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                vacancyId, 12345L, "Backend Engineer", "Acme Corp", "   ", analysis));
    }

    @Test
    void rejectsNullAnalysis() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                vacancyId, 12345L, "Backend Engineer", "Acme Corp", "https://example.com/job-1", null));
    }
}
