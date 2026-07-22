package com.darya.jobassistant.integrations.jobsource.remoteok;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jobsource.remoteok")
public record RemoteOkProperties(String baseUrl) {
}