package com.darya.jobassistant.integrations.notifier.telegram;

import com.darya.jobassistant.integrations.notifier.JobNotification;
import com.darya.jobassistant.telegram.format.JobAnalysisTelegramFormatter;
import com.darya.jobassistant.util.TelegramMessageUtils;
import org.springframework.stereotype.Component;

/**
 * Formats a {@link JobNotification} into MarkdownV2 text for Telegram. The vacancy header
 * (title/company/link) is notification-specific; the analysis content itself is delegated to the
 * shared {@link JobAnalysisTelegramFormatter} - the same one {@code JobMessageFormatter} uses for
 * manual analysis - exactly once, with the notification's {@code analysis} value unmodified. This
 * guarantees the same {@link com.darya.jobassistant.ai.model.JobAnalysis} always produces the
 * exact same canonical block regardless of which workflow renders it: this formatter never
 * shortens, re-derives or otherwise builds a different {@code JobAnalysis} for presentation.
 *
 * <p>All dynamic (external/AI-generated) values are escaped via {@link
 * TelegramMessageUtils#escapeMarkdownV2} so they can never inject Telegram markup.
 *
 * <p>Telegram's message-length limit is a delivery concern, not a formatting one, so it is
 * deliberately not handled here - see {@code TelegramJobNotificationAdapter}, which splits the
 * text this formatter returns into multiple messages when necessary, rather than asking this
 * class to produce a shortened analysis.
 */
@Component
public class TelegramJobNotificationFormatter {

    private final JobAnalysisTelegramFormatter analysisFormatter;

    public TelegramJobNotificationFormatter(JobAnalysisTelegramFormatter analysisFormatter) {
        this.analysisFormatter = analysisFormatter;
    }

    public String format(JobNotification notification) {
        return String.join("\n\n",
                formatHeader(notification),
                formatCompany(notification),
                analysisFormatter.format(notification.analysis()),
                formatLink(notification));
    }

    private String formatHeader(JobNotification n) {
        return "🔥 " + escape(n.title());
    }

    private String formatCompany(JobNotification n) {
        return "🏢 Company: " + escape(n.companyName());
    }

    private String formatLink(JobNotification n) {
        return "🔗 " + escape(n.url());
    }

    private String escape(String text) {
        return TelegramMessageUtils.escapeMarkdownV2(text);
    }
}
