package com.darya.jobassistant.applicationmaterials.generation.model;

import com.darya.jobassistant.candidatecontext.applicationmaterials.model.CandidateContextForApplicationMaterials;
import com.darya.jobassistant.integrations.jobsource.JobOffer;

/**
 * Sprint 10 Step 3 (Sprint 11 Big Block 7 correction): provider-neutral AI boundary for tailored
 * cover-letter generation - the application-material counterpart of {@code JobAnalysisAiPort}.
 * Accepts only purpose-built, framework-free bounded input: the already-selected, already-version-
 * validated {@link CandidateContextForApplicationMaterials} (never a Candidate Profile/Career
 * History aggregate or the raw {@code CandidateContextSnapshot} - see {@code
 * ApplicationMaterialsCandidateContextProvider}) and a {@link JobOffer} (never a {@code Vacancy}
 * JPA entity). Implementations must never expose a Spring AI/OpenAI type through this contract -
 * enforced by an architecture test mirroring {@code AiIntegrationBoundaryArchitectureTest}.
 *
 * <p><strong>Cover letter only, as of Sprint 11 Big Block 7.</strong> This port previously also
 * generated a full CV ({@code GeneratedCv}); that responsibility now belongs entirely to the
 * constrained Sprint 11 pipeline ({@code CvTailoringAiPort} -&gt; {@code CvTailoringResult} -&gt;
 * {@code CvTailoringValidator} -&gt; {@code CvAssembler} -&gt; {@code TailoredCvDocument}) - see
 * {@code CvTailoringUseCase}. Kept for cover-letter generation specifically, per this block's
 * explicit "clearly separate structured CV tailoring from cover-letter prose generation" directive:
 * a cover letter is unstructured persuasive prose with no factual-selection contract to enforce the
 * way a CV's career-history references do, so the original bounded-context-plus-provenance-ids
 * design already fits it well and does not need to be replaced.
 *
 * <p>Returns the raw, not-yet-semantically-validated {@link ApplicationMaterialsGenerationResponse}
 * - deterministic validation against the exact same {@code context} happens afterward, as a
 * separate step the caller performs (see {@code GeneratedCoverLetterValidator}), never inside an
 * implementation of this method.
 *
 * @throws ApplicationMaterialsAiException if the AI provider request fails or returns a
 *     structurally malformed response
 */
public interface ApplicationMaterialsAiPort {

    ApplicationMaterialsGenerationResponse generate(CandidateContextForApplicationMaterials context, JobOffer vacancy);
}
