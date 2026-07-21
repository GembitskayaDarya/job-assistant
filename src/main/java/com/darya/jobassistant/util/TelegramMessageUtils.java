package com.darya.jobassistant.util;

public final class TelegramMessageUtils {

    private static final int MAX_MESSAGE_LENGTH = 4096;
    private static final String TRUNCATION_SUFFIX = "...";

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
}