package com.darya.jobassistant.vacancyextraction.model;

import com.darya.jobassistant.integrations.jobsearch.SourceUrlValidator;
import java.net.URI;
import org.springframework.util.StringUtils;

/**
 * Provider-neutral input to {@code VacancyExtractionService}/{@code VacancyExtractionPort},
 * usable both by guided manual import (raw pasted text, no known source URL yet) and by a future
 * automatic-discovery caller (a Firecrawl-fetched {@code JobPageContent} plus, optionally, the
 * search result's title/snippet as low-confidence hints). Deliberately carries no Telegram,
 * Firecrawl, JPA, or Spring AI type - only what any extraction caller already has on hand.
 *
 * <p>{@link #sourceUrl} is nullable only because the pasted-description guided-import workflow
 * legitimately extracts before any URL is known (the user provides the URL in an earlier,
 * independent step - see {@code VacancyImportSession}). When present, it is validated with the
 * same shared, provider-neutral rule Firecrawl's {@code JobPageContent} already uses, rather than
 * a second copy of that check.
 */
public record VacancyExtractionRequest(URI sourceUrl, String content, String discoveredTitle, String discoveredSnippet) {

    public VacancyExtractionRequest {
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("content must not be blank");
        }
        if (sourceUrl != null) {
            SourceUrlValidator.requireValid(sourceUrl);
        }
        discoveredTitle = trimToNull(discoveredTitle);
        discoveredSnippet = trimToNull(discoveredSnippet);
    }

    /** The guided manual-import shape: pasted text, no source URL or search metadata yet. */
    public static VacancyExtractionRequest ofPastedDescription(String content) {
        return new VacancyExtractionRequest(null, content, null, null);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
