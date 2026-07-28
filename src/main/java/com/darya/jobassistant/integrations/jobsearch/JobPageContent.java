package com.darya.jobassistant.integrations.jobsearch;

import java.net.URI;
import org.springframework.util.StringUtils;

public record JobPageContent(URI sourceUrl, String content, String title) {

    public JobPageContent {
        SourceUrlValidator.requireValid(sourceUrl);
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("content must not be blank");
        }
        title = StringUtils.hasText(title) ? title : null;
    }

    public JobPageContent(URI sourceUrl, String content) {
        this(sourceUrl, content, null);
    }
}
