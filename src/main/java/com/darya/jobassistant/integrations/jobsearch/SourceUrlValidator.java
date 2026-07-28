package com.darya.jobassistant.integrations.jobsearch;

import java.net.URI;

/**
 * Shared absolute-URL invariant for {@link DiscoveredJobReference} and {@link JobPageContent}.
 * Deliberately minimal (no SSRF/host checks, unlike {@code VacancyUrlValidator}) - at this stage
 * these URLs only ever come from a search result or feed straight back into a fetch call that
 * isn't implemented yet, so hardening belongs with the adapter that will actually open the
 * connection.
 */
final class SourceUrlValidator {

    private SourceUrlValidator() {
    }

    static void requireValid(URI sourceUrl) {
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
    }
}
