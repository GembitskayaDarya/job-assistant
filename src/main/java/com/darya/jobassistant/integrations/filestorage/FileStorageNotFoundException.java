package com.darya.jobassistant.integrations.filestorage;

/**
 * Thrown by {@link FileStoragePort#load} when {@code storageKey} has no content stored under it.
 * A controlled, provider-neutral outcome - never a raw {@code java.io.FileNotFoundException} or
 * other provider-specific type.
 */
public class FileStorageNotFoundException extends FileStorageException {

    public FileStorageNotFoundException(String storageKey) {
        super("No content stored at storage key '" + storageKey + "'");
    }
}
