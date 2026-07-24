package com.darya.jobassistant.integrations.notifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JobNotificationTest {

    private final UUID vacancyId = UUID.randomUUID();
    private final List<String> pros = List.of("Java");
    private final List<String> cons = List.of("No Kafka experience");
    private final List<String> missingSkills = List.of("Kafka");

    @Test
    void validNotification_isAccepted() {
        JobNotification notification = new JobNotification(
                vacancyId, 12345L, "Backend Engineer", "Acme Corp", "https://example.com/job-1",
                85, "Strong match", pros, cons, missingSkills);

        assertThat(notification.vacancyId()).isEqualTo(vacancyId);
        assertThat(notification.recipientChatId()).isEqualTo(12345L);
        assertThat(notification.title()).isEqualTo("Backend Engineer");
        assertThat(notification.companyName()).isEqualTo("Acme Corp");
        assertThat(notification.url()).isEqualTo("https://example.com/job-1");
        assertThat(notification.matchScore()).isEqualTo(85);
        assertThat(notification.summary()).isEqualTo("Strong match");
        assertThat(notification.pros()).containsExactly("Java");
        assertThat(notification.cons()).containsExactly("No Kafka experience");
        assertThat(notification.missingSkills()).containsExactly("Kafka");
    }

    @Test
    void rejectsNullVacancyId() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                null, 12345L, "Backend Engineer", "Acme Corp", "https://example.com/job-1",
                85, "Strong match", pros, cons, missingSkills));
    }

    @Test
    void rejectsNullRecipientChatId() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                vacancyId, null, "Backend Engineer", "Acme Corp", "https://example.com/job-1",
                85, "Strong match", pros, cons, missingSkills));
    }

    @Test
    void rejectsZeroRecipientChatId() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                vacancyId, 0L, "Backend Engineer", "Acme Corp", "https://example.com/job-1",
                85, "Strong match", pros, cons, missingSkills));
    }

    @Test
    void rejectsBlankTitle() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                vacancyId, 12345L, "   ", "Acme Corp", "https://example.com/job-1",
                85, "Strong match", pros, cons, missingSkills));
    }

    @Test
    void rejectsBlankCompanyName() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                vacancyId, 12345L, "Backend Engineer", "   ", "https://example.com/job-1",
                85, "Strong match", pros, cons, missingSkills));
    }

    @Test
    void rejectsBlankUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                vacancyId, 12345L, "Backend Engineer", "Acme Corp", "   ",
                85, "Strong match", pros, cons, missingSkills));
    }

    @Test
    void rejectsScoreBelowZero() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                vacancyId, 12345L, "Backend Engineer", "Acme Corp", "https://example.com/job-1",
                -1, "Strong match", pros, cons, missingSkills));
    }

    @Test
    void rejectsScoreAboveHundred() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                vacancyId, 12345L, "Backend Engineer", "Acme Corp", "https://example.com/job-1",
                101, "Strong match", pros, cons, missingSkills));
    }

    @Test
    void acceptsBoundaryScores() {
        assertThat(new JobNotification(
                vacancyId, 12345L, "Backend Engineer", "Acme Corp", "https://example.com/job-1",
                0, "Strong match", pros, cons, missingSkills).matchScore()).isZero();
        assertThat(new JobNotification(
                vacancyId, 12345L, "Backend Engineer", "Acme Corp", "https://example.com/job-1",
                100, "Strong match", pros, cons, missingSkills).matchScore()).isEqualTo(100);
    }

    @Test
    void rejectsBlankSummary() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                vacancyId, 12345L, "Backend Engineer", "Acme Corp", "https://example.com/job-1",
                85, "   ", pros, cons, missingSkills));
    }

    @Test
    void rejectsNullProsList() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                vacancyId, 12345L, "Backend Engineer", "Acme Corp", "https://example.com/job-1",
                85, "Strong match", null, cons, missingSkills));
    }

    @Test
    void rejectsNullConsList() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                vacancyId, 12345L, "Backend Engineer", "Acme Corp", "https://example.com/job-1",
                85, "Strong match", pros, null, missingSkills));
    }

    @Test
    void rejectsNullMissingSkillsList() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                vacancyId, 12345L, "Backend Engineer", "Acme Corp", "https://example.com/job-1",
                85, "Strong match", pros, cons, null));
    }

    @Test
    void rejectsNullElementInProsList() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                vacancyId, 12345L, "Backend Engineer", "Acme Corp", "https://example.com/job-1",
                85, "Strong match", Arrays.asList("Java", null), cons, missingSkills));
    }

    @Test
    void rejectsBlankElementInConsList() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                vacancyId, 12345L, "Backend Engineer", "Acme Corp", "https://example.com/job-1",
                85, "Strong match", pros, List.of("Fine", "   "), missingSkills));
    }

    @Test
    void rejectsBlankElementInMissingSkillsList() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                vacancyId, 12345L, "Backend Engineer", "Acme Corp", "https://example.com/job-1",
                85, "Strong match", pros, cons, List.of("")));
    }

    @Test
    void defensivelyCopiesListsAndMutatingSourceDoesNotAffectNotification() {
        List<String> mutablePros = new ArrayList<>(List.of("Java"));

        JobNotification notification = new JobNotification(
                vacancyId, 12345L, "Backend Engineer", "Acme Corp", "https://example.com/job-1",
                85, "Strong match", mutablePros, cons, missingSkills);
        mutablePros.add("Spring");

        assertThat(notification.pros()).containsExactly("Java");
    }

    @Test
    void exposedListsCannotBeMutated() {
        JobNotification notification = new JobNotification(
                vacancyId, 12345L, "Backend Engineer", "Acme Corp", "https://example.com/job-1",
                85, "Strong match", pros, cons, missingSkills);

        assertThatThrownBy(() -> notification.pros().add("x")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> notification.cons().add("x")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> notification.missingSkills().add("x")).isInstanceOf(UnsupportedOperationException.class);
    }
}
