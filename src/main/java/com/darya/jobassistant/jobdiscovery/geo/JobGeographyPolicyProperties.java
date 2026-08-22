package com.darya.jobassistant.jobdiscovery.geo;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Explicit, configurable target-geography policy for automatic discovery (Sprint 12 Part 7) -
 * currently Poland/Europe. Each entry is a free-text phrase (one or more words); {@link
 * JobGeographyPolicy} tokenizes and matches it as a phrase, not a raw substring, so a short entry
 * like {@code "eu"} cannot accidentally match inside an unrelated word.
 *
 * <p>Deliberately not gated behind {@code job-discovery.enabled} the way {@code
 * JobDiscoveryProperties} is (matching {@code JobSearchQueryPlanningProperties}'s convention): this
 * is a small, always-valid policy namespace independent of whether discovery itself is active.
 */
@ConfigurationProperties(prefix = "job-discovery.geography")
public record JobGeographyPolicyProperties(List<String> acceptedRegionTerms, List<String> incompatibleRegionTerms) {

    public JobGeographyPolicyProperties {
        acceptedRegionTerms = normalize(acceptedRegionTerms, "job-discovery.geography.accepted-region-terms");
        incompatibleRegionTerms = normalize(incompatibleRegionTerms, "job-discovery.geography.incompatible-region-terms");
    }

    private static List<String> normalize(List<String> terms, String propertyName) {
        List<String> safe = terms == null ? List.of() : terms;
        for (String term : safe) {
            if (!StringUtils.hasText(term)) {
                throw new IllegalArgumentException(propertyName + " must not contain a blank entry");
            }
        }
        return List.copyOf(safe);
    }
}
