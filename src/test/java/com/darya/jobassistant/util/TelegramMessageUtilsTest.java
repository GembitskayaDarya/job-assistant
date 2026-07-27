package com.darya.jobassistant.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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

    @Test
    void split_textWithinLimit_returnsSingleUnchangedChunk() {
        String text = "Short message";

        List<String> chunks = TelegramMessageUtils.split(text);

        assertThat(chunks).containsExactly(text);
    }

    @Test
    void split_nullInput_returnsSingleEmptyChunk() {
        assertThat(TelegramMessageUtils.split(null)).containsExactly("");
    }

    @Test
    void split_oversizedText_everyChunkFitsWithinTheLimit() {
        String paragraph = "A".repeat(500);
        String text = String.join("\n\n", java.util.Collections.nCopies(20, paragraph));

        List<String> chunks = TelegramMessageUtils.split(text);

        assertThat(chunks).isNotEmpty();
        for (String chunk : chunks) {
            assertThat(chunk.length()).isLessThanOrEqualTo(TelegramMessageUtils.MAX_MESSAGE_LENGTH);
        }
    }

    @Test
    void split_oversizedText_preservesEveryParagraphExactlyOnceInOrder() {
        List<String> paragraphs = java.util.stream.IntStream.range(0, 30)
                .mapToObj(i -> "Paragraph " + i + ": " + "x".repeat(200))
                .toList();
        String text = String.join("\n\n", paragraphs);

        List<String> chunks = TelegramMessageUtils.split(text);
        String rejoined = String.join("", chunks);

        int previousIndex = -1;
        for (String paragraph : paragraphs) {
            int count = countOccurrences(rejoined, paragraph);
            assertThat(count).as("occurrences of '%s'", paragraph).isEqualTo(1);
            int index = rejoined.indexOf(paragraph);
            assertThat(index).isGreaterThan(previousIndex);
            previousIndex = index;
        }
    }

    @Test
    void split_paragraphLongerThanLimit_splitsByLinesWithoutDroppingContent() {
        List<String> lines = java.util.stream.IntStream.range(0, 100)
                .mapToObj(i -> "Line " + i + ": " + "y".repeat(50))
                .toList();
        String oversizedParagraph = String.join("\n", lines);

        List<String> chunks = TelegramMessageUtils.split(oversizedParagraph);
        String rejoined = String.join("", chunks);

        for (String chunk : chunks) {
            assertThat(chunk.length()).isLessThanOrEqualTo(TelegramMessageUtils.MAX_MESSAGE_LENGTH);
        }
        for (String line : lines) {
            assertThat(countOccurrences(rejoined, line)).isEqualTo(1);
        }
    }

    @Test
    void split_singleLineLongerThanLimit_hardSplitsWithoutDroppingOrDuplicatingCharacters() {
        String hugeLine = "z".repeat(10_000);

        List<String> chunks = TelegramMessageUtils.split(hugeLine);

        for (String chunk : chunks) {
            assertThat(chunk.length()).isLessThanOrEqualTo(TelegramMessageUtils.MAX_MESSAGE_LENGTH);
        }
        assertThat(String.join("", chunks)).isEqualTo(hugeLine);
    }

    @Test
    void split_hardSplitBoundary_neverSeparatesADanglingMarkdownV2Escape() {
        String textWithTrailingEscapePastTheBoundary =
                "a".repeat(TelegramMessageUtils.MAX_MESSAGE_LENGTH - 1) + "\\" + "!" + "b".repeat(100);

        List<String> chunks = TelegramMessageUtils.split(textWithTrailingEscapePastTheBoundary);

        for (String chunk : chunks) {
            int trailingBackslashes = 0;
            for (int i = chunk.length() - 1; i >= 0 && chunk.charAt(i) == '\\'; i--) {
                trailingBackslashes++;
            }
            assertThat(trailingBackslashes % 2).isZero();
        }
        assertThat(String.join("", chunks)).isEqualTo(textWithTrailingEscapePastTheBoundary);
    }

    @Test
    void split_hardSplitBoundary_neverSeparatesASurrogatePair() {
        String emoji = "🔥"; // 🔥, a single codepoint encoded as a UTF-16 surrogate pair
        String textWithEmojiAtTheBoundary = "a".repeat(TelegramMessageUtils.MAX_MESSAGE_LENGTH - 1) + emoji;

        List<String> chunks = TelegramMessageUtils.split(textWithEmojiAtTheBoundary);

        for (String chunk : chunks) {
            if (!chunk.isEmpty()) {
                assertThat(Character.isLowSurrogate(chunk.charAt(0))).isFalse();
                assertThat(Character.isHighSurrogate(chunk.charAt(chunk.length() - 1))).isFalse();
            }
        }
        assertThat(String.join("", chunks)).isEqualTo(textWithEmojiAtTheBoundary);
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
}
