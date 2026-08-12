package com.darya.jobassistant.candidatecontext.cv.model;

import com.darya.jobassistant.candidatecontext.CareerHistoryAvailability;
import com.darya.jobassistant.candidates.CandidateProfileFacts;
import java.util.List;

/**
 * Sprint 11 Step 1: the immutable, framework-free, complete factual universe a future CV tailoring
 * step is allowed to draw on - built deterministically by {@code
 * com.darya.jobassistant.candidatecontext.cv.CvSourceSnapshotFactory} from a {@code
 * CandidateContextSnapshot}, with no AI call, no persistence write, and no vacancy-specific
 * filtering.
 *
 * <p>A deliberately separate type from {@code candidatecontext.analysis.CandidateContextForAnalysis}
 * and {@code candidatecontext.applicationmaterials.model.CandidateContextForApplicationMaterials}:
 * both of those are bounded, vacancy-specific projections (selected/ranked/character-truncated to
 * fit an AI prompt), while this snapshot exists precisely so the application - not the AI - owns
 * the complete factual career structure. The AI will later be allowed to return only controlled
 * tailoring decisions (summary text, skill/bullet ordering, limited bullet rewrites) that reference
 * this snapshot's stable ids; it must never be asked to recreate companies, positions, or projects
 * from scratch.
 *
 * <p>{@link #candidateProfile} reuses {@link CandidateProfileFacts} - the same complete, lossless
 * projection {@code CandidateContextSnapshot} itself carries (Sprint 11 Step 1 correction) - rather
 * than the vacancy-analysis-bounded {@code candidates.CandidateProfile}, which drops skill category
 * and language proficiency. It already carries every candidate-profile fact legitimately
 * displayable on a CV (target role, seniority, skills with proficiency and category, languages with
 * proficiency, experience years, preferences) without inventing a second, parallel profile shape.
 *
 * <p>{@link #companies} is always a valid, non-null (possibly empty) list, empty exactly when
 * {@link #careerHistoryAvailability} is not {@link CareerHistoryAvailability#AVAILABLE}. Every
 * company, position, project, and bullet the source Career History has is preserved here, in its
 * existing source-of-truth display order - nothing is filtered, ranked, or dropped for relevance,
 * and nothing is re-sorted alphabetically or by recency.
 */
public record CvSourceSnapshot(
        CandidateProfileFacts candidateProfile,
        CareerHistoryAvailability careerHistoryAvailability,
        List<CvSourceCompany> companies
) {

    public CvSourceSnapshot {
        if (candidateProfile == null) {
            throw new IllegalArgumentException("CV source snapshot candidate profile must not be null");
        }
        if (careerHistoryAvailability == null) {
            throw new IllegalArgumentException("CV source snapshot career history availability must not be null");
        }
        companies = companies == null ? List.of() : List.copyOf(companies);
    }
}
