package com.darya.jobassistant.jobdiscovery.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for {@code JobSearchQueryPlanner} - purely provider-neutral policy knobs (how
 * many queries to generate, how many results to ask for per query, how many skills to fold into
 * a title-variant query). No API keys or provider-specific settings belong here; {@code
 * results-per-query} is intentionally unbounded above beyond a generous defensive ceiling of its
 * own - Firecrawl's provider-specific cap of 100 is enforced separately, downstream, by {@code
 * FirecrawlProperties.searchLimit} and {@code FirecrawlJobSearchAdapter}'s {@code min(...)}.
 */
@ConfigurationProperties(prefix = "job-discovery.query-planning")
public record JobSearchQueryPlanningProperties(int maxQueries, int resultsPerQuery, int maxSkillsPerQuery) {

    private static final int MAX_QUERIES_UPPER_BOUND = 20;
    private static final int MAX_RESULTS_PER_QUERY_UPPER_BOUND = 50;
    private static final int MAX_SKILLS_PER_QUERY_UPPER_BOUND = 10;

    public JobSearchQueryPlanningProperties {
        if (maxQueries <= 0 || maxQueries > MAX_QUERIES_UPPER_BOUND) {
            throw new IllegalArgumentException(
                    "job-discovery.query-planning.max-queries must be between 1 and "
                            + MAX_QUERIES_UPPER_BOUND + ", but was " + maxQueries);
        }
        if (resultsPerQuery <= 0 || resultsPerQuery > MAX_RESULTS_PER_QUERY_UPPER_BOUND) {
            throw new IllegalArgumentException(
                    "job-discovery.query-planning.results-per-query must be between 1 and "
                            + MAX_RESULTS_PER_QUERY_UPPER_BOUND + ", but was " + resultsPerQuery);
        }
        if (maxSkillsPerQuery <= 0 || maxSkillsPerQuery > MAX_SKILLS_PER_QUERY_UPPER_BOUND) {
            throw new IllegalArgumentException(
                    "job-discovery.query-planning.max-skills-per-query must be between 1 and "
                            + MAX_SKILLS_PER_QUERY_UPPER_BOUND + ", but was " + maxSkillsPerQuery);
        }
    }
}
