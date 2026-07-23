package com.darya.jobassistant.candidates.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "candidate")
@Validated
public record CandidateProfileProperties(
        @NotBlank String targetRole,
        @NotNull List<String> skills,
        @NotNull List<String> languages,
        @PositiveOrZero int experienceYears,
        String preferredCompanyType,
        String preferredLocation
) {
    public CandidateProfileProperties {
        skills = skills == null ? List.of() : List.copyOf(skills);
        languages = languages == null ? List.of() : List.copyOf(languages);
    }
}
