package com.darya.jobassistant.jobdiscovery.url;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;

class JobUrlClassifierTest {

    @Test
    void directJobPage_withSpecificSlug_classifiesAsDirect() {
        assertThat(classify("https://example.com/jobs/senior-java-backend-engineer-42")).isEqualTo(JobUrlType.DIRECT_JOB);
    }

    @Test
    void directJobPage_withNumericIdSegment_classifiesAsDirect() {
        assertThat(classify("https://example.com/jobs/12345")).isEqualTo(JobUrlType.DIRECT_JOB);
    }

    @Test
    void directJobPage_withIdQueryParamDespiteGenericPath_classifiesAsDirect() {
        assertThat(classify("https://example.com/jobs?id=42")).isEqualTo(JobUrlType.DIRECT_JOB);
        assertThat(classify("https://boards.example.com/careers?gh_jid=778899")).isEqualTo(JobUrlType.DIRECT_JOB);
    }

    @Test
    void bareJobsCollectionPage_classifiesAsListing() {
        assertThat(classify("https://example.com/jobs")).isEqualTo(JobUrlType.SEARCH_OR_LISTING_PAGE);
        assertThat(classify("https://example.com/jobs/")).isEqualTo(JobUrlType.SEARCH_OR_LISTING_PAGE);
    }

    @Test
    void careersCollectionPage_classifiesAsListing() {
        assertThat(classify("https://example.com/careers")).isEqualTo(JobUrlType.SEARCH_OR_LISTING_PAGE);
    }

    @Test
    void searchPathSegment_classifiesAsListing() {
        assertThat(classify("https://example.com/en/search/jobs")).isEqualTo(JobUrlType.SEARCH_OR_LISTING_PAGE);
    }

    @Test
    void searchQueryParameter_classifiesAsListing() {
        assertThat(classify("https://boards.example.com/openings?q=java+backend")).isEqualTo(JobUrlType.SEARCH_OR_LISTING_PAGE);
        assertThat(classify("https://example.com/vacancies/browse?keywords=java")).isEqualTo(JobUrlType.SEARCH_OR_LISTING_PAGE);
    }

    @Test
    void rootPath_classifiesAsListing() {
        assertThat(classify("https://example.com")).isEqualTo(JobUrlType.SEARCH_OR_LISTING_PAGE);
        assertThat(classify("https://example.com/")).isEqualTo(JobUrlType.SEARCH_OR_LISTING_PAGE);
    }

    @Test
    void loginAndLegalPages_classifyAsUnsupported() {
        assertThat(classify("https://example.com/login")).isEqualTo(JobUrlType.UNSUPPORTED_OR_INVALID);
        assertThat(classify("https://example.com/privacy-policy")).isEqualTo(JobUrlType.UNSUPPORTED_OR_INVALID);
        assertThat(classify("https://example.com/terms")).isEqualTo(JobUrlType.UNSUPPORTED_OR_INVALID);
    }

    @Test
    void blogArticles_classifyAsUnsupported() {
        assertThat(classify("https://example.com/blog/how-we-hire")).isEqualTo(JobUrlType.UNSUPPORTED_OR_INVALID);
    }

    @Test
    void documentAndImageResources_classifyAsUnsupported() {
        assertThat(classify("https://example.com/files/job-description.pdf")).isEqualTo(JobUrlType.UNSUPPORTED_OR_INVALID);
        assertThat(classify("https://example.com/assets/logo.png")).isEqualTo(JobUrlType.UNSUPPORTED_OR_INVALID);
    }

    @Test
    void nullOrRelativeUrl_classifiesAsUnsupported() {
        assertThat(JobUrlClassifier.classify(null)).isEqualTo(JobUrlType.UNSUPPORTED_OR_INVALID);
        assertThat(JobUrlClassifier.classify(URI.create("/relative/path"))).isEqualTo(JobUrlType.UNSUPPORTED_OR_INVALID);
    }

    private JobUrlType classify(String url) {
        return JobUrlClassifier.classify(URI.create(url));
    }
}
