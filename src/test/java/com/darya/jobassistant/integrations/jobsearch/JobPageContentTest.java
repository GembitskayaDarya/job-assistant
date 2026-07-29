package com.darya.jobassistant.integrations.jobsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.Test;

class JobPageContentTest {

    private static final URI VALID_URL = URI.create("https://boards.example.com/jobs/123");

    @Test
    void validContent_isAccepted() {
        JobPageContent content = new JobPageContent(VALID_URL, "# Senior Java Backend Engineer\n\nDescription...", "Senior Java Backend Engineer");

        assertThat(content.sourceUrl()).isEqualTo(VALID_URL);
        assertThat(content.content()).isEqualTo("# Senior Java Backend Engineer\n\nDescription...");
        assertThat(content.title()).isEqualTo("Senior Java Backend Engineer");
    }

    @Test
    void twoArgConstructor_defaultsTitleToNull() {
        JobPageContent content = new JobPageContent(VALID_URL, "Description...");

        assertThat(content.title()).isNull();
    }

    @Test
    void blankTitle_normalizesToNull() {
        JobPageContent content = new JobPageContent(VALID_URL, "Description...", "   ");

        assertThat(content.title()).isNull();
    }

    @Test
    void rejectsNullSourceUrl() {
        assertThatThrownBy(() -> new JobPageContent(null, "Description...", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsRelativeSourceUrl() {
        assertThatThrownBy(() -> new JobPageContent(URI.create("/jobs/123"), "Description...", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUserInfoInSourceUrl() {
        assertThatThrownBy(() -> new JobPageContent(URI.create("https://user:pass@boards.example.com/jobs/123"), "Description...", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsHostlessSourceUrl() {
        assertThatThrownBy(() -> new JobPageContent(URI.create("https:opaque-no-host"), "Description...", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankContent() {
        assertThatThrownBy(() -> new JobPageContent(VALID_URL, "   ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullContent() {
        assertThatThrownBy(() -> new JobPageContent(VALID_URL, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
