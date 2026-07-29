package com.darya.jobassistant.vacancyextraction.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.Test;

class VacancyExtractionRequestTest {

    @Test
    void ofPastedDescription_hasNullSourceUrlAndHints() {
        VacancyExtractionRequest request = VacancyExtractionRequest.ofPastedDescription("Some vacancy text");

        assertThat(request.sourceUrl()).isNull();
        assertThat(request.content()).isEqualTo("Some vacancy text");
        assertThat(request.discoveredTitle()).isNull();
        assertThat(request.discoveredSnippet()).isNull();
    }

    @Test
    void rejectsBlankContent() {
        assertThatThrownBy(() -> new VacancyExtractionRequest(null, "   ", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullContent() {
        assertThatThrownBy(() -> new VacancyExtractionRequest(null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsNullSourceUrl() {
        assertThatCode(() -> new VacancyExtractionRequest(null, "content", null, null)).doesNotThrowAnyException();
    }

    @Test
    void acceptsValidAbsoluteSourceUrl() {
        URI url = URI.create("https://boards.example.com/jobs/123");
        VacancyExtractionRequest request = new VacancyExtractionRequest(url, "content", null, null);

        assertThat(request.sourceUrl()).isEqualTo(url);
    }

    @Test
    void rejectsRelativeSourceUrl() {
        assertThatThrownBy(() -> new VacancyExtractionRequest(URI.create("/jobs/123"), "content", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsSourceUrlWithUserInfo() {
        assertThatThrownBy(() -> new VacancyExtractionRequest(
                URI.create("https://user:pass@example.com/jobs/123"), "content", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankDiscoveredTitleAndSnippet_normalizeToNull() {
        VacancyExtractionRequest request = new VacancyExtractionRequest(null, "content", "   ", "  ");

        assertThat(request.discoveredTitle()).isNull();
        assertThat(request.discoveredSnippet()).isNull();
    }

    @Test
    void trimsDiscoveredTitleAndSnippet() {
        VacancyExtractionRequest request = new VacancyExtractionRequest(null, "content", "  Title  ", "  Snippet  ");

        assertThat(request.discoveredTitle()).isEqualTo("Title");
        assertThat(request.discoveredSnippet()).isEqualTo("Snippet");
    }
}
