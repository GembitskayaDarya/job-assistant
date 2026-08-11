package com.darya.jobassistant.integrations.filestorage;

/**
 * Sprint 10 Step 4 (production-readiness fix): provider-neutral result of {@link
 * FileStoragePort#load} - the bounded byte content of a previously stored file, plus enough
 * metadata for a caller to confirm integrity without re-reading it. Deliberately carries no local
 * filesystem {@code Path}/{@code File} or {@code org.springframework.core.io.Resource} - matching
 * {@link StoredFile}'s convention - so callers (a future Telegram document-delivery flow, for
 * instance) never depend on where or how a provider physically keeps bytes.
 *
 * <p>A plain in-memory {@code byte[]} is acceptable and preferred here rather than a stream/handle:
 * rendered CV/cover-letter PDFs are small (single-digit KB to low hundreds of KB), and a bounded
 * array avoids the lifecycle complexity of a caller needing to close a provider-specific stream.
 *
 * @param storageKey the key this content was loaded from (echoed back, not reinterpreted)
 * @param content the exact stored bytes
 * @param sizeBytes {@code content.length}, exposed explicitly so a caller does not need to inspect
 *     the array itself just to confirm size
 * @param sha256Checksum the loaded content's SHA-256 checksum, lowercase hex - computed fresh from
 *     {@code content}, so a caller can cross-check it against previously persisted artifact metadata
 */
public record StoredFileContent(String storageKey, byte[] content, long sizeBytes, String sha256Checksum) {

    public StoredFileContent {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("Stored file content storageKey must not be blank");
        }
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Stored file content must not be empty");
        }
        if (sizeBytes != content.length) {
            throw new IllegalArgumentException("Stored file content sizeBytes must equal content.length");
        }
        if (sha256Checksum == null || sha256Checksum.isBlank()) {
            throw new IllegalArgumentException("Stored file content sha256Checksum must not be blank");
        }
    }
}
