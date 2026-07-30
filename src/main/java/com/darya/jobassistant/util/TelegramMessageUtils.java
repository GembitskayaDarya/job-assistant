package com.darya.jobassistant.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class TelegramMessageUtils {

    public static final int MAX_MESSAGE_LENGTH = 4096;
    private static final String TRUNCATION_SUFFIX = "...";

    private static final Pattern MARKDOWN_V2_SPECIAL_CHARACTERS =
            Pattern.compile("[_*\\[\\]()~`>#+\\-=|{}.!\\\\]");

    private TelegramMessageUtils() {
    }

    public static String truncate(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= MAX_MESSAGE_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_MESSAGE_LENGTH - TRUNCATION_SUFFIX.length()) + TRUNCATION_SUFFIX;
    }

    /**
     * Truncates already-{@link #escapeMarkdownV2(String) escaped} text to at most {@code
     * maxLength} characters (marker included), cutting only at a safe boundary - never splitting a
     * UTF-16 surrogate pair, and never separating a MarkdownV2 escape backslash from the character
     * it escapes (the same two rules {@link #split(String)} already applies when hard-splitting a
     * single overlong line). Returns {@code text} unchanged when it already fits. Used by callers
     * that must bound one specific field's contribution to a single message rather than splitting
     * the whole message into multiple deliveries - see {@code CompactRecommendationTelegramFormatter}.
     */
    public static String truncateSafely(String escapedText, int maxLength, String marker) {
        if (escapedText == null) {
            return "";
        }
        if (escapedText.length() <= maxLength) {
            return escapedText;
        }
        int budget = Math.max(0, maxLength - marker.length());
        int end = Math.min(budget, escapedText.length());
        while (end > 0 && !isSafeCutPoint(escapedText, end)) {
            end--;
        }
        return escapedText.substring(0, end) + marker;
    }

    /**
     * Escapes every character MarkdownV2 treats as reserved syntax, per
     * <a href="https://core.telegram.org/bots/api#markdownv2-style">Telegram's MarkdownV2 spec</a>.
     * Telegram rejects the whole message if any reserved character appears unescaped,
     * even outside an intended formatting span - so this must be applied to all dynamic text.
     */
    public static String escapeMarkdownV2(String text) {
        if (text == null) {
            return "";
        }
        return MARKDOWN_V2_SPECIAL_CHARACTERS.matcher(text).replaceAll("\\\\$0");
    }

    /**
     * Splits already-assembled, already-escaped message text into an ordered list of chunks that
     * each fit within {@link #MAX_MESSAGE_LENGTH}, for delivery as separate Telegram messages when
     * a single message would be rejected as too long. Unlike {@link #truncate(String)}, no
     * content is ever dropped: every character of {@code text} appears in exactly one chunk, in
     * its original order.
     *
     * <p>Splits greedily at paragraph breaks ({@code "\n\n"}) first, packing as many whole
     * paragraphs as possible into each chunk. A paragraph that alone exceeds the limit is split
     * further at line breaks ({@code "\n"}); a single line that still exceeds the limit is
     * hard-split at a safe character boundary - one that never separates a MarkdownV2 escape
     * backslash from the character it escapes, and never separates the two {@code char}s of a
     * UTF-16 surrogate pair (e.g. an emoji) - as a last resort so pathologically long input can
     * never produce an invalid or dropped chunk.
     */
    public static List<String> split(String text) {
        if (text == null || text.length() <= MAX_MESSAGE_LENGTH) {
            return List.of(text == null ? "" : text);
        }
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : text.split("\n\n", -1)) {
            addUnit(chunks, current, paragraph, "\n\n");
        }
        flush(chunks, current);
        return chunks;
    }

    /**
     * Appends {@code unit} (a paragraph or a line) to {@code current}, preceded by
     * {@code separator} when {@code current} already has content, provided the result still fits
     * in one chunk. Otherwise flushes {@code current} first, then either starts a new chunk with
     * {@code unit} (if it fits alone) or splits {@code unit} one level finer: a paragraph
     * ({@code separator} is {@code "\n\n"}) splits into lines, a line ({@code separator} is
     * {@code "\n"}) hard-splits by character.
     */
    private static void addUnit(List<String> chunks, StringBuilder current, String unit, String separator) {
        String addition = current.isEmpty() ? unit : separator + unit;
        if (current.length() + addition.length() <= MAX_MESSAGE_LENGTH) {
            current.append(addition);
            return;
        }
        flush(chunks, current);
        if (unit.length() <= MAX_MESSAGE_LENGTH) {
            current.append(unit);
            return;
        }
        if (separator.equals("\n\n")) {
            for (String line : unit.split("\n", -1)) {
                addUnit(chunks, current, line, "\n");
            }
        } else {
            chunks.addAll(hardSplit(unit));
        }
    }

    private static void flush(List<String> chunks, StringBuilder current) {
        if (!current.isEmpty()) {
            chunks.add(current.toString());
            current.setLength(0);
        }
    }

    private static List<String> hardSplit(String text) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + MAX_MESSAGE_LENGTH, text.length());
            while (end > start && !isSafeCutPoint(text, end)) {
                end--;
            }
            if (end == start) {
                end = Math.min(start + MAX_MESSAGE_LENGTH, text.length());
            }
            parts.add(text.substring(start, end));
            start = end;
        }
        return parts;
    }

    private static boolean isSafeCutPoint(String text, int index) {
        if (index < text.length() && Character.isLowSurrogate(text.charAt(index))) {
            return false;
        }
        int backslashes = 0;
        int i = index - 1;
        while (i >= 0 && text.charAt(i) == '\\') {
            backslashes++;
            i--;
        }
        return backslashes % 2 == 0;
    }
}
