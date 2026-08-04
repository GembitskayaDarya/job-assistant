package com.darya.jobassistant.careerhistory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the Career History import workflow (Sprint 9 Step 7) - matching {@code
 * CandidateProfileMigrationProperties}'s convention, this bean is unconditional (always
 * registered) so an unrecognized {@code mode} value fails config binding clearly regardless of
 * whether import is actually active, while {@code YamlCareerHistoryImportSource}/{@code
 * CareerHistoryImportRunner} themselves only exist as beans for {@link
 * CareerHistoryImportRunnerMode#DRY_RUN}/{@link CareerHistoryImportRunnerMode#APPLY} (see {@link
 * CareerHistoryImportActiveCondition}).
 *
 * <p>Deliberately unvalidated here: {@code source} may be blank and {@code maxFileSizeBytes} may
 * be non-positive while {@code mode=OFF} (or absent) without failing application startup - normal
 * runtime must never depend on this file existing or these values being sensible.
 * {@code YamlCareerHistoryImportSource}, which only exists while import is actually active,
 * performs that validation explicitly and fails clearly there instead.
 */
@ConfigurationProperties(prefix = "career-history.import")
public record CareerHistoryImportProperties(
        CareerHistoryImportRunnerMode mode,
        String source,
        @DefaultValue("5242880") long maxFileSizeBytes
) {
}
