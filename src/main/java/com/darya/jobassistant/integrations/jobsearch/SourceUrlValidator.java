package com.darya.jobassistant.integrations.jobsearch;

import java.net.URI;

/**
 * Shared absolute-URL invariant for {@link DiscoveredJobReference}, {@link JobPageContent}, and
 * the {@code JobPageFetchPort} adapter(s) that populate it before making any HTTP call. Public so
 * it can be reused from the {@code integrations.jobsearch.firecrawl} subpackage instead of being
 * duplicated there.
 *
 * <p>Deliberately minimal (no SSRF/private-IP checks, unlike {@code VacancyUrlValidator}) beyond
 * requiring a host and rejecting embedded user-info - full SSRF hardening (rejecting loopback/
 * private/link-local literals) belongs with the adapter that will actually open the connection,
 * not this shared, provider-neutral shape check.
 */
public final class SourceUrlValidator {

    private SourceUrlValidator() {
    }

    public static void requireValid(URI sourceUrl) {
        if (sourceUrl == null) {
            throw new IllegalArgumentException("sourceUrl must not be null");
        }
        if (!sourceUrl.isAbsolute()) {
            throw new IllegalArgumentException("sourceUrl must be an absolute URL");
        }
        String scheme = sourceUrl.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("sourceUrl must use http or https");
        }
        if (sourceUrl.getUserInfo() != null) {
            throw new IllegalArgumentException("sourceUrl must not contain user information");
        }
        String host = sourceUrl.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("sourceUrl must contain a host");
        }
    }
}
