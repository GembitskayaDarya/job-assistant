package com.darya.jobassistant.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TelegramMessageUtilsTest {

    @Test
    void escapesEveryMarkdownV2ReservedCharacter() {
        String input = "_*[]()~`>#+-=|{}.!";
        String expected = "\\_\\*\\[\\]\\(\\)\\~\\`\\>\\#\\+\\-\\=\\|\\{\\}\\.\\!";

        assertThat(TelegramMessageUtils.escapeMarkdownV2(input)).isEqualTo(expected);
    }

    @Test
    void escapesLiteralBackslash() {
        assertThat(TelegramMessageUtils.escapeMarkdownV2("a\\b")).isEqualTo("a\\\\b");
    }

    @Test
    void leavesNonReservedCharactersUntouched() {
        String input = "Hello World 123 $ % ^ & / ? \" '";

        assertThat(TelegramMessageUtils.escapeMarkdownV2(input)).isEqualTo(input);
    }

    @Test
    void returnsEmptyStringForNullInput() {
        assertThat(TelegramMessageUtils.escapeMarkdownV2(null)).isEmpty();
    }
}
