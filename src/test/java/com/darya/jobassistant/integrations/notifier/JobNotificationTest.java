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
    private final List<String> missingRequiredSkills = List.of("Kafka");
    private final List<String> missingPreferredSkills = List.of("Terraform");
    private final String experienceAssessment = "6 years vs. no stated requirement.";
    private final String preferencesAssessment = "Remote preference matches.";

    @Test
    void validNotification_isAccepted() {
        JobNotification notification = new JobNotification(
                vacancyId, 12345L, "Backend Engineer", "Acme Corp", "https://example.com/job-1",
                85, "Strong match", pros, cons, missingRequiredSkills, missingPreferredSkills,
                experienceAssessment, preferencesAssessment);

        assertThat(notification.vacancyId()).isEqualTo(vacancyId);
        assertThat(notification.recipientChatId()).isEqualTo(12345L);
        assertThat(notification.title()).isEqualTo("Backend Engineer");
        assertThat(notification.companyName()).isEqualTo("Acme Corp");
        assertThat(notification.url()).isEqualTo("https://example.com/job-1");
        assertThat(notification.matchScore()).isEqualTo(85);
        assertThat(notification.summary()).isEqualTo("Strong match");
        assertThat(notification.pros()).containsExactly("Java");
        assertThat(notification.cons()).containsExactly("No Kafka experience");
        assertThat(notification.missingRequiredSkills()).containsExactly("Kafka");
        assertThat(notification.missingPreferredSkills()).containsExactly("Terraform");
        assertThat(notification.experienceAssessment()).isEqualTo(experienceAssessment);
        assertThat(notification.preferencesAssessment()).isEqualTo(preferencesAssessment);
    }

    @Test
    void rejectsNullVacancyId() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                null, 12345L, "Backend Engineer", "Acme Corp", "https://example.com/job-1",
                85, "Strong match", pros, cons, missingRequiredSkills, missingPreferredSkills,
                experienceAssessment, preferencesAssessment));
    }

    @Test
    void rejectsNullRecipientChatId() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                vacancyId, null, "Backend Engineer", "Acme Corp", "https://example.com/job-1",
                85, "Strong match", pros, cons, missingRequiredSkills, missingPreferredSkills,
                experienceAssessment, preferencesAssessment));
    }

    @Test
    void rejectsZeroRecipientChatId() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                vacancyId, 0L, "Backend Engineer", "Acme Corp", "https://example.com/job-1",
                85, "Strong match", pros, cons, missingRequiredSkills, missingPreferredSkills,
                experienceAssessment, preferencesAssessment));
    }

    @Test
    void rejectsBlankTitle() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                vacancyId, 12345L, "   ", "Acme Corp", "https://example.com/job-1",
                85, "Strong match", pros, cons, missingRequiredSkills, missingPreferredSkills,
                experienceAssessment, preferencesAssessment));
    }

    @Test
    void rejectsBlankCompanyName() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                vacancyId, 12345L, "Backend Engineer", "   ", "https://example.com/job-1",
                85, "Strong match", pros, cons, missingRequiredSkills, missingPreferredSkills,
                experienceAssessment, preferencesAssessment));
    }

    @Test
    void rejectsBlankUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                vacancyId, 12345L, "Backend Engineer", "Acme Corp", "   ",
                85, "Strong match", pros, cons, missingRequiredSkills, missingPreferredSkills,
                experienceAssessment, preferencesAssessment));
    }

    @Test
    void rejectsScoreBelowZero() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                vacancyId, 12345L, "Backend Engineer", "Acme Corp", "https://example.com/job-1",
                -1, "Strong match", pros, cons, missingRequiredSkills, missingPreferredSkills,
                experienceAssessment, preferencesAssessment));
    }

    @Test
    void rejectsScoreAboveHundred() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                vacancyId, 12345L, "Backend Engineer", "Acme Corp", "https://example.com/job-1",
                101, "Strong match", pros, cons, missingRequiredSkills, missingPreferredSkills,
                experienceAssessment, preferencesAssessment));
    }

    @Test
    void acceptsBoundaryScores() {
        assertThat(new JobNotification(
                vacancyId, 12345L, "Backend Engineer", "Acme Corp", "https://example.com/job-1",
                0, "Strong match", pros, cons, missingRequiredSkills, missingPreferredSkills,
                experienceAssessment, preferencesAssessment).matchScore()).isZero();
        assertThat(new JobNotification(
                vacancyId, 12345L, "Backend Engineer", "Acme Corp", "https://example.com/job-1",
                100, "Strong match", pros, cons, missingRequiredSkills, missingPreferredSkills,
                experienceAssessment, preferencesAssessment).matchScore()).isEqualTo(100);
    }

    @Test
    void rejectsBlankSummary() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                vacancyId, 12345L, "Backend Engineer", "Acme Corp", "https://example.com/job-1",
                85, "   ", pros, cons, missingRequiredSkills, missingPreferredSkills,
                experienceAssessment, preferencesAssessment));
    }

    @Test
    void rejectsBlankExperienceAssessment() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                vacancyId, 12345L, "Backend Engineer", "Acme Corp", "https://example.com/job-1",
                85, "Strong match", pros, cons, missingRequiredSkills, missingPreferredSkills,
                "   ", preferencesAssessment));
    }

    @Test
    void rejectsBlankPreferencesAssessment() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                vacancyId, 12345L, "Backend Engineer", "Acme Corp", "https://example.com/job-1",
                85, "Strong match", pros, cons, missingRequiredSkills, missingPreferredSkills,
                experienceAssessment, "   "));
    }

    @Test
    void rejectsNullProsList() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                vacancyId, 12345L, "Backend Engineer", "Acme Corp", "https://example.com/job-1",
                85, "Strong match", null, cons, missingRequiredSkills, missingPreferredSkills,
                experienceAssessment, preferencesAssessment));
    }

    @Test
    void rejectsNullConsList() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                vacancyId, 12345L, "Backend Engineer", "Acme Corp", "https://example.com/job-1",
                85, "Strong match", pros, null, missingRequiredSkills, missingPreferredSkills,
                experienceAssessment, preferencesAssessment));
    }

    @Test
    void rejectsNullMissingRequiredSkillsList() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                vacancyId, 12345L, "Backend Engineer", "Acme Corp", "https://example.com/job-1",
                85, "Strong match", pros, cons, null, missingPreferredSkills,
                experienceAssessment, preferencesAssessment));
    }

    @Test
    void rejectsNullMissingPreferredSkillsList() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                vacancyId, 12345L, "Backend Engineer", "Acme Corp", "https://example.com/job-1",
                85, "Strong match", pros, cons, missingRequiredSkills, null,
                experienceAssessment, preferencesAssessment));
    }

    @Test
    void rejectsNullElementInProsList() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                vacancyId, 12345L, "Backend Engineer", "Acme Corp", "https://example.com/job-1",
                85, "Strong match", Arrays.asList("Java", null), cons, missingRequiredSkills, missingPreferredSkills,
                experienceAssessment, preferencesAssessment));
    }

    @Test
    void rejectsBlankElementInConsList() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                vacancyId, 12345L, "Backend Engineer", "Acme Corp", "https://example.com/job-1",
                85, "Strong match", pros, List.of("Fine", "   "), missingRequiredSkills, missingPreferredSkills,
                experienceAssessment, preferencesAssessment));
    }

    @Test
    void rejectsBlankElementInMissingRequiredSkillsList() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                vacancyId, 12345L, "Backend Engineer", "Acme Corp", "https://example.com/job-1",
                85, "Strong match", pros, cons, List.of(""), missingPreferredSkills,
                experienceAssessment, preferencesAssessment));
    }

    @Test
    void rejectsBlankElementInMissingPreferredSkillsList() {
        assertThatIllegalArgumentException().isThrownBy(() -> new JobNotification(
                vacancyId, 12345L, "Backend Engineer", "Acme Corp", "https://example.com/job-1",
                85, "Strong match", pros, cons, missingRequiredSkills, List.of(""),
                experienceAssessment, preferencesAssessment));
    }

    @Test
    void defensivelyCopiesListsAndMutatingSourceDoesNotAffectNotification() {
        List<String> mutablePros = new ArrayList<>(List.of("Java"));

        JobNotification notification = new JobNotification(
                vacancyId, 12345L, "Backend Engineer", "Acme Corp", "https://example.com/job-1",
                85, "Strong match", mutablePros, cons, missingRequiredSkills, missingPreferredSkills,
                experienceAssessment, preferencesAssessment);
        mutablePros.add("Spring");

        assertThat(notification.pros()).containsExactly("Java");
    }

    @Test
    void exposedListsCannotBeMutated() {
        JobNotification notification = new JobNotification(
                vacancyId, 12345L, "Backend Engineer", "Acme Corp", "https://example.com/job-1",
                85, "Strong match", pros, cons, missingRequiredSkills, missingPreferredSkills,
                experienceAssessment, preferencesAssessment);

        assertThatThrownBy(() -> notification.pros().add("x")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> notification.cons().add("x")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> notification.missingRequiredSkills().add("x")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> notification.missingPreferredSkills().add("x")).isInstanceOf(UnsupportedOperationException.class);
    }
}
