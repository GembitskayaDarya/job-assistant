package com.darya.jobassistant.integrations.notifier.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.integrations.notifier.JobNotification;
import com.darya.jobassistant.util.TelegramMessageUtils;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TelegramJobNotificationFormatterTest {

    private final TelegramJobNotificationFormatter formatter = new TelegramJobNotificationFormatter();
    private final UUID vacancyId = UUID.randomUUID();

    @Test
    void format_completeNotification_containsAllSections() {
        JobNotification notification = notification(
                "Backend Engineer", "Acme Corp", "https://example.com/job-1", 85, "Strong overall match",
                List.of("Solid Java background"), List.of("No Kafka experience"), List.of("Kafka", "Kubernetes"));

        String message = formatter.format(notification);

        assertThat(message).contains("Backend Engineer");
        assertThat(message).contains("Acme Corp");
        assertThat(message).contains("85%");
        assertThat(message).contains("Strong overall match");
        assertThat(message).contains("Solid Java background");
        assertThat(message).contains("No Kafka experience");
        assertThat(message).contains("Kafka");
        assertThat(message).contains("Kubernetes");
        assertThat(message).contains(TelegramMessageUtils.escapeMarkdownV2("https://example.com/job-1"));
    }

    @Test
    void format_emptyProsSection_isOmitted() {
        JobNotification notification = notification(
                "Backend Engineer", "Acme Corp", "https://example.com/job-1", 85, "Summary",
                List.of(), List.of("Some con"), List.of("Some skill"));

        String message = formatter.format(notification);

        assertThat(message).doesNotContain("Strengths");
    }

    @Test
    void format_emptyConsSection_isOmitted() {
        JobNotification notification = notification(
                "Backend Engineer", "Acme Corp", "https://example.com/job-1", 85, "Summary",
                List.of("Some pro"), List.of(), List.of("Some skill"));

        String message = formatter.format(notification);

        assertThat(message).doesNotContain("Considerations");
    }

    @Test
    void format_emptyMissingSkillsSection_isOmitted() {
        JobNotification notification = notification(
                "Backend Engineer", "Acme Corp", "https://example.com/job-1", 85, "Summary",
                List.of("Some pro"), List.of("Some con"), List.of());

        String message = formatter.format(notification);

        assertThat(message).doesNotContain("Missing Skills");
    }

    @Test
    void format_allSectionsEmpty_omitsAllOptionalSections() {
        JobNotification notification = notification(
                "Backend Engineer", "Acme Corp", "https://example.com/job-1", 85, "Summary",
                List.of(), List.of(), List.of());

        String message = formatter.format(notification);

        assertThat(message).doesNotContain("Strengths");
        assertThat(message).doesNotContain("Considerations");
        assertThat(message).doesNotContain("Missing Skills");
        assertThat(message).contains("Backend Engineer");
        assertThat(message).contains(TelegramMessageUtils.escapeMarkdownV2("https://example.com/job-1"));
    }

    @Test
    void format_preservesSourceOrderOfListItems() {
        JobNotification notification = notification(
                "Backend Engineer", "Acme Corp", "https://example.com/job-1", 85, "Summary",
                List.of("First pro", "Second pro", "Third pro"), List.of(), List.of());

        String message = formatter.format(notification);

        int firstIndex = message.indexOf("First pro");
        int secondIndex = message.indexOf("Second pro");
        int thirdIndex = message.indexOf("Third pro");
        assertThat(firstIndex).isLessThan(secondIndex);
        assertThat(secondIndex).isLessThan(thirdIndex);
    }

    @Test
    void format_specialCharactersInTitleCompanySummaryAndListValues_areEscaped() {
        JobNotification notification = notification(
                "Senior_Engineer* [Backend]", "Acme.Corp!", "https://example.com/job-1", 85,
                "Great fit (highly recommended) #1!",
                List.of("Loves *bold* text"), List.of("Uses `code` blocks"), List.of("C++ experience"));

        String message = formatter.format(notification);

        assertThat(message).contains(TelegramMessageUtils.escapeMarkdownV2("Senior_Engineer* [Backend]"));
        assertThat(message).contains(TelegramMessageUtils.escapeMarkdownV2("Acme.Corp!"));
        assertThat(message).contains(TelegramMessageUtils.escapeMarkdownV2("Great fit (highly recommended) #1!"));
        assertThat(message).contains(TelegramMessageUtils.escapeMarkdownV2("Loves *bold* text"));
        assertThat(message).contains(TelegramMessageUtils.escapeMarkdownV2("Uses `code` blocks"));
        assertThat(message).contains(TelegramMessageUtils.escapeMarkdownV2("C++ experience"));
    }

    @Test
    void format_urlIsRenderedSafely() {
        String url = "https://example.com/jobs?id=123&ref=search-page.v2";
        JobNotification notification = notification(
                "Backend Engineer", "Acme Corp", url, 85, "Summary", List.of(), List.of(), List.of());

        String message = formatter.format(notification);

        assertThat(message).contains(TelegramMessageUtils.escapeMarkdownV2(url));
    }

    @Test
    void format_longNotification_isTruncatedToProviderLimit() {
        List<String> manyLongPros = java.util.stream.IntStream.range(0, 50)
                .mapToObj(i -> "Pro number " + i + " ".repeat(50))
                .toList();
        String longSummary = "S".repeat(3000);
        JobNotification notification = notification(
                "Backend Engineer", "Acme Corp", "https://example.com/job-1", 85, longSummary,
                manyLongPros, manyLongPros, manyLongPros);

        String message = formatter.format(notification);

        assertThat(message.length()).isLessThanOrEqualTo(TelegramMessageUtils.MAX_MESSAGE_LENGTH);
    }

    @Test
    void format_afterTruncation_stillContainsTitleCompanyScoreAndUrl() {
        List<String> manyLongPros = java.util.stream.IntStream.range(0, 50)
                .mapToObj(i -> "Pro number " + i + " ".repeat(50))
                .toList();
        String longSummary = "S".repeat(3000);
        JobNotification notification = notification(
                "Backend Engineer", "Acme Corp", "https://example.com/job-1", 85, longSummary,
                manyLongPros, manyLongPros, manyLongPros);

        String message = formatter.format(notification);

        assertThat(message).contains("Backend Engineer");
        assertThat(message).contains("Acme Corp");
        assertThat(message).contains("85%");
        assertThat(message).contains(TelegramMessageUtils.escapeMarkdownV2("https://example.com/job-1"));
    }

    @Test
    void format_extremelyLongRequiredFields_hardTruncationDoesNotLeaveDanglingEscape() {
        String hugeTitleWithBackslashes = ("Engineer\\_" + "x".repeat(10)).repeat(600);
        JobNotification notification = notification(
                hugeTitleWithBackslashes, "Acme Corp", "https://example.com/job-1", 85, "Summary",
                List.of(), List.of(), List.of());

        String message = formatter.format(notification);

        assertThat(message.length()).isLessThanOrEqualTo(TelegramMessageUtils.MAX_MESSAGE_LENGTH);
        int trailingBackslashes = 0;
        for (int i = message.length() - 1; i >= 0 && message.charAt(i) == '\\'; i--) {
            trailingBackslashes++;
        }
        assertThat(trailingBackslashes % 2).isZero();
    }

    @Test
    void format_doesNotIncludeAnyDescriptionBeyondFormattedFields() {
        JobNotification notification = notification(
                "Backend Engineer", "Acme Corp", "https://example.com/job-1", 85, "Short summary",
                List.of(), List.of(), List.of());

        String message = formatter.format(notification);

        assertThat(message).doesNotContain("full description")
                .doesNotContain("Description:");
    }

    private JobNotification notification(
            String title, String companyName, String url, int score, String summary,
            List<String> pros, List<String> cons, List<String> missingSkills) {
        return new JobNotification(vacancyId, 12345L, title, companyName, url, score, summary, pros, cons, missingSkills);
    }
}
