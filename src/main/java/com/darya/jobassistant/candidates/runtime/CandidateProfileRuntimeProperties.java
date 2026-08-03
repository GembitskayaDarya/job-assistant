package com.darya.jobassistant.candidates.runtime;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for {@link PersistentCandidateProfileProvider} - the business key its {@code
 * getProfile()} looks the persistent Candidate Profile up by. Deliberately separate from {@code
 * CandidateProfileMigrationProperties} (migration mode/profile key): runtime configuration must
 * never depend on migration-specific classes, per Sprint 9 Step 4's dependency-direction rule.
 *
 * <p>Unlike {@code CandidateProfileProperties} (Sprint 9 Step 4: unvalidated, since the YAML file
 * it binds is now optional), this bean is always eagerly bound and validated: {@link #profileKey}
 * always has a value (defaults to {@code "primary"}), so failing fast on a blank override is
 * exactly the desired behavior, not a normal-runtime obstacle.
 */
@ConfigurationProperties(prefix = "candidate-profile.runtime")
@Validated
public record CandidateProfileRuntimeProperties(
        @NotBlank @DefaultValue("primary") String profileKey
) {
}
