package com.darya.jobassistant.integrations.jobsource;

import java.util.List;

public record JobOffer(
        String id,
        String title,
        String company,
        String location,
        String salary,
        String description,
        String url,
        String source,
        List<String> tags
) {

    public JobOffer {
        tags = tags == null
                ? List.of()
                : tags.stream().filter(tag -> tag != null).toList();
    }

    public JobOffer(String id, String title, String company, String location, String salary,
                     String description, String url, String source) {
        this(id, title, company, location, salary, description, url, source, List.of());
    }
}