package com.darya.jobassistant.jobdiscovery;

/**
 * One sanitized, bounded failure record from a discovery run - see {@link JobDiscoveryRunResult}
 * for how the total count is capped by {@code maxReportedIssues}. Deliberately carries only safe,
 * low-cardinality data: never a full URL query string, scraped Markdown, search snippet, AI
 * output, provider error body, API key, Authorization header, or raw stack trace. {@link
 * #sanitizedErrorCategory} is the failing exception's simple class name (e.g. {@code
 * "JobDiscoveryException"}, {@code "VacancyExtractionException"}) - already a safe, bounded,
 * content-free string - never {@code getMessage()} or a stack trace.
 *
 * @param category stage/category this issue occurred in
 * @param queryOrdinal 0-based index of the planned query this issue relates to, or {@code null}
 *     when not query-scoped
 * @param sourceHost the reference's host, when safely available (never a full URL/query string),
 *     or {@code null}
 * @param sanitizedErrorCategory the failing exception's simple class name
 * @param referenceOrdinal 0-based index of the reference (across the whole run) this issue relates
 *     to, or {@code null} when not reference-scoped
 */
public record JobDiscoveryIssue(
        JobDiscoveryIssueCategory category,
        Integer queryOrdinal,
        String sourceHost,
        String sanitizedErrorCategory,
        Integer referenceOrdinal
) {
    public JobDiscoveryIssue {
        if (category == null) {
            throw new IllegalArgumentException("category must not be null");
        }
    }
}
