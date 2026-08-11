package com.darya.jobassistant.integrations.filestorage.local;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Sprint 10 Step 4: configuration for {@link LocalFileStorageAdapter} - the local filesystem root
 * every storage key is resolved beneath. Always eagerly bound and validated (matching {@code
 * CandidateProfileRuntimeProperties}'s convention): {@link #rootDirectory} always has a value
 * (defaults to {@code ./data/application-materials}, relative to the process working directory),
 * so failing fast on a blank override is exactly the desired behavior.
 */
@ConfigurationProperties(prefix = "application-materials.storage.local")
@Validated
public record LocalFileStorageProperties(
        @NotBlank @DefaultValue("./data/application-materials") String rootDirectory
) {
}
