package com.darya.jobassistant.applicationmaterials.render.model;

/**
 * Sprint 10 Step 4: the complete immutable render snapshot content - everything {@code
 * ApplicationMaterialDocumentRendererPort} needs to produce a CV and cover letter document, and
 * nothing else. Built exactly once by {@code RenderModelAssembler} and persisted verbatim as {@code
 * ApplicationMaterialRenderSnapshot#content()} - see that aggregate's javadoc for why this must be
 * frozen rather than re-resolved on every render.
 */
public record RenderableApplicationMaterials(RenderableCv cv, RenderableCoverLetter coverLetter) {

    public RenderableApplicationMaterials {
        if (cv == null) {
            throw new IllegalArgumentException("Renderable application materials cv must not be null");
        }
        if (coverLetter == null) {
            throw new IllegalArgumentException("Renderable application materials coverLetter must not be null");
        }
    }
}
