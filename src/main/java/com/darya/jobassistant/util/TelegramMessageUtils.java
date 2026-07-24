package com.darya.jobassistant.util;

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
}