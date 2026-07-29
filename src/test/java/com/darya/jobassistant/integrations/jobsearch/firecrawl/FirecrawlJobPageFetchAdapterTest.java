package com.darya.jobassistant.integrations.jobsearch.firecrawl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.config.FirecrawlProperties;
import com.darya.jobassistant.integrations.jobsearch.JobDiscoveryException;
import com.darya.jobassistant.integrations.jobsearch.JobPageContent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
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

class FirecrawlJobPageFetchAdapterTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final URI VALID_URL = URI.create("https://boards.example.com/jobs/123");

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

    // --- Successful mapping ---------------------------------------------------------------

    @Test
    void fetch_sendsExpectedRequest() throws Exception {
        server.enqueue(successResponse("""
                {"success": true, "data": {"markdown": "# Job\\n\\nDescription"}}
                """));
        FirecrawlJobPageFetchAdapter adapter = newAdapter(Duration.ofSeconds(5), Duration.ofSeconds(10),
                Duration.ofSeconds(5), 100_000);

        adapter.fetch(VALID_URL);

        RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getMethod()).isEqualTo("POST");
        assertThat(recorded.getPath()).isEqualTo("/v2/scrape");
        assertThat(recorded.getHeader("Content-Type")).startsWith("application/json");
        assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer test-api-key");

        JsonNode body = OBJECT_MAPPER.readTree(recorded.getBody().readUtf8());
        assertThat(body.get("url").asText()).isEqualTo(VALID_URL.toString());
        assertThat(body.get("formats")).hasSize(1);
        assertThat(body.get("formats").get(0).asText()).isEqualTo("markdown");
        assertThat(body.get("onlyMainContent").asBoolean()).isTrue();
        assertThat(body.get("onlyCleanContent").asBoolean()).isFalse();
        assertThat(body.get("removeBase64Images").asBoolean()).isTrue();
        assertThat(body.get("blockAds").asBoolean()).isTrue();
        assertThat(body.get("proxy").asText()).isEqualTo("basic");
        assertThat(body.get("timeout").asInt()).isEqualTo(5000);

        assertThat(body.has("actions")).isFalse();
        assertThat(body.has("screenshot")).isFalse();
        assertThat(body.has("html")).isFalse();
        assertThat(body.has("rawHtml")).isFalse();
        assertThat(body.has("json")).isFalse();
        assertThat(body.has("summary")).isFalse();
    }

    @Test
    void fetch_mapsMarkdownToJobPageContent() {
        server.enqueue(successResponse("""
                {"success": true, "data": {"markdown": "# Senior Java Backend Engineer\\n\\nDescription..."}}
                """));
        FirecrawlJobPageFetchAdapter adapter = newAdapter();

        JobPageContent content = adapter.fetch(VALID_URL);

        assertThat(content.sourceUrl()).isEqualTo(VALID_URL);
        assertThat(content.content()).isEqualTo("# Senior Java Backend Engineer\n\nDescription...");
    }

    @Test
    void fetch_preservesMarkdownByteForByte() throws Exception {
        String markdown = "# Title\n\n- item one\n- item two\n\n[link](https://example.com)\n\n> quote\n\ttab";
        server.enqueue(successResponse(OBJECT_MAPPER.writeValueAsString(
                java.util.Map.of("success", true, "data", java.util.Map.of("markdown", markdown)))));
        FirecrawlJobPageFetchAdapter adapter = newAdapter();

        JobPageContent content = adapter.fetch(VALID_URL);

        assertThat(content.content()).isEqualTo(markdown);
    }

    @Test
    void fetch_handlesUnicodeMarkdownCorrectly() throws Exception {
        String markdown = "# Héllo Wörld 日本語 🚀 emoji test — em dash";
        server.enqueue(successResponse(OBJECT_MAPPER.writeValueAsString(
                java.util.Map.of("success", true, "data", java.util.Map.of("markdown", markdown)))));
        FirecrawlJobPageFetchAdapter adapter = newAdapter();

        JobPageContent content = adapter.fetch(VALID_URL);

        assertThat(content.content()).isEqualTo(markdown);
    }

    // --- Cost-safety ------------------------------------------------------------------------

    @Test
    void fetch_success_performsExactlyOneHttpRequest() {
        server.enqueue(successResponse("""
                {"success": true, "data": {"markdown": "content"}}
                """));
        FirecrawlJobPageFetchAdapter adapter = newAdapter();

        adapter.fetch(VALID_URL);

        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void fetch_http429_performsExactlyOneHttpRequest_noRetry() {
        server.enqueue(new MockResponse().setResponseCode(429).setBody("rate limited"));
        FirecrawlJobPageFetchAdapter adapter = newAdapter();

        assertThatThrownBy(() -> adapter.fetch(VALID_URL)).isInstanceOf(JobDiscoveryException.class);
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void fetch_http500_performsExactlyOneHttpRequest_noRetry() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("error"));
        FirecrawlJobPageFetchAdapter adapter = newAdapter();

        assertThatThrownBy(() -> adapter.fetch(VALID_URL)).isInstanceOf(JobDiscoveryException.class);
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void fetch_timeout_performsExactlyOneHttpRequest_noRetry() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"success": true, "data": {"markdown": "content"}}
                        """)
                .setBodyDelay(2, TimeUnit.SECONDS));
        FirecrawlJobPageFetchAdapter adapter = newAdapter(Duration.ofMillis(500), Duration.ofMillis(1500),
                Duration.ofSeconds(1), 100_000);

        assertThatThrownBy(() -> adapter.fetch(VALID_URL))
                .isInstanceOf(JobDiscoveryException.class)
                .hasMessageContaining("timed out");
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void fetch_malformedJson_performsExactlyOneHttpRequest_noRetry() {
        server.enqueue(successResponse("not json"));
        FirecrawlJobPageFetchAdapter adapter = newAdapter();

        assertThatThrownBy(() -> adapter.fetch(VALID_URL)).isInstanceOf(JobDiscoveryException.class);
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void fetch_neverSendsAutoOrEnhancedProxy() throws Exception {
        server.enqueue(successResponse("""
                {"success": true, "data": {"markdown": "content"}}
                """));
        FirecrawlJobPageFetchAdapter adapter = newAdapter();

        adapter.fetch(VALID_URL);

        JsonNode body = OBJECT_MAPPER.readTree(server.takeRequest().getBody().readUtf8());
        assertThat(body.get("proxy").asText()).isNotIn("auto", "enhanced").isEqualTo("basic");
    }

    // --- URL validation -----------------------------------------------------------------------

    @Test
    void fetch_relativeUrl_causesZeroRequests() {
        FirecrawlJobPageFetchAdapter adapter = newAdapter();

        assertThatThrownBy(() -> adapter.fetch(URI.create("/jobs/123"))).isInstanceOf(JobDiscoveryException.class);
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void fetch_unsupportedScheme_causesZeroRequests() {
        FirecrawlJobPageFetchAdapter adapter = newAdapter();

        assertThatThrownBy(() -> adapter.fetch(URI.create("ftp://example.com/jobs/123")))
                .isInstanceOf(JobDiscoveryException.class);
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void fetch_hostlessUrl_causesZeroRequests() {
        FirecrawlJobPageFetchAdapter adapter = newAdapter();

        assertThatThrownBy(() -> adapter.fetch(URI.create("https:opaque-no-host")))
                .isInstanceOf(JobDiscoveryException.class);
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void fetch_urlWithUserInfo_causesZeroRequests() {
        FirecrawlJobPageFetchAdapter adapter = newAdapter();

        assertThatThrownBy(() -> adapter.fetch(URI.create("https://user:pass@example.com/jobs/123")))
                .isInstanceOf(JobDiscoveryException.class);
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void fetch_nullUrl_causesZeroRequests() {
        FirecrawlJobPageFetchAdapter adapter = newAdapter();

        assertThatThrownBy(() -> adapter.fetch(null)).isInstanceOf(JobDiscoveryException.class);
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void fetch_acceptsValidHttpUrl() {
        server.enqueue(successResponse("""
                {"success": true, "data": {"markdown": "content"}}
                """));
        FirecrawlJobPageFetchAdapter adapter = newAdapter();

        JobPageContent content = adapter.fetch(URI.create("http://example.com/jobs/123"));

        assertThat(content.sourceUrl().toString()).isEqualTo("http://example.com/jobs/123");
    }

    @Test
    void fetch_acceptsValidHttpsUrl() {
        server.enqueue(successResponse("""
                {"success": true, "data": {"markdown": "content"}}
                """));
        FirecrawlJobPageFetchAdapter adapter = newAdapter();

        JobPageContent content = adapter.fetch(VALID_URL);

        assertThat(content.sourceUrl()).isEqualTo(VALID_URL);
    }

    @Test
    void fetch_preservesQueryParametersInOutboundRequest() throws Exception {
        URI urlWithQuery = URI.create("https://boards.example.com/jobs/123?ref=abc&utm=x");
        server.enqueue(successResponse("""
                {"success": true, "data": {"markdown": "content"}}
                """));
        FirecrawlJobPageFetchAdapter adapter = newAdapter();

        adapter.fetch(urlWithQuery);

        JsonNode body = OBJECT_MAPPER.readTree(server.takeRequest().getBody().readUtf8());
        assertThat(body.get("url").asText()).isEqualTo(urlWithQuery.toString());
    }

    @Test
    void fetch_preservesFragment_withoutInventingNewPolicy() throws Exception {
        URI urlWithFragment = URI.create("https://boards.example.com/jobs/123#section");
        server.enqueue(successResponse("""
                {"success": true, "data": {"markdown": "content"}}
                """));
        FirecrawlJobPageFetchAdapter adapter = newAdapter();

        JobPageContent content = adapter.fetch(urlWithFragment);

        JsonNode body = OBJECT_MAPPER.readTree(server.takeRequest().getBody().readUtf8());
        assertThat(body.get("url").asText()).isEqualTo(urlWithFragment.toString());
        assertThat(content.sourceUrl()).isEqualTo(urlWithFragment);
    }

    // --- Response validation ------------------------------------------------------------------

    @Test
    void fetch_unsuccessfulResponse_isRejected() {
        server.enqueue(successResponse("""
                {"success": false, "data": null}
                """));
        FirecrawlJobPageFetchAdapter adapter = newAdapter();

        assertThatThrownBy(() -> adapter.fetch(VALID_URL))
                .isInstanceOf(JobDiscoveryException.class)
                .hasMessageContaining("unsuccessful");
    }

    @Test
    void fetch_missingData_isRejected() {
        server.enqueue(successResponse("""
                {"success": true}
                """));
        FirecrawlJobPageFetchAdapter adapter = newAdapter();

        assertThatThrownBy(() -> adapter.fetch(VALID_URL))
                .isInstanceOf(JobDiscoveryException.class)
                .hasMessageContaining("could not be parsed");
    }

    @Test
    void fetch_missingMarkdown_isRejected() {
        server.enqueue(successResponse("""
                {"success": true, "data": {}}
                """));
        FirecrawlJobPageFetchAdapter adapter = newAdapter();

        assertThatThrownBy(() -> adapter.fetch(VALID_URL))
                .isInstanceOf(JobDiscoveryException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void fetch_blankMarkdown_isRejected() {
        server.enqueue(successResponse("""
                {"success": true, "data": {"markdown": "   "}}
                """));
        FirecrawlJobPageFetchAdapter adapter = newAdapter();

        assertThatThrownBy(() -> adapter.fetch(VALID_URL))
                .isInstanceOf(JobDiscoveryException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void fetch_oversizedMarkdown_isRejectedWithoutLeakingContent() throws Exception {
        String secretMarkdownMarker = "UNIQUE_MARKDOWN_MARKER_SHOULD_NOT_LEAK";
        String oversizedMarkdown = secretMarkdownMarker + "x".repeat(50);
        server.enqueue(successResponse(OBJECT_MAPPER.writeValueAsString(
                java.util.Map.of("success", true, "data", java.util.Map.of("markdown", oversizedMarkdown)))));
        FirecrawlJobPageFetchAdapter adapter = newAdapter(Duration.ofSeconds(5), Duration.ofSeconds(10),
                Duration.ofSeconds(5), 10);

        assertThatThrownBy(() -> adapter.fetch(VALID_URL))
                .isInstanceOf(JobDiscoveryException.class)
                .hasMessageContaining(String.valueOf(oversizedMarkdown.length()))
                .hasMessageContaining("10")
                .hasMessageNotContaining(secretMarkdownMarker);
    }

    @Test
    void fetch_malformedJson_isRejected() {
        server.enqueue(successResponse("not json"));
        FirecrawlJobPageFetchAdapter adapter = newAdapter();

        assertThatThrownBy(() -> adapter.fetch(VALID_URL))
                .isInstanceOf(JobDiscoveryException.class)
                .hasMessageContaining("could not be parsed");
    }

    @Test
    void fetch_emptyHttpResponse_isRejected() {
        server.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(""));
        FirecrawlJobPageFetchAdapter adapter = newAdapter();

        assertThatThrownBy(() -> adapter.fetch(VALID_URL)).isInstanceOf(JobDiscoveryException.class);
    }

    // --- HTTP/error mapping -------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 402, 403, 404, 408, 429, 500, 502, 503})
    void fetch_nonSuccessStatus_mapsToJobDiscoveryExceptionOnly(int status) {
        server.enqueue(new MockResponse().setResponseCode(status).setBody("sensitive-provider-error-body"));
        FirecrawlJobPageFetchAdapter adapter = newAdapter();

        assertThatThrownBy(() -> adapter.fetch(VALID_URL))
                .isInstanceOf(JobDiscoveryException.class)
                .isNotInstanceOf(org.springframework.web.reactive.function.client.WebClientResponseException.class)
                .hasMessageContaining(String.valueOf(status))
                .hasMessageNotContaining("sensitive-provider-error-body")
                .hasMessageNotContaining("test-api-key");
    }

    @Test
    void fetch_connectionFailure_mapsToJobDiscoveryException() {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
        FirecrawlJobPageFetchAdapter adapter = newAdapter();

        assertThatThrownBy(() -> adapter.fetch(VALID_URL)).isInstanceOf(JobDiscoveryException.class);
    }

    @Test
    void fetch_responseTimeout_mapsToJobDiscoveryException() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"success": true, "data": {"markdown": "content"}}
                        """)
                .setBodyDelay(2, TimeUnit.SECONDS));
        FirecrawlJobPageFetchAdapter adapter = newAdapter(Duration.ofMillis(500), Duration.ofMillis(1500),
                Duration.ofSeconds(1), 100_000);

        assertThatThrownBy(() -> adapter.fetch(VALID_URL))
                .isInstanceOf(JobDiscoveryException.class)
                .hasMessageContaining("timed out");
    }

    @Test
    void fetch_apiKeyNeverAppearsInExceptionMessage() {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("unauthorized"));
        FirecrawlJobPageFetchAdapter adapter = newAdapter();

        assertThatThrownBy(() -> adapter.fetch(VALID_URL))
                .isInstanceOf(JobDiscoveryException.class)
                .hasMessageNotContaining("test-api-key");
    }

    private MockResponse successResponse(String body) {
        return new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body);
    }

    private FirecrawlJobPageFetchAdapter newAdapter() {
        return newAdapter(Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(5), 100_000);
    }

    private FirecrawlJobPageFetchAdapter newAdapter(Duration connectTimeout, Duration readTimeout,
                                                     Duration scrapeTimeout, int maxMarkdownChars) {
        FirecrawlProperties properties = new FirecrawlProperties(true, "test-api-key", server.url("").toString(),
                10, connectTimeout, readTimeout, scrapeTimeout, maxMarkdownChars);
        WebClient webClient = new FirecrawlWebClientConfig(properties).firecrawlWebClient(OBJECT_MAPPER);
        return new FirecrawlJobPageFetchAdapter(webClient, properties);
    }
}
