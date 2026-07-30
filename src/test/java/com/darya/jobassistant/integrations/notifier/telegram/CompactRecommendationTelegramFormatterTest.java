package com.darya.jobassistant.integrations.notifier.telegram;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.integrations.notifier.CompactVacancyRecommendation;
import com.darya.jobassistant.util.TelegramMessageUtils;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Sprint 8 Step 11A.1: proves {@link CompactRecommendationTelegramFormatter} always produces at
 * most one message, bounded by the single {@link TelegramMessageUtils#MAX_MESSAGE_LENGTH} source
 * of truth, on the final MarkdownV2-escaped text - never the raw pre-escaped fields.
 */
class CompactRecommendationTelegramFormatterTest {

    private static final Long RECIPIENT_CHAT_ID = 555L;

    private final CompactRecommendationTelegramFormatter formatter = new CompactRecommendationTelegramFormatter();

    @Test
    void format_normalRecommendation_producesExactlyOneMessage() {
        CompactVacancyRecommendation recommendation = recommendation(
                "Backend Engineer", "Acme Corp", "https://example.com/job-1", 85,
                "Strong match for your Java/Kafka background.", List.of("Java", "Kafka"), List.of("No AWS experience mentioned"),
                "Berlin", "REMOTE", "120k-140k EUR");

        Optional<String> result = formatter.format(recommendation);

        assertThat(result).isPresent();
        assertThat(result.get().length()).isLessThanOrEqualTo(TelegramMessageUtils.MAX_MESSAGE_LENGTH);
    }

    @Test
    void format_worstCaseValidRecommendation_stillProducesOneMessage() {
        // DB-length-bounded worst case: title(255)/company(255)/location(300)/salary(200) all at
        // their real schema limits, url near its 1000-char bound, heavy with MarkdownV2 special
        // characters (many of which double in length once escaped).
        String title = "T".repeat(255);
        String company = "C".repeat(255);
        String url = "https://example.com/" + "a.b-c_d.".repeat(120);
        String location = "L".repeat(300);
        String salary = "S".repeat(200);
        CompactVacancyRecommendation recommendation = recommendation(
                title, company, url, 90,
                "Reason. ".repeat(200), manyItems(20), manyItems(20), location, "HYBRID", salary);

        Optional<String> result = formatter.format(recommendation);

        assertThat(result).isPresent();
        assertThat(result.get().length()).isLessThanOrEqualTo(TelegramMessageUtils.MAX_MESSAGE_LENGTH);
        assertThat(result.get()).contains(title).contains(company);
    }

    @Test
    void format_veryLongReason_isDeterministicallyTruncated() {
        String longReason = "This is a very long AI-generated reason. ".repeat(50);
        CompactVacancyRecommendation recommendation = recommendation(
                "Backend Engineer", "Acme Corp", "https://example.com/job-1", 85,
                longReason, List.of(), List.of(), null, null, null);

        Optional<String> first = formatter.format(recommendation);
        Optional<String> second = formatter.format(recommendation);

        assertThat(first).isPresent();
        assertThat(first).isEqualTo(second); // deterministic
        assertThat(first.get()).contains("…"); // truncation marker present
        assertThat(first.get().length()).isLessThanOrEqualTo(TelegramMessageUtils.MAX_MESSAGE_LENGTH);
    }

    @Test
    void format_veryLongStrengthsAndRisks_areDeterministicallyTruncated() {
        List<String> manyStrengths = manyItems(30);
        List<String> manyRisks = manyItems(30);
        CompactVacancyRecommendation recommendation = recommendation(
                "Backend Engineer", "Acme Corp", "https://example.com/job-1", 85,
                "Good match overall.", manyStrengths, manyRisks, null, null, null);

        Optional<String> result = formatter.format(recommendation);

        assertThat(result).isPresent();
        String message = result.get();
        // At most MAX_LIST_ITEMS bullets rendered per section - never all 30 raw items.
        long bulletCount = message.lines().filter(line -> line.startsWith("•")).count();
        assertThat(bulletCount).isLessThanOrEqualTo(2L * CompactRecommendationTelegramFormatter.MAX_LIST_ITEMS);
    }

    @Test
    void format_markdownV2Escaping_isIncludedInFinalSizeCalculation() {
        // Raw length is well below REASON_MAX_CHARS, but every character is a MarkdownV2 special
        // character (".") - once escaped, each becomes two characters ("\\."), roughly doubling
        // the length past the cap. If truncation measured the raw string instead, this would
        // never be truncated.
        String dotsOnly = ".".repeat(CompactRecommendationTelegramFormatter.REASON_MAX_CHARS - 10);
        CompactVacancyRecommendation recommendation = recommendation(
                "Backend Engineer", "Acme Corp", "https://example.com/job-1", 85,
                dotsOnly, List.of(), List.of(), null, null, null);

        Optional<String> result = formatter.format(recommendation);

        assertThat(result).isPresent();
        // Every literal "\." pair in the rendered reason section - if truncation had used the raw
        // (unescaped) length as its budget, the full un-truncated escaped text would appear,
        // doubling this section past REASON_MAX_CHARS escaped characters.
        int escapedDotOccurrences = countOccurrences(result.get(), "\\.");
        assertThat(escapedDotOccurrences).isLessThan(dotsOnly.length());
    }

    @Test
    void format_surrogatePairs_areNeverSplit() {
        // An emoji (surrogate pair) repeated to force the truncation cut point to land near one.
        String emojiHeavyReason = "🎯".repeat(400);
        CompactVacancyRecommendation recommendation = recommendation(
                "Backend Engineer", "Acme Corp", "https://example.com/job-1", 85,
                emojiHeavyReason, List.of(), List.of(), null, null, null);

        Optional<String> result = formatter.format(recommendation);

        assertThat(result).isPresent();
        String message = result.get();
        for (int i = 0; i < message.length(); i++) {
            if (Character.isHighSurrogate(message.charAt(i))) {
                assertThat(i + 1).isLessThan(message.length());
                assertThat(Character.isLowSurrogate(message.charAt(i + 1))).isTrue();
            }
        }
    }

    @Test
    void format_essentialFields_remainPresentRegardlessOfTruncation() {
        CompactVacancyRecommendation recommendation = recommendation(
                "Backend Engineer", "Acme Corp", "https://example.com/job-1", 77,
                "Reason text. ".repeat(200), manyItems(20), manyItems(20), null, null, null);

        Optional<String> result = formatter.format(recommendation);

        assertThat(result).isPresent();
        String message = result.get();
        assertThat(message).contains("Backend Engineer");
        assertThat(message).contains("Acme Corp");
        assertThat(message).contains("77%");
        assertThat(message).contains("example\\.com/job\\-1");
    }

    @Test
    void format_neverGeneratesAContinuationMessage() {
        // The method's own return type - Optional<String>, not List<String> - already makes a
        // second/continuation message structurally impossible; this test documents that intent.
        CompactVacancyRecommendation recommendation = recommendation(
                "Backend Engineer", "Acme Corp", "https://example.com/job-1", 85,
                "Reason. ".repeat(500), manyItems(50), manyItems(50), "Berlin", "REMOTE", "100k EUR");

        Optional<String> result = formatter.format(recommendation);

        assertThat(result).isPresent();
        assertThat(result.get()).isInstanceOf(String.class);
    }

    @Test
    void format_finalRenderedMessage_alwaysWithinSingleMessageLimit() {
        List<CompactVacancyRecommendation> cases = List.of(
                recommendation("Short", "Co", "https://x.co/1", 50, "ok", List.of(), List.of(), null, null, null),
                recommendation("Backend Engineer", "Acme Corp", "https://example.com/job-1", 85,
                        "Reason. ".repeat(300), manyItems(30), manyItems(30), "Berlin", "REMOTE", "100k EUR"));

        for (CompactVacancyRecommendation recommendation : cases) {
            Optional<String> result = formatter.format(recommendation);
            assertThat(result).isPresent();
            assertThat(result.get().length()).isLessThanOrEqualTo(TelegramMessageUtils.MAX_MESSAGE_LENGTH);
        }
    }

    @Test
    void sizeLimit_hasOneSourceOfTruth_behaviorTracksTelegramMessageUtilsConstant() {
        // Essential fields alone (no reason/strengths/risks contribution, since reason is a
        // required field but rendered as a tiny single word) sized to exactly straddle
        // TelegramMessageUtils.MAX_MESSAGE_LENGTH via a very long location field.
        int overhead = 200; // approximate space used by title/company/score/link/id/reason labels
        String justFits = "L".repeat(TelegramMessageUtils.MAX_MESSAGE_LENGTH - overhead - 1);
        String justOverflows = "L".repeat(TelegramMessageUtils.MAX_MESSAGE_LENGTH + 500);

        CompactVacancyRecommendation fits = recommendation(
                "T", "C", "https://x.co/1", 50, "ok", List.of(), List.of(), justFits, null, null);
        CompactVacancyRecommendation overflows = recommendation(
                "T", "C", "https://x.co/1", 50, "ok", List.of(), List.of(), justOverflows, null, null);

        assertThat(formatter.format(fits)).isPresent();
        assertThat(formatter.format(overflows)).isEmpty();
    }

    @Test
    void format_essentialFieldsAloneExceedLimit_returnsEmptyWithoutAnyMessage() {
        String hugeUrl = "https://example.com/" + "x".repeat(6000);
        CompactVacancyRecommendation recommendation = recommendation(
                "T", "C", hugeUrl, 50, "ok", List.of(), List.of(), null, null, null);

        Optional<String> result = formatter.format(recommendation);

        assertThat(result).isEmpty();
    }

    private int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private List<String> manyItems(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> "Item number " + i + " with some extra descriptive text to make it long")
                .toList();
    }

    private CompactVacancyRecommendation recommendation(
            String title, String company, String url, int score, String reason,
            List<String> strengths, List<String> risks, String location, String remoteMode, String salaryText) {
        return new CompactVacancyRecommendation(
                UUID.randomUUID(), RECIPIENT_CHAT_ID, title, company, url, score, reason,
                strengths, risks, location, remoteMode, salaryText);
    }
}
