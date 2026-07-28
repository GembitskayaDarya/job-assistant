package com.darya.jobassistant.integrations.jobsearch;

import org.springframework.util.StringUtils;

public record JobSearchRequest(String query, int maxResults) {

    public JobSearchRequest {
        if (!StringUtils.hasText(query)) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (maxResults <= 0) {
            throw new IllegalArgumentException("maxResults must be positive");
        }
    }
}
