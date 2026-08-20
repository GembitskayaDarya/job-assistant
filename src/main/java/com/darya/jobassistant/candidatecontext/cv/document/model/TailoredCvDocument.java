package com.darya.jobassistant.candidatecontext.cv.document.model;

import com.darya.jobassistant.candidates.CandidateEducationFacts;
import com.darya.jobassistant.candidates.CandidateLanguageFacts;
import java.util.List;

/**
 * Sprint 11 Big Block 6: the complete, immutable, presentation-neutral CV content produced by {@code
 * CvAssembler} from a {@code CvSourceSnapshot} plus a source-validated {@code CvTailoringResult} -
 * the end of the pipeline this block implements:
 *
 * <pre>{@code
 * CvSourceSnapshot + CvTailoringResult -> CvTailoringValidator -> CvAssembler -> TailoredCvDocument
 * }</pre>
 *
 * <p>This is <strong>not</strong> a renderer model: it carries no CSS, HTML, PDF coordinates, fonts,
 * colors, or renderer-specific layout flags - only the factual + tailored content any future
 * renderer (professional-v1 HTML/PDF, a later block) would need. Every field is either copied
 * unchanged from factual source data ({@link #header}, {@link #education}, {@link #languages}, every
 * company/position/project identity/date fact nested under {@link #experience}, every Personal
 * Project identity/description/URL/date fact nested under {@link #personalProjects}) or fully
 * resolved by the assembler from a validated tailoring decision ({@link #professionalSummary},
 * {@link #skills}, and every selected/ordered/possibly-rewritten bullet or technology list nested
 * below) - never a raw id, never an unresolved reference.
 *
 * <p>{@link #education}/{@link #languages} reuse {@code CandidateEducationFacts}/{@code
 * CandidateLanguageFacts} directly rather than introducing parallel {@code TailoredCv*} types for
 * them: neither is ever a tailoring/selection surface (see {@code CvTailoringResult}'s "what the AI
 * does NOT control" list), so there is nothing for this document to resolve or reshape - they are
 * carried through byte-for-byte, the same way {@code CvSourceSnapshot} itself does.
 */
public record TailoredCvDocument(
        TailoredCvHeader header,
        String professionalSummary,
        List<String> skills,
        List<TailoredCvCompany> experience,
        List<TailoredCvPersonalProject> personalProjects,
        List<CandidateEducationFacts> education,
        List<CandidateLanguageFacts> languages
) {

    public TailoredCvDocument {
        if (header == null) {
            throw new IllegalArgumentException("Tailored CV document header must not be null");
        }
        skills = skills == null ? List.of() : List.copyOf(skills);
        experience = experience == null ? List.of() : List.copyOf(experience);
        personalProjects = personalProjects == null ? List.of() : List.copyOf(personalProjects);
        education = education == null ? List.of() : List.copyOf(education);
        languages = languages == null ? List.of() : List.copyOf(languages);
    }
}
