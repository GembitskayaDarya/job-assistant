package com.darya.jobassistant.integrations.jobsource;

public record JobOffer(
        String id,
        String title,
        String company,
        String location,
        String salary,
        String description,
        String url,
        String source
) {
}
