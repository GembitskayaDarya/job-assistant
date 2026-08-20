package com.darya.jobassistant.candidatecontext.cv.document.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Sprint 11 Big Block 6: one project within a {@link TailoredCvPosition}, fully resolved to final
 * display text by {@code CvAssembler} - name/dates remain the exact factual {@code CvSourceProject}
 * values; {@link #responsibilities}/{@link #achievements} are the exact ordered, possibly-rewritten
 * bullet text a validated {@code CvTailoringResult} selected (or, absent a tailoring decision for
 * this project, every one of the project's own factual bullets unchanged - see {@code CvAssembler}'s
 * javadoc for that baseline-fallback rule); {@link #technologies} are the ordered factual technology
 * names the tailoring result selected (or all of them, same fallback).
 */
public record TailoredCvProject(
        String name,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        List<String> responsibilities,
        List<String> achievements,
        List<String> technologies
) {

    public TailoredCvProject {
        responsibilities = responsibilities == null ? List.of() : List.copyOf(responsibilities);
        achievements = achievements == null ? List.of() : List.copyOf(achievements);
        technologies = technologies == null ? List.of() : List.copyOf(technologies);
    }
}
