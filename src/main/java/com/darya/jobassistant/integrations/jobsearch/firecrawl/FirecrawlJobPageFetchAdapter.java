package com.darya.jobassistant.integrations.jobsearch.firecrawl;

import com.darya.jobassistant.config.FirecrawlProperties;
import com.darya.jobassistant.integrations.jobsearch.JobDiscoveryException;
import com.darya.jobassistant.integrations.jobsearch.JobPageContent;
import com.darya.jobassistant.integrations.jobsearch.JobPageFetchPort;
import com.darya.jobassistant.integrations.jobsearch.SourceUrlValidator;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import java.net.URI;
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
 * Firecrawl implementation of {@link JobPageFetchPort}, backed by {@code POST /v2/scrape}. Fetches
 * the clean Markdown of exactly one job page - {@code formats: ["markdown"]} only, {@code proxy}
 * pinned to {@code "basic"} (never Firecrawl's default, {@code "auto"}, or {@code "enhanced"}), no
 * {@code actions}/{@code screenshot} options. One {@link #fetch(URI)} call makes at most one HTTP
 * request: there is no retry or proxy-escalation logic here, by design - that belongs to a later
 * resilience step.
 *
 * <p>The {@code timeout} sent to Firecrawl comes from {@link FirecrawlProperties#scrapeTimeout()},
 * a server-side budget for how long Firecrawl may spend rendering the page. It is a different
 * timeout from {@link FirecrawlProperties#readTimeout()}, which bounds the shared {@code
 * firecrawlWebClient}'s transport-level wait for a response; {@code FirecrawlProperties} validates
 * that the latter is strictly greater than the former so this adapter is never cut off by the
 * client before Firecrawl's own, longer timeout could have returned.
 *
 * <p>Every Spring/reactor-netty exception this adapter can encounter (bad status, decoding
 * failure, connection error, timeout) is translated to {@link JobDiscoveryException} with a
 * sanitized message before it can cross the {@link JobPageFetchPort} boundary - never the API key,
 * the Authorization header, the full response body, or the scraped Markdown itself.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "firecrawl", name = "enabled", havingValue = "true")
public class FirecrawlJobPageFetchAdapter implements JobPageFetchPort {

    private static final String SCRAPE_PATH = "/v2/scrape";
    private static final List<String> MARKDOWN_FORMAT = List.of("markdown");
    private static final String BASIC_PROXY = "basic";

    private final WebClient firecrawlWebClient;
    private final FirecrawlProperties firecrawlProperties;

    public FirecrawlJobPageFetchAdapter(@Qualifier("firecrawlWebClient") WebClient firecrawlWebClient,
                                         FirecrawlProperties firecrawlProperties) {
        this.firecrawlWebClient = firecrawlWebClient;
        this.firecrawlProperties = firecrawlProperties;
    }

    @Override
    public JobPageContent fetch(URI sourceUrl) {
        requireValidUrl(sourceUrl);

        int timeoutMillis = Math.toIntExact(firecrawlProperties.scrapeTimeout().toMillis());
        FirecrawlScrapeRequestDto requestBody = new FirecrawlScrapeRequestDto(
                sourceUrl.toString(), MARKDOWN_FORMAT, true, false, true, true, BASIC_PROXY, timeoutMillis);

        FirecrawlScrapeResponseDto response = executeScrape(requestBody);

        return toJobPageContent(response, sourceUrl);
    }

    private void requireValidUrl(URI sourceUrl) {
        try {
            SourceUrlValidator.requireValid(sourceUrl);
        } catch (IllegalArgumentException e) {
            throw new JobDiscoveryException("Firecrawl page fetch rejected an invalid source URL: " + e.getMessage());
        }
    }

    private FirecrawlScrapeResponseDto executeScrape(FirecrawlScrapeRequestDto requestBody) {
        try {
            FirecrawlScrapeResponseDto response = firecrawlWebClient.post()
                    .uri(SCRAPE_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + firecrawlProperties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse -> Mono.error(new JobDiscoveryException(
                            "Firecrawl page fetch failed with HTTP status " + clientResponse.statusCode().value())))
                    .bodyToMono(FirecrawlScrapeResponseDto.class)
                    .block();
            if (response == null) {
                throw new JobDiscoveryException("Firecrawl page fetch response could not be parsed");
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
                throw new JobDiscoveryException("Firecrawl page fetch timed out", e);
            }
            if (e instanceof WebClientResponseException responseException) {
                throw new JobDiscoveryException(
                        "Firecrawl page fetch failed with HTTP status " + responseException.getStatusCode().value(), e);
            }
            if (e instanceof WebClientRequestException) {
                throw new JobDiscoveryException("Firecrawl page fetch failed due to a connection error", e);
            }
            if (e instanceof DecodingException) {
                throw new JobDiscoveryException("Firecrawl page fetch response could not be parsed", e);
            }
            throw new JobDiscoveryException("Firecrawl page fetch failed unexpectedly", e);
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

    private JobPageContent toJobPageContent(FirecrawlScrapeResponseDto response, URI sourceUrl) {
        if (response.success() == null || !response.success()) {
            throw new JobDiscoveryException("Firecrawl returned an unsuccessful page fetch response");
        }
        if (response.data() == null) {
            throw new JobDiscoveryException("Firecrawl page fetch response could not be parsed");
        }
        String markdown = response.data().markdown();
        if (!StringUtils.hasText(markdown)) {
            throw new JobDiscoveryException("Firecrawl page fetch returned blank content");
        }
        int maxMarkdownChars = firecrawlProperties.maxMarkdownChars();
        if (markdown.length() > maxMarkdownChars) {
            throw new JobDiscoveryException("Firecrawl page fetch content exceeded the configured limit ("
                    + markdown.length() + " > " + maxMarkdownChars + " characters)");
        }

        log.debug("Firecrawl scrape succeeded operation=scrape host={} outcome=success chars={}",
                sourceUrl.getHost(), markdown.length());
        return new JobPageContent(sourceUrl, markdown);
    }
}
