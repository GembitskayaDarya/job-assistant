package com.darya.jobassistant.applicationmaterials.render.model;

/**
 * Sprint 10 Step 4: the bytes produced by {@link ApplicationMaterialDocumentRendererPort}, plus
 * their MIME content type. Never a {@code java.io.File}/{@code Path} - the renderer knows nothing
 * about storage.
 */
public record RenderedDocument(byte[] content, String contentType) {

    public RenderedDocument {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Rendered document content must not be empty");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("Rendered document contentType must not be blank");
        }
    }
}
