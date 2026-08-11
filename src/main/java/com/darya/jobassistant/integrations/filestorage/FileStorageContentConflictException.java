package com.darya.jobassistant.integrations.filestorage;

/**
 * Thrown by {@link FileStoragePort#store} when {@code storageKey} already has content stored under
 * it whose SHA-256 checksum does not match the bytes being stored now. Storage keys are
 * application-generated and deterministic (see {@code RenderApplicationMaterialsUseCase}'s storage-
 * key strategy), so this indicates a genuine conflict - never silently overwritten.
 */
public class FileStorageContentConflictException extends FileStorageException {

    public FileStorageContentConflictException(String storageKey) {
        super("Content already stored at storage key '" + storageKey + "' has a different checksum than the content being stored now");
    }
}
