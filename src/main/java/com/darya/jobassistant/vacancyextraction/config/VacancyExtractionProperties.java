package com.darya.jobassistant.vacancyextraction.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bounds for {@code VacancyExtractionContentPreparer}, the deterministic size cap applied to
 * vacancy content before it ever reaches an AI provider. Independent of {@code
 * FirecrawlProperties}/{@code firecrawl.enabled}: extraction serves guided manual import today,
 * which has nothing to do with Firecrawl, so this must bind and validate on its own regardless of
 * whether Firecrawl is enabled.
 */
@ConfigurationProperties(prefix = "vacancy-extraction")
public record VacancyExtractionProperties(int maxInputChars, int tailInputChars) {

    private static final int MAX_INPUT_CHARS_UPPER_BOUND = 100_000;

    public VacancyExtractionProperties {
        if (maxInputChars <= 0 || maxInputChars > MAX_INPUT_CHARS_UPPER_BOUND) {
            throw new IllegalArgumentException("vacancy-extraction.max-input-chars must be between 1 and "
                    + MAX_INPUT_CHARS_UPPER_BOUND + ", but was " + maxInputChars);
        }
        if (tailInputChars < 0) {
            throw new IllegalArgumentException(
                    "vacancy-extraction.tail-input-chars must not be negative, but was " + tailInputChars);
        }
        if (tailInputChars >= maxInputChars) {
            throw new IllegalArgumentException(
                    "vacancy-extraction.tail-input-chars must be strictly less than vacancy-extraction.max-input-chars, "
                            + "but was " + tailInputChars + " >= " + maxInputChars);
        }
    }
}
