package com.darya.jobassistant.candidates.migration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for {@link CandidateProfileMigrationRunner} - matching every other maintenance-tool
 * {@code @ConfigurationProperties} record in this project ({@code
 * VacancyCanonicalUrlBackfillProperties}, {@code VacancyCanonicalUrlAuditProperties}), the
 * application must keep starting with no migration configuration at all, which is the default:
 * {@link CandidateProfileMigrationRunner} itself only exists as a bean when {@code
 * candidate-profile.migration.mode} is present (see its {@code @ConditionalOnProperty}), and even
 * then does nothing when the value is {@link CandidateProfileMigrationRunnerMode#OFF}.
 */
@ConfigurationProperties(prefix = "candidate-profile.migration")
public record CandidateProfileMigrationProperties(
        CandidateProfileMigrationRunnerMode mode,
        @DefaultValue("primary") String profileKey
) {
}
