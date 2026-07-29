package com.darya.jobassistant.integrations.jobsearch.firecrawl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.config.FirecrawlProperties;
import com.darya.jobassistant.integrations.jobsearch.DiscoveredJobReference;
import com.darya.jobassistant.integrations.jobsearch.JobDiscoveryException;
import com.darya.jobassistant.integrations.jobsearch.JobSearchRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.reactive.function.client.WebClient;

class FirecrawlJobSearchAdapterTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void search_sendsExpectedRequest() throws Exception {
        server.enqueue(successResponse("""
                {"success": true, "data": {"web": []}}
                """));
        FirecrawlJobSearchAdapter adapter = newAdapter(10, Duration.ofSeconds(5), Duration.ofSeconds(5));

        adapter.search(new JobSearchRequest("java backend remote", 5));

        RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getMethod()).isEqualTo("POST");
        assertThat(recorded.getPath()).isEqualTo("/v2/search");
        assertThat(recorded.getHeader("Content-Type")).startsWith("application/json");
        assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer test-api-key");

        JsonNode body = OBJECT_MAPPER.readTree(recorded.getBody().readUtf8());
        assertThat(body.get("query").asText()).isEqualTo("java backend remote");
        assertThat(body.get("sources")).hasSize(1);
        assertThat(body.get("sources").get(0).asText()).isEqualTo("web");
        assertThat(body.get("ignoreInvalidURLs").asBoolean()).isTrue();
        assertThat(body.has("scrapeOptions")).isFalse();
    }

    @Test
    void search_effectiveLimit_usesMaxResultsWhenBelowConfiguredSearchLimit() throws Exception {
        server.enqueue(successResponse("""
                {"success": true, "data": {"web": []}}
                """));
        FirecrawlJobSearchAdapter adapter = newAdapter(10, Duration.ofSeconds(5), Duration.ofSeconds(5));

        adapter.search(new JobSearchRequest("java backend", 3));

        JsonNode body = OBJECT_MAPPER.readTree(server.takeRequest().getBody().readUtf8());
        assertThat(body.get("limit").asInt()).isEqualTo(3);
    }

    @Test
    void search_effectiveLimit_usesConfiguredSearchLimitWhenBelowMaxResults() throws Exception {
        server.enqueue(successResponse("""
                {"success": true, "data": {"web": []}}
                """));
        FirecrawlJobSearchAdapter adapter = newAdapter(4, Duration.ofSeconds(5), Duration.ofSeconds(5));

        adapter.search(new JobSearchRequest("java backend", 50));

        JsonNode body = OBJECT_MAPPER.readTree(server.takeRequest().getBody().readUtf8());
        assertThat(body.get("limit").asInt()).isEqualTo(4);
    }

    @Test
    void search_mapsUrlTitleAndDescription_preservingOrder() {
        server.enqueue(successResponse("""
                {
                  "success": true,
                  "data": {
                    "web": [
                      {"url": "https://example.com/jobs/1", "title": "Job One", "description": "First role"},
                      {"url": "https://example.com/jobs/2", "title": "Job Two", "description": "Second role"}
                    ]
                  },
                  "warning": null,
                  "id": "abc",
                  "creditsUsed": 2
                }
                """));
        FirecrawlJobSearchAdapter adapter = newAdapter(10, Duration.ofSeconds(5), Duration.ofSeconds(5));

        List<DiscoveredJobReference> results = adapter.search(new JobSearchRequest("java backend", 10));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).sourceUrl().toString()).isEqualTo("https://example.com/jobs/1");
        assertThat(results.get(0).title()).isEqualTo("Job One");
        assertThat(results.get(0).snippet()).isEqualTo("First role");
        assertThat(results.get(1).sourceUrl().toString()).isEqualTo("https://example.com/jobs/2");
        assertThat(results.get(1).title()).isEqualTo("Job Two");
    }

    @Test
    void search_blankDescription_becomesNullSnippet() {
        server.enqueue(successResponse("""
                {"success": true, "data": {"web": [
                  {"url": "https://example.com/jobs/1", "title": "Job One", "description": "   "}
                ]}}
                """));
        FirecrawlJobSearchAdapter adapter = newAdapter(10, Duration.ofSeconds(5), Duration.ofSeconds(5));

        List<DiscoveredJobReference> results = adapter.search(new JobSearchRequest("java backend", 10));

        assertThat(results.get(0).snippet()).isNull();
    }

    @Test
    void search_returnsImmutableList() {
        server.enqueue(successResponse("""
                {"success": true, "data": {"web": [
                  {"url": "https://example.com/jobs/1", "title": "Job One", "description": null}
                ]}}
                """));
        FirecrawlJobSearchAdapter adapter = newAdapter(10, Duration.ofSeconds(5), Duration.ofSeconds(5));

        List<DiscoveredJobReference> results = adapter.search(new JobSearchRequest("java backend", 10));

        assertThatThrownBy(() -> results.add(null)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void search_emptyWebArray_returnsEmptyImmutableList() {
        server.enqueue(successResponse("""
                {"success": true, "data": {"web": []}}
                """));
        FirecrawlJobSearchAdapter adapter = newAdapter(10, Duration.ofSeconds(5), Duration.ofSeconds(5));

        List<DiscoveredJobReference> results = adapter.search(new JobSearchRequest("java backend", 10));

        assertThat(results).isEmpty();
        assertThatThrownBy(() -> results.add(null)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void search_skipsResultWithMissingUrl_butKeepsValidSiblings() {
        server.enqueue(successResponse("""
                {"success": true, "data": {"web": [
                  {"url": null, "title": "Missing URL"},
                  {"url": "https://example.com/jobs/2", "title": "Valid"}
                ]}}
                """));
        FirecrawlJobSearchAdapter adapter = newAdapter(10, Duration.ofSeconds(5), Duration.ofSeconds(5));

        List<DiscoveredJobReference> results = adapter.search(new JobSearchRequest("java backend", 10));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).title()).isEqualTo("Valid");
    }

    @Test
    void search_skipsResultWithUnsupportedScheme_butKeepsValidSiblings() {
        server.enqueue(successResponse("""
                {"success": true, "data": {"web": [
                  {"url": "ftp://example.com/jobs/1", "title": "Bad Scheme"},
                  {"url": "https://example.com/jobs/2", "title": "Valid"}
                ]}}
                """));
        FirecrawlJobSearchAdapter adapter = newAdapter(10, Duration.ofSeconds(5), Duration.ofSeconds(5));

        List<DiscoveredJobReference> results = adapter.search(new JobSearchRequest("java backend", 10));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).title()).isEqualTo("Valid");
    }

    @Test
    void search_skipsResultWithBlankTitle_butKeepsValidSiblings() {
        server.enqueue(successResponse("""
                {"success": true, "data": {"web": [
                  {"url": "https://example.com/jobs/1", "title": "   "},
                  {"url": "https://example.com/jobs/2", "title": "Valid"}
                ]}}
                """));
        FirecrawlJobSearchAdapter adapter = newAdapter(10, Duration.ofSeconds(5), Duration.ofSeconds(5));

        List<DiscoveredJobReference> results = adapter.search(new JobSearchRequest("java backend", 10));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).title()).isEqualTo("Valid");
    }

    @Test
    void search_unsuccessfulResponse_throwsJobDiscoveryException() {
        server.enqueue(successResponse("""
                {"success": false, "data": null}
                """));
        FirecrawlJobSearchAdapter adapter = newAdapter(10, Duration.ofSeconds(5), Duration.ofSeconds(5));

        assertThatThrownBy(() -> adapter.search(new JobSearchRequest("java backend", 10)))
                .isInstanceOf(JobDiscoveryException.class)
                .hasMessageContaining("unsuccessful");
    }

    @Test
    void search_missingData_throwsJobDiscoveryException() {
        server.enqueue(successResponse("""
                {"success": true}
                """));
        FirecrawlJobSearchAdapter adapter = newAdapter(10, Duration.ofSeconds(5), Duration.ofSeconds(5));

        assertThatThrownBy(() -> adapter.search(new JobSearchRequest("java backend", 10)))
                .isInstanceOf(JobDiscoveryException.class)
                .hasMessageContaining("could not be parsed");
    }

    @Test
    void search_missingWebField_throwsJobDiscoveryException() {
        server.enqueue(successResponse("""
                {"success": true, "data": {}}
                """));
        FirecrawlJobSearchAdapter adapter = newAdapter(10, Duration.ofSeconds(5), Duration.ofSeconds(5));

        assertThatThrownBy(() -> adapter.search(new JobSearchRequest("java backend", 10)))
                .isInstanceOf(JobDiscoveryException.class)
                .hasMessageContaining("could not be parsed");
    }

    @Test
    void search_emptyResponseBody_throwsJobDiscoveryException() {
        server.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(""));
        FirecrawlJobSearchAdapter adapter = newAdapter(10, Duration.ofSeconds(5), Duration.ofSeconds(5));

        assertThatThrownBy(() -> adapter.search(new JobSearchRequest("java backend", 10)))
                .isInstanceOf(JobDiscoveryException.class);
    }

    @Test
    void search_malformedJson_throwsJobDiscoveryException() {
        server.enqueue(successResponse("not json"));
        FirecrawlJobSearchAdapter adapter = newAdapter(10, Duration.ofSeconds(5), Duration.ofSeconds(5));

        assertThatThrownBy(() -> adapter.search(new JobSearchRequest("java backend", 10)))
                .isInstanceOf(JobDiscoveryException.class)
                .hasMessageContaining("could not be parsed");
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 402, 408, 429, 500})
    void search_nonSuccessStatus_throwsJobDiscoveryExceptionOnly(int status) {
        server.enqueue(new MockResponse().setResponseCode(status).setBody("error"));
        FirecrawlJobSearchAdapter adapter = newAdapter(10, Duration.ofSeconds(5), Duration.ofSeconds(5));

        assertThatThrownBy(() -> adapter.search(new JobSearchRequest("java backend", 10)))
                .isInstanceOf(JobDiscoveryException.class)
                .isNotInstanceOf(org.springframework.web.reactive.function.client.WebClientResponseException.class)
                .hasMessageContaining(String.valueOf(status));
    }

    @Test
    void search_readTimeout_throwsJobDiscoveryException() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"success": true, "data": {"web": []}}
                        """)
                .setBodyDelay(2, TimeUnit.SECONDS));
        FirecrawlJobSearchAdapter adapter = newAdapter(10, Duration.ofMillis(500), Duration.ofMillis(1500),
                Duration.ofSeconds(1), 100_000);

        assertThatThrownBy(() -> adapter.search(new JobSearchRequest("java backend", 10)))
                .isInstanceOf(JobDiscoveryException.class)
                .hasMessageContaining("timed out");
    }

    @Test
    void search_connectionFailure_throwsJobDiscoveryException() throws IOException {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
        FirecrawlJobSearchAdapter adapter = newAdapter(10, Duration.ofSeconds(5), Duration.ofSeconds(5));

        assertThatThrownBy(() -> adapter.search(new JobSearchRequest("java backend", 10)))
                .isInstanceOf(JobDiscoveryException.class);
    }

    @Test
    void search_performsExactlyOneHttpRequest() {
        server.enqueue(successResponse("""
                {"success": true, "data": {"web": []}}
                """));
        FirecrawlJobSearchAdapter adapter = newAdapter(10, Duration.ofSeconds(5), Duration.ofSeconds(5));

        adapter.search(new JobSearchRequest("java backend", 10));

        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void search_httpFailure_producesExactlyOneRequest_noRetry() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("error"));
        FirecrawlJobSearchAdapter adapter = newAdapter(10, Duration.ofSeconds(5), Duration.ofSeconds(5));

        assertThatThrownBy(() -> adapter.search(new JobSearchRequest("java backend", 10)))
                .isInstanceOf(JobDiscoveryException.class);
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    private MockResponse successResponse(String body) {
        return new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body);
    }

    private FirecrawlJobSearchAdapter newAdapter(int searchLimit, Duration connectTimeout, Duration readTimeout) {
        // scrapeTimeout defaults to the minimum valid value (1s), well below the 5s read-timeout
        // most of these search-only tests use - these tests never exercise the scrape code path.
        return newAdapter(searchLimit, connectTimeout, readTimeout, Duration.ofSeconds(1), 100_000);
    }

    private FirecrawlJobSearchAdapter newAdapter(int searchLimit, Duration connectTimeout, Duration readTimeout,
                                                  Duration scrapeTimeout, int maxMarkdownChars) {
        FirecrawlProperties properties = new FirecrawlProperties(true, "test-api-key", server.url("").toString(),
                searchLimit, connectTimeout, readTimeout, scrapeTimeout, maxMarkdownChars);
        WebClient webClient = new FirecrawlWebClientConfig(properties).firecrawlWebClient(OBJECT_MAPPER);
        return new FirecrawlJobSearchAdapter(webClient, properties);
    }
}
