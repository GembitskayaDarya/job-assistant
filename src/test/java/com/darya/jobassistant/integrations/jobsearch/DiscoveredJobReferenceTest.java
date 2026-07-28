package com.darya.jobassistant.integrations.jobsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.Test;

class DiscoveredJobReferenceTest {

    private static final URI VALID_URL = URI.create("https://boards.example.com/jobs/123");

    @Test
    void validReference_isAccepted() {
        DiscoveredJobReference reference = new DiscoveredJobReference(VALID_URL, "Senior Java Backend Engineer", "Great remote role");

        assertThat(reference.sourceUrl()).isEqualTo(VALID_URL);
        assertThat(reference.title()).isEqualTo("Senior Java Backend Engineer");
        assertThat(reference.snippet()).isEqualTo("Great remote role");
    }

    @Test
    void twoArgConstructor_defaultsSnippetToNull() {
        DiscoveredJobReference reference = new DiscoveredJobReference(VALID_URL, "Senior Java Backend Engineer");

        assertThat(reference.snippet()).isNull();
    }

    @Test
    void blankSnippet_normalizesToNull() {
        DiscoveredJobReference reference = new DiscoveredJobReference(VALID_URL, "Senior Java Backend Engineer", "   ");

        assertThat(reference.snippet()).isNull();
    }

    @Test
    void rejectsNullSourceUrl() {
        assertThatThrownBy(() -> new DiscoveredJobReference(null, "Senior Java Backend Engineer", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsRelativeSourceUrl() {
        assertThatThrownBy(() -> new DiscoveredJobReference(URI.create("/jobs/123"), "Senior Java Backend Engineer", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonHttpScheme() {
        assertThatThrownBy(() -> new DiscoveredJobReference(URI.create("ftp://example.com/jobs/123"), "Senior Java Backend Engineer", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankTitle() {
        assertThatThrownBy(() -> new DiscoveredJobReference(VALID_URL, "   ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullTitle() {
        assertThatThrownBy(() -> new DiscoveredJobReference(VALID_URL, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsHttpScheme() {
        assertThatCode(() -> new DiscoveredJobReference(URI.create("http://example.com/jobs/123"), "Title", null))
                .doesNotThrowAnyException();
    }
}
