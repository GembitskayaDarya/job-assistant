package com.darya.jobassistant.applicationmaterials.render.model;

import java.util.List;

/**
 * Sprint 10 Step 4: the bytes produced by {@link ApplicationMaterialDocumentRendererPort}, plus
 * their MIME content type. Never a {@code java.io.File}/{@code Path} - the renderer knows nothing
 * about storage.
 *
 * <p>Sprint 11 Golden Master Template Rendering: {@link #renderedCvSkills} is the exact Technical
 * Skills list that ended up drawn - only ever non-empty for a CV render, and only ever different
 * from the {@code TailoredCvDocument#skills()} the renderer was given when the golden master
 * template's display-fit cap dropped one or more lowest-priority skills to keep the list on one
 * physical line (see {@code GoldenMasterCvSkillsFitPolicy}). {@code RenderApplicationMaterialsUseCase}
 * uses this - not the original document's skills - for ATS verification, so a display-fit drop can
 * never be misreported as a missing skill term. Empty for a cover letter render, and for any CV
 * render where the given skills already fit unchanged.
 */
public record RenderedDocument(byte[] content, String contentType, List<String> renderedCvSkills) {

    public RenderedDocument {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Rendered document content must not be empty");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("Rendered document contentType must not be blank");
        }
        renderedCvSkills = renderedCvSkills == null ? List.of() : List.copyOf(renderedCvSkills);
    }

    /** Convenience constructor for a render with no Technical Skills reconciliation concern (a cover letter, or any non-golden-master render). */
    public RenderedDocument(byte[] content, String contentType) {
        this(content, contentType, List.of());
    }
}
