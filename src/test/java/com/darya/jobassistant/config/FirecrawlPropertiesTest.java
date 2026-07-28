package com.darya.jobassistant.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class FirecrawlPropertiesTest {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    @Test
    void validEnabledConfiguration_isAccepted() {
        assertThatCode(() -> properties(true, "test-key", "https://api.firecrawl.dev", 10, CONNECT_TIMEOUT, READ_TIMEOUT))
                .doesNotThrowAnyException();
    }

    @Test
    void disabled_doesNotRequireOtherFieldsToBeValid() {
        assertThatCode(() -> properties(false, null, null, 0, null, null)).doesNotThrowAnyException();
    }

    @Test
    void enabled_rejectsBlankApiKey() {
        assertThatThrownBy(() -> properties(true, "  ", "https://api.firecrawl.dev", 10, CONNECT_TIMEOUT, READ_TIMEOUT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsNullApiKey() {
        assertThatThrownBy(() -> properties(true, null, "https://api.firecrawl.dev", 10, CONNECT_TIMEOUT, READ_TIMEOUT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsBlankBaseUrl() {
        assertThatThrownBy(() -> properties(true, "test-key", " ", 10, CONNECT_TIMEOUT, READ_TIMEOUT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsNonPositiveSearchLimit() {
        assertThatThrownBy(() -> properties(true, "test-key", "https://api.firecrawl.dev", 0, CONNECT_TIMEOUT, READ_TIMEOUT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsNullConnectTimeout() {
        assertThatThrownBy(() -> properties(true, "test-key", "https://api.firecrawl.dev", 10, null, READ_TIMEOUT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsNonPositiveConnectTimeout() {
        assertThatThrownBy(() -> properties(true, "test-key", "https://api.firecrawl.dev", 10, Duration.ZERO, READ_TIMEOUT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsNullReadTimeout() {
        assertThatThrownBy(() -> properties(true, "test-key", "https://api.firecrawl.dev", 10, CONNECT_TIMEOUT, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enabled_rejectsNonPositiveReadTimeout() {
        assertThatThrownBy(() -> properties(true, "test-key", "https://api.firecrawl.dev", 10, CONNECT_TIMEOUT, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toString_masksApiKey() {
        FirecrawlProperties properties = properties(true, "super-secret-key", "https://api.firecrawl.dev", 10, CONNECT_TIMEOUT, READ_TIMEOUT);

        assertThatCode(() -> {
            String rendered = properties.toString();
            if (rendered.contains("super-secret-key")) {
                throw new AssertionError("toString() must not expose the raw api key: " + rendered);
            }
        }).doesNotThrowAnyException();
    }

    private FirecrawlProperties properties(boolean enabled, String apiKey, String baseUrl, int searchLimit,
                                            Duration connectTimeout, Duration readTimeout) {
        return new FirecrawlProperties(enabled, apiKey, baseUrl, searchLimit, connectTimeout, readTimeout);
    }
}
