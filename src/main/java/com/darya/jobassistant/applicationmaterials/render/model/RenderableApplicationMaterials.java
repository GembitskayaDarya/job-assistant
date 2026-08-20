package com.darya.jobassistant.applicationmaterials.render.model;

import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvDocument;

/**
 * Sprint 10 Step 4 (Sprint 11 Big Block 7 correction): the complete immutable render snapshot
 * content - everything {@code ApplicationMaterialDocumentRendererPort} needs to produce a CV and
 * cover letter document, and nothing else. Built once - {@link #cv} directly by {@code
 * CvTailoringUseCase}/{@code CvAssembler} (Sprint 11), {@link #coverLetter} by {@code
 * RenderModelAssembler} (Sprint 10) - and persisted verbatim as {@code
 * ApplicationMaterialRenderSnapshot#content()} - see that aggregate's javadoc for why this must be
 * frozen rather than re-resolved on every render.
 */
public record RenderableApplicationMaterials(TailoredCvDocument cv, RenderableCoverLetter coverLetter) {

    public RenderableApplicationMaterials {
        if (cv == null) {
            throw new IllegalArgumentException("Renderable application materials cv must not be null");
        }
        if (coverLetter == null) {
            throw new IllegalArgumentException("Renderable application materials coverLetter must not be null");
        }
    }
}
