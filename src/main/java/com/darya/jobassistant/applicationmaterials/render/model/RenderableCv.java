package com.darya.jobassistant.applicationmaterials.render.model;

import com.darya.jobassistant.applicationmaterials.generation.model.GeneratedCvSkill;
import java.util.List;

/**
 * Sprint 10 Step 4: the complete canonical CV content ready to render - the frozen result of
 * {@code RenderModelAssembler} resolving a generation's {@code GeneratedCv} against the exact
 * {@code CandidateContextForApplicationMaterials} used for that generation.
 *
 * <p>{@link #targetRole}/{@link #seniority}/{@link #totalExperienceYears} are the only "header"
 * facts included - taken directly from {@code CandidateProfile}, which has no name, address,
 * phone, email, LinkedIn, education, or certification fields to draw from. None of those are
 * invented here; a future step that adds such fields to Candidate Profile can extend this record
 * then, not before. {@link #skills} reuses {@link GeneratedCvSkill} directly - it is already the
 * canonical, validated shape (name plus Java-resolved proficiency), and duplicating it here would
 * only add drift risk for no benefit.
 */
public record RenderableCv(
        String targetRole,
        String seniority,
        int totalExperienceYears,
        String headline,
        String professionalSummary,
        List<GeneratedCvSkill> skills,
        List<RenderableCvExperience> experiences,
        List<String> languages
) {

    public RenderableCv {
        if (targetRole == null || targetRole.isBlank()) {
            throw new IllegalArgumentException("Renderable CV targetRole must not be blank");
        }
        if (headline == null || headline.isBlank()) {
            throw new IllegalArgumentException("Renderable CV headline must not be blank");
        }
        if (professionalSummary == null || professionalSummary.isBlank()) {
            throw new IllegalArgumentException("Renderable CV professionalSummary must not be blank");
        }
        skills = skills == null ? List.of() : List.copyOf(skills);
        experiences = experiences == null ? List.of() : List.copyOf(experiences);
        languages = languages == null ? List.of() : List.copyOf(languages);
    }
}
