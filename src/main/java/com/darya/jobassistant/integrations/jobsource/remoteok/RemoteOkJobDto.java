package com.darya.jobassistant.integrations.jobsource.remoteok;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * RemoteOK's feed prepends a legal-notice object with none of these fields
 * set, so every field must tolerate being null.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoteOkJobDto(
        String id,
        String company,
        String position,
        String description,
        String url,
        String date,
        @JsonProperty("salary_min") BigDecimal salaryMin,
        @JsonProperty("salary_max") BigDecimal salaryMax
) {
}
