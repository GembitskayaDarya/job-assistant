package com.darya.jobassistant.integrations.jobsearch.firecrawl;

import com.darya.jobassistant.config.FirecrawlProperties;
import com.darya.jobassistant.jobdiscovery.budget.JobDiscoveryBudgetDecision;
import com.darya.jobassistant.jobdiscovery.budget.JobDiscoveryBudgetPolicy;
import com.darya.jobassistant.jobdiscovery.budget.JobDiscoveryBudgetPort;
import com.darya.jobassistant.jobdiscovery.budget.JobDiscoveryBudgetRequest;
import com.darya.jobassistant.jobdiscovery.budget.JobDiscoveryCreditSnapshot;
import com.darya.jobassistant.jobdiscovery.config.JobDiscoveryProperties;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Firecrawl implementation of {@link JobDiscoveryBudgetPort}, backed by {@code GET
 * /v2/team/credit-usage}. Makes at most one HTTP request per {@link #assessBudget} call - no
 * retry, repeat, or fallback call - bounded by {@link FirecrawlProperties#creditUsageTimeout()}, a
 * dedicated per-operation Reactor timeout deliberately shorter than the shared {@code
 * firecrawlWebClient}'s transport-level {@link FirecrawlProperties#readTimeout()} so a slow budget
 * check can never consume the same time budget as a slow Search/Scrape call.
 *
 * <p>Never throws: every failure mode (bad HTTP status, decoding failure, connection error,
 * timeout, an unsuccessful/malformed/invalid provider response, or a cost-estimate/policy
 * arithmetic overflow) is converted into an {@link
 * com.darya.jobassistant.jobdiscovery.budget.JobDiscoveryBudgetStatus#UNAVAILABLE} decision here,
 * with a short sanitized {@link JobDiscoveryBudgetDecision#reasonCategory()} - never the API key,
 * the Authorization header, or the raw response body. This guarantees {@code JobDiscoveryService}
 * fails closed without needing any exception handling of its own around this port.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "firecrawl", name = "enabled", havingValue = "true")
public class FirecrawlJobDiscoveryBudgetAdapter implements JobDiscoveryBudgetPort {

    private static final String CREDIT_USAGE_PATH = "/v2/team/credit-usage";

    private final WebClient firecrawlWebClient;
    private final FirecrawlProperties firecrawlProperties;
    private final JobDiscoveryProperties jobDiscoveryProperties;

    public FirecrawlJobDiscoveryBudgetAdapter(@Qualifier("firecrawlWebClient") WebClient firecrawlWebClient,
                                               FirecrawlProperties firecrawlProperties,
                                               JobDiscoveryProperties jobDiscoveryProperties) {
        this.firecrawlWebClient = firecrawlWebClient;
        this.firecrawlProperties = firecrawlProperties;
        this.jobDiscoveryProperties = jobDiscoveryProperties;
    }

    @Override
    public JobDiscoveryBudgetDecision assessBudget(JobDiscoveryBudgetRequest request) {
        JobDiscoveryProperties.Budget budget = jobDiscoveryProperties.budget();

        FirecrawlCreditCostEstimator.Estimate estimate;
        try {
            estimate = FirecrawlCreditCostEstimator.estimate(
                    request.searchRequests(), request.maxScrapesPerRun(), firecrawlProperties.cost());
        } catch (ArithmeticException e) {
            log.warn("Job discovery budget check unavailable - reason=ESTIMATE_OVERFLOW");
            return unavailable(0, 0, budget, "ESTIMATE_OVERFLOW");
        }

        FirecrawlCreditUsageResponseDto response;
        try {
            response = fetchCreditUsage();
        } catch (RuntimeException e) {
            String reason = classifyFailure(e);
            log.warn("Job discovery budget check unavailable - reason={}", reason);
            return unavailable(estimate.searchCredits(), estimate.scrapeCredits(), budget, reason);
        }

        JobDiscoveryCreditSnapshot snapshot;
        try {
            snapshot = toSnapshot(response);
        } catch (RuntimeException e) {
            log.warn("Job discovery budget check unavailable - reason=INVALID_PROVIDER_RESPONSE");
            return unavailable(estimate.searchCredits(), estimate.scrapeCredits(), budget, "INVALID_PROVIDER_RESPONSE");
        }

        try {
            return JobDiscoveryBudgetPolicy.decide(
                    estimate.searchCredits(), estimate.scrapeCredits(), estimate.totalCredits(), budget, snapshot);
        } catch (ArithmeticException e) {
            log.warn("Job discovery budget check unavailable - reason=ESTIMATE_OVERFLOW");
            return unavailable(estimate.searchCredits(), estimate.scrapeCredits(), budget, "ESTIMATE_OVERFLOW");
        }
    }

    private JobDiscoveryBudgetDecision unavailable(long estimatedSearchCredits, long estimatedScrapeCredits,
                                                     JobDiscoveryProperties.Budget budget, String reasonCategory) {
        return JobDiscoveryBudgetDecision.unavailable(estimatedSearchCredits, estimatedScrapeCredits,
                budget.monthlyCreditLimit(), budget.reserveCredits(), budget.maxEstimatedCreditsPerRun(), reasonCategory);
    }

    private FirecrawlCreditUsageResponseDto fetchCreditUsage() {
        FirecrawlCreditUsageResponseDto response = firecrawlWebClient.get()
                .uri(CREDIT_USAGE_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + firecrawlProperties.apiKey())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(FirecrawlCreditUsageResponseDto.class)
                .timeout(firecrawlProperties.creditUsageTimeout())
                .block();
        if (response == null) {
            throw new IllegalStateException("Firecrawl credit-usage response could not be parsed");
        }
        return response;
    }

    private String classifyFailure(RuntimeException e) {
        if (containsTimeoutCause(e)) {
            return "TIMEOUT";
        }
        if (e instanceof WebClientResponseException responseException) {
            return "HTTP_" + responseException.getStatusCode().value();
        }
        if (e instanceof WebClientRequestException) {
            return "CONNECTION_ERROR";
        }
        if (e instanceof DecodingException) {
            return "DECODING_ERROR";
        }
        return "UNEXPECTED_ERROR";
    }

    private boolean containsTimeoutCause(Throwable throwable) {
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 10) {
            if (current instanceof ReadTimeoutException
                    || current instanceof ConnectTimeoutException
                    || current instanceof TimeoutException) {
                return true;
            }
            current = current.getCause();
            depth++;
        }
        return false;
    }

    private JobDiscoveryCreditSnapshot toSnapshot(FirecrawlCreditUsageResponseDto response) {
        if (response.success() == null || !response.success()) {
            throw new IllegalArgumentException("Firecrawl returned an unsuccessful credit-usage response");
        }
        FirecrawlCreditUsageResponseDto.Data data = response.data();
        if (data == null) {
            throw new IllegalArgumentException("Firecrawl credit-usage response is missing data");
        }
        if (data.remainingCredits() == null) {
            throw new IllegalArgumentException("Firecrawl credit-usage response is missing remainingCredits");
        }
        if (data.planCredits() == null) {
            throw new IllegalArgumentException("Firecrawl credit-usage response is missing planCredits");
        }
        Instant billingPeriodStart = parseInstant(data.billingPeriodStart());
        Instant billingPeriodEnd = parseInstant(data.billingPeriodEnd());
        return new JobDiscoveryCreditSnapshot(
                data.remainingCredits(), data.planCredits(), billingPeriodStart, billingPeriodEnd);
    }

    private Instant parseInstant(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("Firecrawl credit-usage response is missing a billing period timestamp");
        }
        try {
            return Instant.parse(rawValue);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Firecrawl credit-usage response has an invalid billing period timestamp", e);
        }
    }
}
