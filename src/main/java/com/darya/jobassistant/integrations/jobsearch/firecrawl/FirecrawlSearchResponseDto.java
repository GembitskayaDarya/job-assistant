package com.darya.jobassistant.integrations.jobsearch.firecrawl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Firecrawl {@code POST /v2/search} response shape. Only the fields the adapter actually maps are
 * declared - {@code warning}, {@code id}, and {@code creditsUsed} are intentionally left out and
 * tolerated via {@code ignoreUnknown} rather than modeled, since they must never reach the
 * provider-neutral application contracts.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FirecrawlSearchResponseDto(Boolean success, Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(List<WebResult> web) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WebResult(String url, String title, String description) {
    }
}
