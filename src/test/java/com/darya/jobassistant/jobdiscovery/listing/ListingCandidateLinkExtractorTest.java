package com.darya.jobassistant.jobdiscovery.listing;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListingCandidateLinkExtractorTest {

    private static final URI LISTING_URL = URI.create("https://boards.example.com/jobs");

    @Test
    void extractsAbsoluteMarkdownLinks() {
        String markdown = """
                # Open roles

                - [Senior Java Backend Engineer](https://boards.example.com/jobs/123)
                - [Platform Engineer](https://boards.example.com/jobs/456)
                """;

        List<ListingCandidateLink> links = ListingCandidateLinkExtractor.extract(markdown, LISTING_URL, 10);

        assertThat(links).extracting(ListingCandidateLink::url).containsExactly(
                URI.create("https://boards.example.com/jobs/123"),
                URI.create("https://boards.example.com/jobs/456"));
        assertThat(links).extracting(ListingCandidateLink::anchorText)
                .containsExactly("Senior Java Backend Engineer", "Platform Engineer");
    }

    @Test
    void resolvesRelativeLinksAgainstListingPageUrl() {
        String markdown = "[Backend Engineer](/jobs/789)";

        List<ListingCandidateLink> links = ListingCandidateLinkExtractor.extract(markdown, LISTING_URL, 10);

        assertThat(links).extracting(ListingCandidateLink::url)
                .containsExactly(URI.create("https://boards.example.com/jobs/789"));
    }

    @Test
    void deduplicatesRepeatedLinks() {
        String markdown = """
                [Backend Engineer](https://boards.example.com/jobs/123)
                [Backend Engineer (apply)](https://boards.example.com/jobs/123)
                """;

        List<ListingCandidateLink> links = ListingCandidateLinkExtractor.extract(markdown, LISTING_URL, 10);

        assertThat(links).hasSize(1);
        assertThat(links.get(0).anchorText()).isEqualTo("Backend Engineer");
    }

    @Test
    void dropsIrrelevantAndInvalidLinkSchemes() {
        String markdown = """
                [Email us](mailto:jobs@example.com)
                [Call to action](javascript:void(0))
                [Section anchor](#top)
                [Valid job](https://boards.example.com/jobs/123)
                """;

        List<ListingCandidateLink> links = ListingCandidateLinkExtractor.extract(markdown, LISTING_URL, 10);

        assertThat(links).extracting(ListingCandidateLink::url)
                .containsExactly(URI.create("https://boards.example.com/jobs/123"));
    }

    @Test
    void supportsAngleBracketAutolinks() {
        String markdown = "See <https://boards.example.com/jobs/999> for details.";

        List<ListingCandidateLink> links = ListingCandidateLinkExtractor.extract(markdown, LISTING_URL, 10);

        assertThat(links).extracting(ListingCandidateLink::url)
                .containsExactly(URI.create("https://boards.example.com/jobs/999"));
    }

    @Test
    void boundsResultsToMaxCandidates() {
        StringBuilder markdown = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            markdown.append("[Job ").append(i).append("](https://boards.example.com/jobs/").append(i).append(")\n");
        }

        List<ListingCandidateLink> links = ListingCandidateLinkExtractor.extract(markdown.toString(), LISTING_URL, 3);

        assertThat(links).hasSize(3);
    }

    @Test
    void zeroValidLinks_returnsEmptyList() {
        String markdown = "No links here, just plain text about our great culture.";

        List<ListingCandidateLink> links = ListingCandidateLinkExtractor.extract(markdown, LISTING_URL, 10);

        assertThat(links).isEmpty();
    }

    @Test
    void blankMarkdown_returnsEmptyList() {
        assertThat(ListingCandidateLinkExtractor.extract(null, LISTING_URL, 10)).isEmpty();
        assertThat(ListingCandidateLinkExtractor.extract("   ", LISTING_URL, 10)).isEmpty();
    }

    @Test
    void nestedListingLinkIsStillExtracted_classificationHappensDownstream() {
        // The extractor itself does not classify - a link to another listing/search page is
        // extracted like any other; JobDiscoveryService's re-classification is what discards it.
        String markdown = "[More roles](https://boards.example.com/jobs?page=2)";

        List<ListingCandidateLink> links = ListingCandidateLinkExtractor.extract(markdown, LISTING_URL, 10);

        assertThat(links).extracting(ListingCandidateLink::url)
                .containsExactly(URI.create("https://boards.example.com/jobs?page=2"));
    }
}
