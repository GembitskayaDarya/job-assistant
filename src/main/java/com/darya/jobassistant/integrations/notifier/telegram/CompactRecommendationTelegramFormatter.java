package com.darya.jobassistant.integrations.notifier.telegram;

import com.darya.jobassistant.integrations.notifier.CompactVacancyRecommendation;
import com.darya.jobassistant.util.TelegramMessageUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Renders a {@link CompactVacancyRecommendation} into exactly one MarkdownV2 message that always
 * fits within {@link TelegramMessageUtils#MAX_MESSAGE_LENGTH} - the automatic
 * recommendation workflow's counterpart to {@code TelegramJobNotificationFormatter}/{@code
 * JobAnalysisTelegramFormatter} (which render the full, unbounded analysis for the legacy
 * monitoring workflow and rely on {@link TelegramJobNotificationAdapter}'s chunking instead).
 *
 * <p>Length validation happens on the final, already-{@link TelegramMessageUtils#escapeMarkdownV2
 * escaped} text - never on raw pre-escaped field values - since MarkdownV2 escaping can nearly
 * double a field's length (many URL/location characters, e.g. {@code . - = ! ~ # +}, are reserved
 * MarkdownV2 syntax and become two characters once escaped).
 *
 * <h2>Truncation priority</h2>
 *
 * Title, company, score, location, salary, the source link, and the {@code /analyze} id hint are
 * treated as essential identity fields and are never truncated (they are already bounded by this
 * project's own database column lengths - see {@code V1__init.sql}/{@code
 * V9__vacancy_location_remote_salary.sql} - so in ordinary operation they never threaten the
 * limit on their own). {@code reason}, {@code strengths}, and {@code risks} are AI-generated prose
 * this formatter bounds in three stages, each only entered if the previous stage still overflows:
 * <ol>
 *   <li>fixed per-field caps ({@value #REASON_MAX_CHARS} characters for {@code reason}; at most
 *       {@value #MAX_LIST_ITEMS} bullet items for {@code strengths}/{@code risks}, each capped at
 *       {@value #ITEM_MAX_CHARS} characters) - applied unconditionally, every time;</li>
 *   <li>if the capped message still overflows (an unusually long essential-field combination, e.g.
 *       a near-maximum-length URL and location together), sections are dropped in ascending
 *       priority order - {@code risks} first, then {@code strengths}, then {@code reason} is
 *       shrunk further (never fully removed) to whatever budget remains after the essential
 *       fields;</li>
 *   <li>if even the essential fields alone exceed the limit, {@link #format} returns {@link
 *       Optional#empty()} - {@link TelegramJobNotificationAdapter} maps this to {@link
 *       com.darya.jobassistant.integrations.notifier.JobNotificationFailureType#PAYLOAD_TOO_LARGE}
 *       without ever calling Telegram. No continuation/second message is ever generated.</li>
 * </ol>
 */
@Component
public class CompactRecommendationTelegramFormatter {

    static final int REASON_MAX_CHARS = 500;
    static final int MAX_LIST_ITEMS = 3;
    static final int ITEM_MAX_CHARS = 100;
    private static final String TRUNCATION_MARKER = "…";
    private static final String SECTION_SEPARATOR = "\n\n";

    public Optional<String> format(CompactVacancyRecommendation recommendation) {
        String essentialBlock = essentialBlock(recommendation);
        String reasonSection = boundedReasonSection(recommendation.reason());
        String strengthsSection = boundedListSection("✅ Strengths", recommendation.strengths());
        String risksSection = boundedListSection("⚠️ Risks", recommendation.risks());

        String withEverything = assemble(essentialBlock, reasonSection, strengthsSection, risksSection);
        if (fits(withEverything)) {
            return Optional.of(withEverything);
        }

        String withoutRisks = assemble(essentialBlock, reasonSection, strengthsSection, "");
        if (fits(withoutRisks)) {
            return Optional.of(withoutRisks);
        }

        String withoutRisksOrStrengths = assemble(essentialBlock, reasonSection, "", "");
        if (fits(withoutRisksOrStrengths)) {
            return Optional.of(withoutRisksOrStrengths);
        }

        String reasonOnlyBudget = shrinkReasonToFit(essentialBlock, recommendation.reason());
        if (reasonOnlyBudget != null) {
            String withShrunkReason = assemble(essentialBlock, reasonOnlyBudget, "", "");
            if (fits(withShrunkReason)) {
                return Optional.of(withShrunkReason);
            }
        }

        if (fits(essentialBlock)) {
            return Optional.of(essentialBlock);
        }
        return Optional.empty();
    }

    private boolean fits(String candidate) {
        return candidate.length() <= TelegramMessageUtils.MAX_MESSAGE_LENGTH;
    }

    private String essentialBlock(CompactVacancyRecommendation recommendation) {
        List<String> lines = new ArrayList<>();
        lines.add("🎯 " + escape(recommendation.title()));
        lines.add("🏢 " + escape(recommendation.companyName()));
        lines.add("⭐ Match: " + recommendation.score() + "%");
        addIfPresent(lines, "📍 ", combineLocation(recommendation));
        addIfPresent(lines, "💰 ", recommendation.salaryText());
        lines.add("🔗 " + escape(recommendation.url()));
        lines.add("🆔 /analyze " + escape(recommendation.vacancyId().toString()));
        return String.join("\n", lines);
    }

    private void addIfPresent(List<String> lines, String prefix, String rawValue) {
        if (StringUtils.hasText(rawValue)) {
            lines.add(prefix + escape(rawValue));
        }
    }

    /** Combines location and remote-mode into one line, e.g. "London (REMOTE)" or just one when only one is present. */
    private String combineLocation(CompactVacancyRecommendation recommendation) {
        boolean hasLocation = StringUtils.hasText(recommendation.location());
        boolean hasRemoteMode = StringUtils.hasText(recommendation.remoteMode());
        if (hasLocation && hasRemoteMode) {
            return recommendation.location() + " (" + recommendation.remoteMode() + ")";
        }
        if (hasLocation) {
            return recommendation.location();
        }
        if (hasRemoteMode) {
            return recommendation.remoteMode();
        }
        return null;
    }

    private String boundedReasonSection(String reason) {
        String escaped = escape(reason);
        String bounded = TelegramMessageUtils.truncateSafely(escaped, REASON_MAX_CHARS, TRUNCATION_MARKER);
        return "💬 " + bounded;
    }

    private String boundedListSection(String heading, List<String> items) {
        if (items.isEmpty()) {
            return "";
        }
        String bullets = items.stream()
                .limit(MAX_LIST_ITEMS)
                .map(item -> "• " + TelegramMessageUtils.truncateSafely(escape(item), ITEM_MAX_CHARS, TRUNCATION_MARKER))
                .collect(Collectors.joining("\n"));
        return heading + "\n" + bullets;
    }

    private String assemble(String essentialBlock, String reasonSection, String strengthsSection, String risksSection) {
        List<String> sections = new ArrayList<>();
        sections.add(essentialBlock);
        if (StringUtils.hasText(reasonSection)) {
            sections.add(reasonSection);
        }
        if (StringUtils.hasText(strengthsSection)) {
            sections.add(strengthsSection);
        }
        if (StringUtils.hasText(risksSection)) {
            sections.add(risksSection);
        }
        return String.join(SECTION_SEPARATOR, sections);
    }

    /** @return a shrunk, single-section reason string fit to whatever remains after essentialBlock, or null if no positive budget remains. */
    private String shrinkReasonToFit(String essentialBlock, String rawReason) {
        int budget = TelegramMessageUtils.MAX_MESSAGE_LENGTH - essentialBlock.length()
                - SECTION_SEPARATOR.length() - "💬 ".length();
        if (budget <= TRUNCATION_MARKER.length()) {
            return null;
        }
        String shrunk = TelegramMessageUtils.truncateSafely(escape(rawReason), budget, TRUNCATION_MARKER);
        return "💬 " + shrunk;
    }

    private String escape(String text) {
        return TelegramMessageUtils.escapeMarkdownV2(text);
    }
}
