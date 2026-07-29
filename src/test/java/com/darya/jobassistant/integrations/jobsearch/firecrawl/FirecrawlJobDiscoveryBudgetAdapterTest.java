package com.darya.jobassistant.integrations.jobsearch.firecrawl;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.config.FirecrawlProperties;
import com.darya.jobassistant.integrations.jobsearch.JobSearchRequest;
import com.darya.jobassistant.jobdiscovery.budget.JobDiscoveryBudgetDecision;
import com.darya.jobassistant.jobdiscovery.budget.JobDiscoveryBudgetRequest;
import com.darya.jobassistant.jobdiscovery.budget.JobDiscoveryBudgetStatus;
import com.darya.jobassistant.jobdiscovery.config.JobDiscoveryProperties;
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

class FirecrawlJobDiscoveryBudgetAdapterTest {

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

    // --- HTTP contract -----------------------------------------------------------------------

    @Test
    void assessBudget_sendsExpectedRequest() throws Exception {
        server.enqueue(successResponse(validBody()));
        FirecrawlJobDiscoveryBudgetAdapter adapter = newAdapter();

        adapter.assessBudget(defaultRequest());

        RecordedRequest recorded = server.takeRequest();
        assertThat(recorded.getMethod()).isEqualTo("GET");
        assertThat(recorded.getPath()).isEqualTo("/v2/team/credit-usage");
        assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer test-api-key");
        assertThat(recorded.getHeader("Accept")).isEqualTo("application/json");
        assertThat(recorded.getBodySize()).isZero();
    }

    @Test
    void assessBudget_success_performsExactlyOneHttpRequest() {
        server.enqueue(successResponse(validBody()));
        FirecrawlJobDiscoveryBudgetAdapter adapter = newAdapter();

        adapter.assessBudget(defaultRequest());

        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void assessBudget_timeout_performsExactlyOneHttpRequest_noRetry() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(validBody())
                .setBodyDelay(3, TimeUnit.SECONDS));
        FirecrawlJobDiscoveryBudgetAdapter adapter = newAdapter(Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(1));

        JobDiscoveryBudgetDecision decision = adapter.assessBudget(defaultRequest());

        assertThat(decision.status()).isEqualTo(JobDiscoveryBudgetStatus.UNAVAILABLE);
        assertThat(decision.reasonCategory()).isEqualTo("TIMEOUT");
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void assessBudget_http429_performsExactlyOneHttpRequest_noRetry() {
        server.enqueue(new MockResponse().setResponseCode(429).setBody("rate limited"));
        FirecrawlJobDiscoveryBudgetAdapter adapter = newAdapter();

        JobDiscoveryBudgetDecision decision = adapter.assessBudget(defaultRequest());

        assertThat(decision.status()).isEqualTo(JobDiscoveryBudgetStatus.UNAVAILABLE);
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void assessBudget_http500_performsExactlyOneHttpRequest_noRetry() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("error"));
        FirecrawlJobDiscoveryBudgetAdapter adapter = newAdapter();

        JobDiscoveryBudgetDecision decision = adapter.assessBudget(defaultRequest());

        assertThat(decision.status()).isEqualTo(JobDiscoveryBudgetStatus.UNAVAILABLE);
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    // --- Response mapping ----------------------------------------------------------------------

    @Test
    void assessBudget_validResponse_mapsAllFourProviderFields() {
        server.enqueue(successResponse("""
                {"success": true, "data": {"remainingCredits": 750, "planCredits": 1000, \
                "billingPeriodStart": "2026-07-01T00:00:00Z", "billingPeriodEnd": "2026-07-31T23:59:59Z"}}
                """));
        FirecrawlJobDiscoveryBudgetAdapter adapter = newAdapter();

        JobDiscoveryBudgetDecision decision = adapter.assessBudget(defaultRequest());

        assertThat(decision.remainingCredits()).isEqualTo(750L);
        assertThat(decision.planCredits()).isEqualTo(1000L);
        assertThat(decision.estimatedUsedCredits()).isEqualTo(250L);
        assertThat(decision.billingPeriodStart().toString()).isEqualTo("2026-07-01T00:00:00Z");
        assertThat(decision.billingPeriodEnd().toString()).isEqualTo("2026-07-31T23:59:59Z");
        assertThat(decision.status()).isEqualTo(JobDiscoveryBudgetStatus.ALLOWED);
    }

    @Test
    void assessBudget_zeroRemainingCredits_isValidInput() {
        server.enqueue(successResponse("""
                {"success": true, "data": {"remainingCredits": 0, "planCredits": 1000, \
                "billingPeriodStart": "2026-07-01T00:00:00Z", "billingPeriodEnd": "2026-07-31T23:59:59Z"}}
                """));
        FirecrawlJobDiscoveryBudgetAdapter adapter = newAdapter();

        JobDiscoveryBudgetDecision decision = adapter.assessBudget(defaultRequest());

        assertThat(decision.status()).isNotEqualTo(JobDiscoveryBudgetStatus.UNAVAILABLE);
        assertThat(decision.remainingCredits()).isZero();
    }

    @Test
    void assessBudget_remainingGreaterThanPlanCredits_isAccepted() {
        server.enqueue(successResponse("""
                {"success": true, "data": {"remainingCredits": 1500, "planCredits": 1000, \
                "billingPeriodStart": "2026-07-01T00:00:00Z", "billingPeriodEnd": "2026-07-31T23:59:59Z"}}
                """));
        FirecrawlJobDiscoveryBudgetAdapter adapter = newAdapter();

        JobDiscoveryBudgetDecision decision = adapter.assessBudget(defaultRequest());

        assertThat(decision.status()).isNotEqualTo(JobDiscoveryBudgetStatus.UNAVAILABLE);
        assertThat(decision.estimatedUsedCredits()).isZero();
    }

    @Test
    void assessBudget_zeroPlanCredits_isRejected() {
        server.enqueue(successResponse("""
                {"success": true, "data": {"remainingCredits": 1000, "planCredits": 0, \
                "billingPeriodStart": "2026-07-01T00:00:00Z", "billingPeriodEnd": "2026-07-31T23:59:59Z"}}
                """));
        FirecrawlJobDiscoveryBudgetAdapter adapter = newAdapter();

        JobDiscoveryBudgetDecision decision = adapter.assessBudget(defaultRequest());

        assertThat(decision.status()).isEqualTo(JobDiscoveryBudgetStatus.UNAVAILABLE);
        assertThat(decision.reasonCategory()).isEqualTo("INVALID_PROVIDER_RESPONSE");
    }

    @Test
    void assessBudget_negativeRemainingCredits_isRejected() {
        server.enqueue(successResponse("""
                {"success": true, "data": {"remainingCredits": -1, "planCredits": 1000, \
                "billingPeriodStart": "2026-07-01T00:00:00Z", "billingPeriodEnd": "2026-07-31T23:59:59Z"}}
                """));
        FirecrawlJobDiscoveryBudgetAdapter adapter = newAdapter();

        JobDiscoveryBudgetDecision decision = adapter.assessBudget(defaultRequest());

        assertThat(decision.status()).isEqualTo(JobDiscoveryBudgetStatus.UNAVAILABLE);
    }

    @Test
    void assessBudget_missingData_isRejected() {
        server.enqueue(successResponse("""
                {"success": true}
                """));
        FirecrawlJobDiscoveryBudgetAdapter adapter = newAdapter();

        JobDiscoveryBudgetDecision decision = adapter.assessBudget(defaultRequest());

        assertThat(decision.status()).isEqualTo(JobDiscoveryBudgetStatus.UNAVAILABLE);
        assertThat(decision.reasonCategory()).isEqualTo("INVALID_PROVIDER_RESPONSE");
    }

    @Test
    void assessBudget_unsuccessfulResponse_isRejected() {
        server.enqueue(successResponse("""
                {"success": false, "data": null}
                """));
        FirecrawlJobDiscoveryBudgetAdapter adapter = newAdapter();

        JobDiscoveryBudgetDecision decision = adapter.assessBudget(defaultRequest());

        assertThat(decision.status()).isEqualTo(JobDiscoveryBudgetStatus.UNAVAILABLE);
        assertThat(decision.reasonCategory()).isEqualTo("INVALID_PROVIDER_RESPONSE");
    }

    @Test
    void assessBudget_missingBillingDates_isRejected() {
        server.enqueue(successResponse("""
                {"success": true, "data": {"remainingCredits": 1000, "planCredits": 1000}}
                """));
        FirecrawlJobDiscoveryBudgetAdapter adapter = newAdapter();

        JobDiscoveryBudgetDecision decision = adapter.assessBudget(defaultRequest());

        assertThat(decision.status()).isEqualTo(JobDiscoveryBudgetStatus.UNAVAILABLE);
        assertThat(decision.reasonCategory()).isEqualTo("INVALID_PROVIDER_RESPONSE");
    }

    @Test
    void assessBudget_billingPeriodEndBeforeStart_isRejected() {
        server.enqueue(successResponse("""
                {"success": true, "data": {"remainingCredits": 1000, "planCredits": 1000, \
                "billingPeriodStart": "2026-07-31T23:59:59Z", "billingPeriodEnd": "2026-07-01T00:00:00Z"}}
                """));
        FirecrawlJobDiscoveryBudgetAdapter adapter = newAdapter();

        JobDiscoveryBudgetDecision decision = adapter.assessBudget(defaultRequest());

        assertThat(decision.status()).isEqualTo(JobDiscoveryBudgetStatus.UNAVAILABLE);
        assertThat(decision.reasonCategory()).isEqualTo("INVALID_PROVIDER_RESPONSE");
    }

    @Test
    void assessBudget_malformedJson_isRejected() {
        server.enqueue(successResponse("not json"));
        FirecrawlJobDiscoveryBudgetAdapter adapter = newAdapter();

        JobDiscoveryBudgetDecision decision = adapter.assessBudget(defaultRequest());

        assertThat(decision.status()).isEqualTo(JobDiscoveryBudgetStatus.UNAVAILABLE);
        assertThat(decision.reasonCategory()).isEqualTo("DECODING_ERROR");
    }

    @Test
    void assessBudget_unknownFields_areIgnored() {
        server.enqueue(successResponse("""
                {"success": true, "warning": "low balance", "id": "abc123", "data": {"remainingCredits": 1000, \
                "planCredits": 1000, "creditsUsed": 500, "billingPeriodStart": "2026-07-01T00:00:00Z", \
                "billingPeriodEnd": "2026-07-31T23:59:59Z"}}
                """));
        FirecrawlJobDiscoveryBudgetAdapter adapter = newAdapter();

        JobDiscoveryBudgetDecision decision = adapter.assessBudget(defaultRequest());

        assertThat(decision.status()).isEqualTo(JobDiscoveryBudgetStatus.ALLOWED);
        assertThat(decision.remainingCredits()).isEqualTo(1000L);
    }

    // --- HTTP failure mapping ------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 402, 403, 404, 408, 429, 500, 502, 503})
    void assessBudget_nonSuccessStatus_mapsToUnavailableWithoutLeaking(int status) {
        server.enqueue(new MockResponse().setResponseCode(status).setBody("sensitive-provider-error-body"));
        FirecrawlJobDiscoveryBudgetAdapter adapter = newAdapter();

        JobDiscoveryBudgetDecision decision = adapter.assessBudget(defaultRequest());

        assertThat(decision.status()).isEqualTo(JobDiscoveryBudgetStatus.UNAVAILABLE);
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCategory()).isEqualTo("HTTP_" + status);
        assertThat(decision.reasonCategory()).doesNotContain("sensitive-provider-error-body", "test-api-key");
    }

    @Test
    void assessBudget_connectionFailure_mapsToUnavailable() {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
        FirecrawlJobDiscoveryBudgetAdapter adapter = newAdapter();

        JobDiscoveryBudgetDecision decision = adapter.assessBudget(defaultRequest());

        assertThat(decision.status()).isEqualTo(JobDiscoveryBudgetStatus.UNAVAILABLE);
        assertThat(decision.reasonCategory()).isEqualTo("CONNECTION_ERROR");
    }

    // --- Cost estimation (end-to-end through the adapter) --------------------------------------

    @Test
    void assessBudget_defaultThreeQueriesPlusFiveScrapes_estimatesElevenCredits() {
        server.enqueue(successResponse(validBody()));
        FirecrawlJobDiscoveryBudgetAdapter adapter = newAdapter();

        JobDiscoveryBudgetDecision decision = adapter.assessBudget(defaultRequest());

        assertThat(decision.estimatedSearchCredits()).isEqualTo(6);
        assertThat(decision.estimatedScrapeCredits()).isEqualTo(5);
        assertThat(decision.estimatedTotalCredits()).isEqualTo(11);
    }

    // --- Security --------------------------------------------------------------------------------

    @Test
    void assessBudget_apiKeyNeverAppearsInDecision() {
        server.enqueue(new MockResponse().setResponseCode(401).setBody("unauthorized"));
        FirecrawlJobDiscoveryBudgetAdapter adapter = newAdapter();

        JobDiscoveryBudgetDecision decision = adapter.assessBudget(defaultRequest());

        assertThat(decision.toString()).doesNotContain("test-api-key");
    }

    @Test
    void assessBudget_providerAndFrameworkTypesNeverLeakIntoReasonCategory() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("error"));
        FirecrawlJobDiscoveryBudgetAdapter adapter = newAdapter();

        JobDiscoveryBudgetDecision decision = adapter.assessBudget(defaultRequest());

        assertThat(decision.reasonCategory()).doesNotContain("WebClientResponseException", "reactor.", "com.fasterxml");
    }

    // --- Fixtures --------------------------------------------------------------------------------

    private MockResponse successResponse(String body) {
        return new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body);
    }

    private String validBody() {
        return """
                {"success": true, "data": {"remainingCredits": 1000, "planCredits": 1000, \
                "billingPeriodStart": "2026-07-01T00:00:00Z", "billingPeriodEnd": "2026-07-31T23:59:59Z"}}
                """;
    }

    private JobDiscoveryBudgetRequest defaultRequest() {
        return new JobDiscoveryBudgetRequest(
                List.of(new JobSearchRequest("q1", 10), new JobSearchRequest("q2", 10), new JobSearchRequest("q3", 10)),
                5);
    }

    private FirecrawlJobDiscoveryBudgetAdapter newAdapter() {
        return newAdapter(Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(5));
    }

    private FirecrawlJobDiscoveryBudgetAdapter newAdapter(Duration connectTimeout, Duration readTimeout,
                                                            Duration creditUsageTimeout) {
        FirecrawlProperties firecrawlProperties = new FirecrawlProperties(true, "test-api-key", server.url("").toString(),
                10, connectTimeout, readTimeout, Duration.ofSeconds(1), 100_000, creditUsageTimeout,
                new FirecrawlProperties.Cost(10, 2, 1));
        JobDiscoveryProperties jobDiscoveryProperties = new JobDiscoveryProperties(true,
                new JobDiscoveryProperties.Execution(3, 5, 5, 30, 50),
                new JobDiscoveryProperties.Budget(800, 200, 15),
                new JobDiscoveryProperties.Scheduler(false, "0 0 8 * * *", "Europe/Warsaw",
                        Duration.ofHours(2), Duration.ofMinutes(1)));
        WebClient webClient = new FirecrawlWebClientConfig(firecrawlProperties).firecrawlWebClient(OBJECT_MAPPER);
        return new FirecrawlJobDiscoveryBudgetAdapter(webClient, firecrawlProperties, jobDiscoveryProperties);
    }
}
