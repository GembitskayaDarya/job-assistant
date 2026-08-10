package com.darya.jobassistant.applicationmaterials.generation.model;

import com.darya.jobassistant.candidatecontext.applicationmaterials.model.CandidateContextForApplicationMaterials;
import com.darya.jobassistant.integrations.jobsource.JobOffer;

/**
 * Sprint 10 Step 3: provider-neutral AI boundary for tailored CV/cover-letter generation - the
 * application-material counterpart of {@code JobAnalysisAiPort}. Accepts only purpose-built,
 * framework-free bounded input: the already-selected, already-version-validated {@link
 * CandidateContextForApplicationMaterials} (never a Candidate Profile/Career History aggregate or
 * the raw {@code CandidateContextSnapshot} - see {@code
 * ApplicationMaterialsCandidateContextProvider}) and a {@link JobOffer} (never a {@code Vacancy}
 * JPA entity). Implementations must never expose a Spring AI/OpenAI type through this contract -
 * enforced by an architecture test mirroring {@code AiIntegrationBoundaryArchitectureTest}.
 *
 * <p>Returns the raw, not-yet-semantically-validated {@link ApplicationMaterialsGenerationResponse}
 * - deterministic validation against the exact same {@code context} happens afterward, as a
 * separate step the caller performs (see {@code GeneratedApplicationMaterialsValidator}), never
 * inside an implementation of this method.
 *
 * @throws ApplicationMaterialsAiException if the AI provider request fails or returns a
 *     structurally malformed response
 */
public interface ApplicationMaterialsAiPort {

    ApplicationMaterialsGenerationResponse generate(CandidateContextForApplicationMaterials context, JobOffer vacancy);
}
