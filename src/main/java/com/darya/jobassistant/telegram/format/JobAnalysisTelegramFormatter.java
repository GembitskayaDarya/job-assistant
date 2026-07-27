package com.darya.jobassistant.telegram.format;

import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.util.TelegramMessageUtils;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Renders the one canonical Telegram presentation of a {@link JobAnalysis}'s content - score,
 * pros, cons, missing required/preferred skills, experience assessment, preferences assessment,
 * and summary, always in that order - shared by the manual {@code JobMessageFormatter} and the
 * monitoring {@code TelegramJobNotificationFormatter} so a candidate sees exactly the same
 * analysis block regardless of which workflow produced it.
 *
 * <p>Deliberately knows nothing about the vacancy (title, company, salary, URL), the notification
 * recipient, or persistence metadata (analysis version/origin/claims) - those stay each caller's
 * own responsibility. Every section is always rendered, even when a list is empty (as "None"),
 * so the two callers can never diverge on which sections appear.
 */
@Component
public class JobAnalysisTelegramFormatter {

    private static final String NONE = "None";

    public String format(JobAnalysis analysis) {
        return String.join("\n\n",
                formatScore(analysis),
                formatList("✅ Strengths", analysis.pros()),
                formatList("⚠️ Considerations", analysis.cons()),
                formatList("🧩 Missing Required Skills", analysis.missingRequiredSkills()),
                formatList("🔧 Missing Preferred Skills", analysis.missingPreferredSkills()),
                formatText("📈 Experience", analysis.experienceAssessment()),
                formatText("🧭 Preferences", analysis.preferencesAssessment()),
                formatText("💬 Summary", analysis.summary()));
    }

    private String formatScore(JobAnalysis analysis) {
        return "⭐ Match: " + analysis.score() + "%";
    }

    private String formatList(String heading, List<String> items) {
        return heading + "\n" + formatBulletList(items);
    }

    private String formatText(String heading, String text) {
        return heading + "\n" + escape(text);
    }

    private String formatBulletList(List<String> items) {
        if (items.isEmpty()) {
            return "• " + NONE;
        }
        return items.stream()
                .map(item -> "• " + escape(item))
                .collect(Collectors.joining("\n"));
    }

    private String escape(String text) {
        return TelegramMessageUtils.escapeMarkdownV2(text);
    }
}
