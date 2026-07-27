package com.darya.jobassistant.integrations.notifier.telegram;

import com.darya.jobassistant.integrations.notifier.JobNotification;
import com.darya.jobassistant.util.TelegramMessageUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Formats a {@link JobNotification} into MarkdownV2 text for Telegram, matching the visual
 * style of the existing command formatter (emoji-prefixed sections, bullet lists). All dynamic
 * (external/AI-generated) values are escaped via {@link TelegramMessageUtils#escapeMarkdownV2}
 * so they can never inject Telegram markup.
 *
 * <p>If the fully rendered message exceeds {@link TelegramMessageUtils#MAX_MESSAGE_LENGTH}, a
 * deterministic reduction is applied in order: shorten individual pros/cons/missing-skill items,
 * then cap how many items per section are shown, then shorten the summary. Title, company,
 * match score and URL are never reduced. Every reduction truncates the raw (pre-escape) value,
 * so escaping afterwards can never split an escape sequence. As a last-resort safety net for
 * pathologically long required fields, the fully assembled (already escaped) message is
 * hard-truncated without ever cutting between a backslash and the character it escapes.
 */
@Component
public class TelegramJobNotificationFormatter {

    private static final int MAX_MESSAGE_LENGTH = TelegramMessageUtils.MAX_MESSAGE_LENGTH;
    private static final String ELLIPSIS = "...";
    private static final int ITEM_MAX_LENGTH = 100;
    private static final int MAX_ITEMS_PER_SECTION = 3;
    private static final int SUMMARY_MAX_LENGTH = 200;

    public String format(JobNotification notification) {
        String message = render(notification, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        if (fits(message)) {
            return message;
        }
        message = render(notification, ITEM_MAX_LENGTH, Integer.MAX_VALUE, Integer.MAX_VALUE);
        if (fits(message)) {
            return message;
        }
        message = render(notification, ITEM_MAX_LENGTH, MAX_ITEMS_PER_SECTION, Integer.MAX_VALUE);
        if (fits(message)) {
            return message;
        }
        message = render(notification, ITEM_MAX_LENGTH, MAX_ITEMS_PER_SECTION, SUMMARY_MAX_LENGTH);
        if (fits(message)) {
            return message;
        }
        return safeTruncate(message);
    }

    private boolean fits(String message) {
        return message.length() <= MAX_MESSAGE_LENGTH;
    }

    private String render(JobNotification n, int itemMaxLength, int maxItemsPerSection, int summaryMaxLength) {
        List<String> sections = new ArrayList<>();
        sections.add(formatHeader(n));
        sections.add(formatCompany(n));
        sections.add(formatMatch(n));
        sections.add(formatSummary(n, summaryMaxLength));
        addSectionIfPresent(sections, "✅ Strengths", n.pros(), itemMaxLength, maxItemsPerSection);
        addSectionIfPresent(sections, "⚠️ Considerations", n.cons(), itemMaxLength, maxItemsPerSection);
        addSectionIfPresent(sections, "🧩 Missing Required Skills", n.missingRequiredSkills(), itemMaxLength, maxItemsPerSection);
        addSectionIfPresent(sections, "🔧 Missing Preferred Skills", n.missingPreferredSkills(), itemMaxLength, maxItemsPerSection);
        sections.add(formatExperienceAssessment(n, summaryMaxLength));
        sections.add(formatPreferencesAssessment(n, summaryMaxLength));
        sections.add(formatLink(n));
        return String.join("\n\n", sections);
    }

    private String formatHeader(JobNotification n) {
        return "🔥 " + escape(n.title());
    }

    private String formatCompany(JobNotification n) {
        return "🏢 Company: " + escape(n.companyName());
    }

    private String formatMatch(JobNotification n) {
        return "⭐ Match: " + n.matchScore() + "%";
    }

    private String formatSummary(JobNotification n, int summaryMaxLength) {
        return "💬 Summary\n" + escape(truncateWord(n.summary(), summaryMaxLength));
    }

    private String formatExperienceAssessment(JobNotification n, int summaryMaxLength) {
        return "📈 Experience\n" + escape(truncateWord(n.experienceAssessment(), summaryMaxLength));
    }

    private String formatPreferencesAssessment(JobNotification n, int summaryMaxLength) {
        return "🧭 Preferences\n" + escape(truncateWord(n.preferencesAssessment(), summaryMaxLength));
    }

    private String formatLink(JobNotification n) {
        return "🔗 " + escape(n.url());
    }

    private void addSectionIfPresent(
            List<String> sections, String heading, List<String> items, int itemMaxLength, int maxItemsPerSection) {
        if (items.isEmpty()) {
            return;
        }
        List<String> limited = items.size() > maxItemsPerSection ? items.subList(0, maxItemsPerSection) : items;
        String body = limited.stream()
                .map(item -> "• " + escape(truncateWord(item, itemMaxLength)))
                .collect(Collectors.joining("\n"));
        sections.add(heading + "\n" + body);
    }

    private String truncateWord(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - ELLIPSIS.length())) + ELLIPSIS;
    }

    private String escape(String text) {
        return TelegramMessageUtils.escapeMarkdownV2(text);
    }

    private String safeTruncate(String text) {
        if (fits(text)) {
            return text;
        }
        int cutLength = MAX_MESSAGE_LENGTH - ELLIPSIS.length();
        while (cutLength > 0 && hasDanglingEscapeAt(text, cutLength)) {
            cutLength--;
        }
        return text.substring(0, cutLength) + ELLIPSIS;
    }

    private boolean hasDanglingEscapeAt(String text, int cutLength) {
        int backslashes = 0;
        int i = cutLength - 1;
        while (i >= 0 && text.charAt(i) == '\\') {
            backslashes++;
            i--;
        }
        return backslashes % 2 == 1;
    }
}
