package com.darya.jobassistant.candidatecontext.cv.model;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Sprint 11 Step 5: one Personal Project within a {@link CvSourceSnapshot} - the complete-graph,
 * no-AI counterpart of {@code personalprojects.aggregate.PersonalProject}. {@link
 * #personalProjectId} is preserved so a future tailoring step can reference this exact source
 * project rather than asking the AI to recreate it by text.
 *
 * <p>{@link #highlights}/{@link #technologies} are never capped, truncated, or re-ordered here -
 * this type carries every fact the source project has, in its existing display order, matching
 * {@link CvSourceProject}'s own "complete factual universe, not a vacancy-bounded excerpt"
 * contract exactly.
 */
public record CvSourcePersonalProject(
        UUID personalProjectId,
        String name,
        String description,
        String url,
        LocalDate startDate,
        LocalDate endDate,
        List<CvSourcePersonalProjectHighlight> highlights,
        List<CvSourcePersonalProjectTechnology> technologies
) {

    public CvSourcePersonalProject {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("CV source personal project name must not be blank");
        }
        highlights = highlights == null ? List.of() : List.copyOf(highlights);
        technologies = technologies == null ? List.of() : List.copyOf(technologies);
    }
}
