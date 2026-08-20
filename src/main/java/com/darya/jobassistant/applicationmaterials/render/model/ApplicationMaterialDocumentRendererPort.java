package com.darya.jobassistant.applicationmaterials.render.model;

import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvDocument;

/**
 * Sprint 10 Step 4 (Sprint 11 Big Block 7 correction): provider-neutral document-rendering boundary -
 * the render counterpart of {@code ApplicationMaterialsAiPort}. An implementation converts an
 * already-assembled, trusted {@link TailoredCvDocument}/{@link RenderableCoverLetter} into document
 * bytes; it must never query a repository, call OpenAI, access Telegram, write to the filesystem, or
 * know anything about a storage root path - see {@code PdfBoxApplicationMaterialDocumentRenderer}'s
 * javadoc, and the architecture test that enforces this boundary.
 *
 * <p>{@link #renderCv} now takes {@link TailoredCvDocument} directly - Sprint 11's {@code
 * CvAssembler} output is already the complete, canonical, presentation-neutral CV content, so no
 * intermediate {@code Renderable*} CV type is needed any more (the old {@code RenderableCv}/{@code
 * RenderableCvExperience}/{@code RenderableCvProject} types are removed; {@link RenderableCoverLetter}
 * is unchanged - cover-letter generation still goes through the Sprint 10 AI path, see {@code
 * ApplicationMaterialsAiPort}).
 *
 * <p>Two explicit methods rather than one method keyed by {@link ApplicationMaterialType}: a CV and
 * a cover letter are structurally different inputs, and this avoids any runtime "wrong renderable
 * type for this material type" ambiguity entirely.
 *
 * @throws DocumentRenderingException if the input cannot be rendered
 */
public interface ApplicationMaterialDocumentRendererPort {

    RenderedDocument renderCv(TailoredCvDocument cv);

    RenderedDocument renderCoverLetter(RenderableCoverLetter coverLetter);
}
