package com.darya.jobassistant.integrations.notifier.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.integrations.notifier.JobNotification;
import com.darya.jobassistant.telegram.format.JobAnalysisTelegramFormatter;
import com.darya.jobassistant.util.TelegramMessageUtils;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TelegramJobNotificationFormatterTest {

    private final JobAnalysisTelegramFormatter analysisFormatter = new JobAnalysisTelegramFormatter();
    private final TelegramJobNotificationFormatter formatter = new TelegramJobNotificationFormatter(analysisFormatter);
    private final UUID vacancyId = UUID.randomUUID();

    private final JobAnalysis representative = new JobAnalysis(
            82,
            List.of("Strong Java and Spring Boot experience", "Relevant Kafka production experience"),
            List.of("AWS proficiency may be below the level expected"),
            List.of("Kubernetes"),
            List.of("GraphQL"),
            "The vacancy requests 5+ years and the candidate has 6 years.",
            "Remote work from Poland appears compatible. The contract type is not stated.",
            "The candidate is a strong overall match with one notable infrastructure gap.");

    @Test
    void format_completeNotification_containsAllSections() {
        JobNotification notification = notification("Backend Engineer", "Acme Corp", "https://example.com/job-1", representative);

        String message = formatter.format(notification);

        assertThat(message).contains("Backend Engineer");
        assertThat(message).contains("Acme Corp");
        assertThat(message).contains("82%");
        assertThat(message).contains("Strong Java and Spring Boot experience");
        assertThat(message).contains("AWS proficiency may be below the level expected");
        assertThat(message).contains("Kubernetes");
        assertThat(message).contains("GraphQL");
        assertThat(message).contains(TelegramMessageUtils.escapeMarkdownV2("https://example.com/job-1"));
    }

    @Test
    void format_embedsTheExactSharedAnalysisBlockExactlyOnce() {
        JobNotification notification = notification("Backend Engineer", "Acme Corp", "https://example.com/job-1", representative);

        String message = formatter.format(notification);
        String sharedBlock = analysisFormatter.format(representative);

        assertThat(message).contains(sharedBlock);
        int firstIndex = message.indexOf(sharedBlock);
        int lastIndex = message.lastIndexOf(sharedBlock);
        assertThat(firstIndex).isEqualTo(lastIndex);
    }

    @Test
    void format_emptySections_renderCanonicalNoneValueInsteadOfBeingOmitted() {
        JobAnalysis empty = new JobAnalysis(
                50, List.of(), List.of(), List.of(), List.of(), "Not assessed.", "Not assessed.", "Summary");
        JobNotification notification = notification("Backend Engineer", "Acme Corp", "https://example.com/job-1", empty);

        String message = formatter.format(notification);

        assertThat(message)
                .contains("✅ Strengths\n• None")
                .contains("⚠️ Considerations\n• None")
                .contains("🧩 Missing Required Skills\n• None")
                .contains("🔧 Missing Preferred Skills\n• None");
    }

    @Test
    void format_preservesSourceOrderOfListItems() {
        JobAnalysis analysis = new JobAnalysis(
                85, List.of("First pro", "Second pro", "Third pro"), List.of(), List.of(), List.of(),
                "Not assessed.", "Not assessed.", "Summary");
        JobNotification notification = notification("Backend Engineer", "Acme Corp", "https://example.com/job-1", analysis);

        String message = formatter.format(notification);

        int firstIndex = message.indexOf("First pro");
        int secondIndex = message.indexOf("Second pro");
        int thirdIndex = message.indexOf("Third pro");
        assertThat(firstIndex).isLessThan(secondIndex);
        assertThat(secondIndex).isLessThan(thirdIndex);
    }

    @Test
    void format_specialCharactersInTitleCompanyAndAnalysisFields_areEscaped() {
        JobAnalysis analysis = new JobAnalysis(
                85, List.of("Loves *bold* text"), List.of("Uses `code` blocks"), List.of("C++ experience"), List.of(),
                "Not assessed.", "Not assessed.", "Great fit (highly recommended) #1!");
        JobNotification notification =
                notification("Senior_Engineer* [Backend]", "Acme.Corp!", "https://example.com/job-1", analysis);

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
        JobNotification notification = notification("Backend Engineer", "Acme Corp", url, representative);

        String message = formatter.format(notification);

        assertThat(message).contains(TelegramMessageUtils.escapeMarkdownV2(url));
    }

    /**
     * Message-length handling is a delivery concern (see {@code TelegramJobNotificationAdapter}
     * and {@code TelegramMessageUtils#split}), not a formatting one: the formatter must always
     * return the complete, unmodified text - however long - never a shortened or clamped
     * {@link JobAnalysis}. This is the Step 8.1 regression check.
     */
    @Test
    void format_oversizedAnalysis_isNeverTruncatedOrClampedByTheFormatter() {
        JobAnalysis oversized = oversizedAnalysis();
        JobNotification notification = notification("Backend Engineer", "Acme Corp", "https://example.com/job-1", oversized);

        String message = formatter.format(notification);

        assertThat(message.length()).isGreaterThan(TelegramMessageUtils.MAX_MESSAGE_LENGTH);
        assertThat(message).contains(analysisFormatter.format(oversized));
        // Every pro/con/missing-skill item survives complete and untruncated (no "..." cut).
        for (String pro : oversized.pros()) {
            assertThat(message).contains(pro);
        }
        for (String con : oversized.cons()) {
            assertThat(message).contains(con);
        }
        // Escaped, since MarkdownV2 requires it (the raw text contains ".") - not truncated.
        assertThat(message).contains(TelegramMessageUtils.escapeMarkdownV2(oversized.experienceAssessment()));
        assertThat(message).contains(TelegramMessageUtils.escapeMarkdownV2(oversized.preferencesAssessment()));
        assertThat(message).contains(TelegramMessageUtils.escapeMarkdownV2(oversized.summary()));
    }

    @Test
    void format_oversizedAnalysis_doesNotMutateTheSuppliedJobAnalysis() {
        JobAnalysis oversized = oversizedAnalysis();
        List<String> originalPros = oversized.pros();
        JobNotification notification = notification("Backend Engineer", "Acme Corp", "https://example.com/job-1", oversized);

        formatter.format(notification);

        assertThat(oversized.pros()).isEqualTo(originalPros);
        assertThat(notification.analysis()).isSameAs(oversized);
    }

    private JobAnalysis oversizedAnalysis() {
        List<String> longPros = java.util.stream.IntStream.range(0, 50)
                .mapToObj(i -> "Pro number " + i + " ".repeat(50))
                .toList();
        List<String> longCons = java.util.stream.IntStream.range(0, 50)
                .mapToObj(i -> "Con number " + i + " ".repeat(50))
                .toList();
        List<String> longMissingRequired = java.util.stream.IntStream.range(0, 20)
                .mapToObj(i -> "Missing required skill number " + i + " ".repeat(30))
                .toList();
        List<String> longMissingPreferred = java.util.stream.IntStream.range(0, 20)
                .mapToObj(i -> "Missing preferred skill number " + i + " ".repeat(30))
                .toList();
        return new JobAnalysis(
                85, longPros, longCons, longMissingRequired, longMissingPreferred,
                "Experience assessment. ".repeat(200),
                "Preferences assessment. ".repeat(200),
                "Summary. ".repeat(200));
    }

    @Test
    void format_doesNotIncludeAnyDescriptionBeyondFormattedFields() {
        JobNotification notification = notification("Backend Engineer", "Acme Corp", "https://example.com/job-1", representative);

        String message = formatter.format(notification);

        assertThat(message).doesNotContain("full description")
                .doesNotContain("Description:");
    }

    @Test
    void format_notificationOnlyMetadataNeverLeaksIntoTheSharedAnalysisBlock() {
        JobNotification notification = notification("Backend Engineer", "Acme Corp", "https://example.com/job-1", representative);
        String sharedBlock = analysisFormatter.format(representative);

        String message = formatter.format(notification);

        assertThat(sharedBlock).doesNotContain("Backend Engineer").doesNotContain("Acme Corp").doesNotContain("job-1");
        assertThat(message).contains(sharedBlock);
    }

    private JobNotification notification(String title, String companyName, String url, JobAnalysis analysis) {
        return new JobNotification(vacancyId, 12345L, title, companyName, url, analysis);
    }
}
