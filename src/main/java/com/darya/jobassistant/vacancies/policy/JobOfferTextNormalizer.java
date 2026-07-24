package com.darya.jobassistant.vacancies.policy;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Turns free-form job-offer text into lowercase alphanumeric tokens, stripping HTML markup and
 * treating punctuation (hyphens, slashes, whitespace) as word separators. Splitting "back-end"
 * and "back end" into the same ["back", "end"] tokens - while leaving "backend" as a single
 * token - is what lets {@link JavaBackendJobMatchPolicy} do word-aware phrase matching without a
 * regex word-boundary check, which would not by itself stop "java" from matching inside
 * "javascript".
 */
final class JobOfferTextNormalizer {

    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

    private JobOfferTextNormalizer() {
    }

    static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String withoutHtml = HTML_TAG.matcher(text).replaceAll(" ");
        String lower = withoutHtml.toLowerCase(Locale.ROOT);
        return Arrays.stream(NON_ALPHANUMERIC.split(lower))
                .filter(token -> !token.isBlank())
                .toList();
    }

    static List<String> tokenizeAll(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return tokenize(String.join(" ", values));
    }
}
