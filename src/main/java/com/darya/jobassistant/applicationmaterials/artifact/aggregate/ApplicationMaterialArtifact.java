package com.darya.jobassistant.applicationmaterials.artifact.aggregate;

import com.darya.jobassistant.applicationmaterials.render.model.ApplicationMaterialFormat;
import com.darya.jobassistant.applicationmaterials.render.model.ApplicationMaterialType;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Sprint 10 Step 4: framework-free domain aggregate for one rendered document artifact's metadata -
 * backed by V24's {@code application_material_artifact} table. Never carries the document bytes
 * themselves ({@link #storageKey} is the sole pointer into {@code FileStoragePort}); PostgreSQL is
 * the source of truth for artifact facts, never for binary content.
 *
 * <p>Unlike {@code ApplicationMaterialGenerationResult}/{@code ApplicationMaterialRenderSnapshot},
 * more than one artifact may exist per generation - one per distinct {@link #materialType}/{@link
 * #format}/{@link #rendererVersion} combination (V24's {@code
 * uk_application_material_artifact_natural_key}), so the same immutable generation can accumulate a
 * CV PDF renderer v1, a CV PDF renderer v2 (a later template, no new AI call), and a Cover Letter
 * PDF renderer v1 side by side, without any of them overwriting another.
 */
public record ApplicationMaterialArtifact(
        UUID id,
        UUID generationId,
        ApplicationMaterialType materialType,
        ApplicationMaterialFormat format,
        int rendererVersion,
        String storageKey,
        String fileName,
        String contentType,
        long sizeBytes,
        String sha256Checksum,
        Instant createdAt
) {

    /** Mirrors V24's {@code chk_amart_sha256_checksum_format} exactly - lowercase hex, 64 characters. */
    private static final Pattern SHA256_HEX_FORMAT = Pattern.compile("^[a-f0-9]{64}$");

    public ApplicationMaterialArtifact {
        if (generationId == null) {
            throw new IllegalArgumentException("Application material artifact generationId must not be null");
        }
        if (materialType == null) {
            throw new IllegalArgumentException("Application material artifact materialType must not be null");
        }
        if (format == null) {
            throw new IllegalArgumentException("Application material artifact format must not be null");
        }
        if (rendererVersion <= 0) {
            throw new IllegalArgumentException("Application material artifact rendererVersion must be positive");
        }
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("Application material artifact storageKey must not be blank");
        }
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("Application material artifact fileName must not be blank");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("Application material artifact contentType must not be blank");
        }
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("Application material artifact sizeBytes must be positive");
        }
        if (sha256Checksum == null || !SHA256_HEX_FORMAT.matcher(sha256Checksum).matches()) {
            throw new IllegalArgumentException("Application material artifact sha256Checksum must be 64 lowercase hex characters");
        }
    }

    /**
     * Creates a newly rendered/stored artifact, not yet persisted ({@link #id}/{@link #createdAt}
     * are {@code null}) - the only domain-safe way to build one for {@link
     * ApplicationMaterialArtifactRepositoryPort#save}.
     */
    public static ApplicationMaterialArtifact create(
            UUID generationId, ApplicationMaterialType materialType, ApplicationMaterialFormat format, int rendererVersion,
            String storageKey, String fileName, String contentType, long sizeBytes, String sha256Checksum) {
        return new ApplicationMaterialArtifact(
                null, generationId, materialType, format, rendererVersion, storageKey, fileName, contentType,
                sizeBytes, sha256Checksum, null);
    }
}
