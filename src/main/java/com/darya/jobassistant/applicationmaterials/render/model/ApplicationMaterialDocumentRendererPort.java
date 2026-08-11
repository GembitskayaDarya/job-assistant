package com.darya.jobassistant.applicationmaterials.render.model;

/**
 * Sprint 10 Step 4: provider-neutral document-rendering boundary - the render counterpart of
 * {@code ApplicationMaterialsAiPort}. An implementation converts an already-assembled, trusted
 * {@link RenderableCv}/{@link RenderableCoverLetter} into document bytes; it must never query a
 * repository, call OpenAI, access Telegram, write to the filesystem, or know anything about a
 * storage root path - see {@code PdfBoxApplicationMaterialDocumentRenderer}'s javadoc, and the
 * architecture test that enforces this boundary.
 *
 * <p>Two explicit methods rather than one method keyed by {@link ApplicationMaterialType}: a CV and
 * a cover letter are structurally different inputs, and this avoids any runtime "wrong renderable
 * type for this material type" ambiguity entirely.
 *
 * @throws DocumentRenderingException if the input cannot be rendered
 */
public interface ApplicationMaterialDocumentRendererPort {

    RenderedDocument renderCv(RenderableCv cv);

    RenderedDocument renderCoverLetter(RenderableCoverLetter coverLetter);
}
