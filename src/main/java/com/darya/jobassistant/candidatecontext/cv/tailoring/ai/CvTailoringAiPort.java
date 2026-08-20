package com.darya.jobassistant.candidatecontext.cv.tailoring.ai;

import com.darya.jobassistant.candidatecontext.cv.model.CvSourceSnapshot;
import com.darya.jobassistant.candidatecontext.cv.tailoring.CvTailoringResult;
import com.darya.jobassistant.integrations.jobsource.JobOffer;

/**
 * Sprint 11 Big Block 6: the provider-neutral application/domain boundary for AI-assisted CV
 * tailoring - the {@code CvSourceSnapshot} counterpart of {@code
 * applicationmaterials.generation.model.ApplicationMaterialsAiPort}. Accepts only the complete,
 * framework-free {@link CvSourceSnapshot} (never a {@code CandidateProfileAggregate}/{@code
 * CareerHistoryAggregate}/{@code PersonalProject} or the raw {@code CandidateContextSnapshot}) and a
 * {@link JobOffer} (never a {@code Vacancy} JPA entity). Implementations must never expose a Spring
 * AI/OpenAI type through this contract - enforced by {@code CvTailoringAiBoundaryArchitectureTest}.
 *
 * <p>Deliberately placed in its own {@code candidatecontext.cv.tailoring.ai} sub-package rather than
 * directly in {@code candidatecontext.cv.tailoring}: that package's own boundary test intentionally
 * forbids referencing {@code candidatecontext.cv.model}/{@code integrations.ai} at all, so a port
 * whose signature needs {@link CvSourceSnapshot} cannot live there - this sub-package mirrors {@code
 * candidatecontext.cv.tailoring.validation}'s intentionally more permissive guard instead.
 *
 * <p>Returns the raw, not-yet-source-validated {@link CvTailoringResult} - {@code
 * CvTailoringValidator#validate} against the exact same {@code snapshot} happens afterward, as a
 * separate step the caller performs, never inside an implementation of this method. This method must
 * never call the source-aware validator, never query a repository, and never persist anything.
 *
 * @throws CvTailoringAiException if the AI provider request fails or returns a structurally
 *     malformed response
 */
public interface CvTailoringAiPort {

    CvTailoringResult tailor(JobOffer vacancy, CvSourceSnapshot snapshot);
}
