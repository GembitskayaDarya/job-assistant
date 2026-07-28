package com.darya.jobassistant.integrations.jobsearch;

import java.net.URI;
import org.springframework.util.StringUtils;

public record DiscoveredJobReference(URI sourceUrl, String title, String snippet) {

    public DiscoveredJobReference {
        SourceUrlValidator.requireValid(sourceUrl);
        if (!StringUtils.hasText(title)) {
            throw new IllegalArgumentException("title must not be blank");
        }
        snippet = StringUtils.hasText(snippet) ? snippet : null;
    }

    public DiscoveredJobReference(URI sourceUrl, String title) {
        this(sourceUrl, title, null);
    }
}
