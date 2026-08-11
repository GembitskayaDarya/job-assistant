package com.darya.jobassistant.integrations.filestorage;

/**
 * Provider-neutral file-storage failure. Never carries a local filesystem absolute path, a raw
 * {@code IOException} message with infrastructure detail beyond what is safe to log, or any other
 * provider-specific type - only a safe message plus (where one exists) the original cause,
 * matching {@code ApplicationMaterialsAiException}/{@code DocumentRenderingException}'s convention.
 */
public class FileStorageException extends RuntimeException {

    public FileStorageException(String message) {
        super(message);
    }

    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
