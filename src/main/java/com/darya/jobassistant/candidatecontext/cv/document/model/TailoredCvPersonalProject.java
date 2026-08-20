package com.darya.jobassistant.candidatecontext.cv.document.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Sprint 11 Big Block 6: one Personal Project within {@link TailoredCvDocument#personalProjects()},
 * fully resolved to final display text by {@code CvAssembler}. Name/description/URL/dates remain the
 * exact factual {@code CvSourcePersonalProject} values - never selected, reordered, or rewritten.
 * {@link #highlights}/{@link #technologies} are the ordered factual text/names a validated {@code
 * CvTailoringResult} selected (or, absent a tailoring decision for this project, every one of its own
 * factual highlights/technologies unchanged).
 */
public record TailoredCvPersonalProject(
        String name,
        String description,
        String url,
        LocalDate startDate,
        LocalDate endDate,
        List<String> highlights,
        List<String> technologies
) {

    public TailoredCvPersonalProject {
        highlights = highlights == null ? List.of() : List.copyOf(highlights);
        technologies = technologies == null ? List.of() : List.copyOf(technologies);
    }
}
