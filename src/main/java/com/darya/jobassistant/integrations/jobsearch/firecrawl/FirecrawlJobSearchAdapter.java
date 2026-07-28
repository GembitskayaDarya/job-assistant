package com.darya.jobassistant.integrations.jobsearch.firecrawl;

import com.darya.jobassistant.config.FirecrawlProperties;
import com.darya.jobassistant.integrations.jobsearch.DiscoveredJobReference;
import com.darya.jobassistant.integrations.jobsearch.JobDiscoveryException;
import com.darya.jobassistant.integrations.jobsearch.JobSearchPort;
import com.darya.jobassistant.integrations.jobsearch.JobSearchRequest;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * Firecrawl implementation of {@link JobSearchPort}, backed by {@code POST /v2/search}. Requests
 * only lightweight web-search references ({@code sources: ["web"]}, no {@code scrapeOptions}) -
 * page scraping is a separate, not-yet-implemented capability behind {@code JobPageFetchPort}.
 *
 * <p>Every Spring/reactor-netty exception this adapter can encounter (bad status, decoding
 * failure, connection error, timeout) is translated to {@link JobDiscoveryException} with a
 * sanitized message before it can cross the {@link JobSearchPort} boundary - never the API key,
 * the Authorization header, the full response body, or the complete query.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "firecrawl", name = "enabled", havingValue = "true")
public class FirecrawlJobSearchAdapter implements JobSearchPort {

    private static final String SEARCH_PATH = "/v2/search";
    private static final List<String> WEB_SOURCE = List.of("web");

    private final WebClient firecrawlWebClient;
    private final FirecrawlProperties firecrawlProperties;

    public FirecrawlJobSearchAdapter(@Qualifier("firecrawlWebClient") WebClient firecrawlWebClient,
                                      FirecrawlProperties firecrawlProperties) {
        this.firecrawlWebClient = firecrawlWebClient;
        this.firecrawlProperties = firecrawlProperties;
    }

    @Override
    public List<DiscoveredJobReference> search(JobSearchRequest request) {
        int effectiveLimit = Math.min(request.maxResults(), firecrawlProperties.searchLimit());
        FirecrawlSearchRequestDto requestBody =
                new FirecrawlSearchRequestDto(request.query(), effectiveLimit, WEB_SOURCE, true);

        FirecrawlSearchResponseDto response = executeSearch(requestBody);

        if (response.success() == null || !response.success()) {
            throw new JobDiscoveryException("Firecrawl returned an unsuccessful search response");
        }
        if (response.data() == null || response.data().web() == null) {
            throw new JobDiscoveryException("Firecrawl job search response could not be parsed");
        }

        return mapResults(response.data().web());
    }

    private FirecrawlSearchResponseDto executeSearch(FirecrawlSearchRequestDto requestBody) {
        try {
            FirecrawlSearchResponseDto response = firecrawlWebClient.post()
                    .uri(SEARCH_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + firecrawlProperties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse -> Mono.error(new JobDiscoveryException(
                            "Firecrawl job search failed with HTTP status " + clientResponse.statusCode().value())))
                    .bodyToMono(FirecrawlSearchResponseDto.class)
                    .block();
            if (response == null) {
                throw new JobDiscoveryException("Firecrawl job search response could not be parsed");
            }
            return response;
        } catch (JobDiscoveryException e) {
            throw e;
        } catch (RuntimeException e) {
            // Checked ahead of the specific exception types below: a stalled body read after a
            // 200 response can surface as a WebClientResponseException carrying that original
            // status rather than as a WebClientRequestException, so the cause chain - not the
            // outer exception's type - is the only reliable signal that this was actually a
            // read/connect timeout.
            if (containsTimeoutCause(e)) {
                throw new JobDiscoveryException("Firecrawl job search timed out", e);
            }
            if (e instanceof WebClientResponseException responseException) {
                throw new JobDiscoveryException(
                        "Firecrawl job search failed with HTTP status " + responseException.getStatusCode().value(), e);
            }
            if (e instanceof WebClientRequestException) {
                throw new JobDiscoveryException("Firecrawl job search failed due to a connection error", e);
            }
            if (e instanceof DecodingException) {
                throw new JobDiscoveryException("Firecrawl job search response could not be parsed", e);
            }
            throw new JobDiscoveryException("Firecrawl job search failed unexpectedly", e);
        }
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

    private List<DiscoveredJobReference> mapResults(List<FirecrawlSearchResponseDto.WebResult> results) {
        List<DiscoveredJobReference> references = new ArrayList<>();
        int skipped = 0;
        for (FirecrawlSearchResponseDto.WebResult result : results) {
            DiscoveredJobReference reference = toReference(result);
            if (reference == null) {
                skipped++;
                continue;
            }
            references.add(reference);
        }
        if (skipped > 0) {
            log.info("Skipped {} malformed Firecrawl search result(s)", skipped);
        }
        return List.copyOf(references);
    }

    private DiscoveredJobReference toReference(FirecrawlSearchResponseDto.WebResult result) {
        if (result == null) {
            return null;
        }
        URI sourceUrl = parseUri(result.url());
        if (sourceUrl == null) {
            return null;
        }
        try {
            return new DiscoveredJobReference(sourceUrl, result.title(), result.description());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private URI parseUri(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            return null;
        }
        try {
            return new URI(rawUrl);
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
