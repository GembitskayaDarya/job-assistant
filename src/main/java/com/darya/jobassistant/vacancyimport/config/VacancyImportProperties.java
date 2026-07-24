package com.darya.jobassistant.vacancyimport.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vacancy-import")
public record VacancyImportProperties(Duration sessionTtl) {

    public VacancyImportProperties {
        if (sessionTtl == null || !sessionTtl.isPositive()) {
            throw new IllegalArgumentException("vacancy-import.session-ttl must be positive");
        }
    }
}
