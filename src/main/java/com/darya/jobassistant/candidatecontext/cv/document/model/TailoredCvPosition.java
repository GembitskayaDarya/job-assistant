package com.darya.jobassistant.candidatecontext.cv.document.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Sprint 11 Big Block 6: one position within a {@link TailoredCvCompany}, fully resolved to final
 * display text by {@code CvAssembler}. Title/dates/employment facts remain the exact factual {@code
 * CvSourcePosition} values - never selected, reordered, or rewritten. {@link #responsibilities}/
 * {@link #achievements} are this position's own (non-project) bullets, exactly as a validated {@code
 * CvTailoringResult} selected/ordered/rewrote them (or, absent a tailoring decision for this
 * position, every one of its own factual bullets unchanged). {@link #projects} preserves every
 * project the position factually has, in its existing order - {@code CvAssembler} never drops a
 * project, only tailors what appears inside one.
 */
public record TailoredCvPosition(
        String title,
        String employmentType,
        String location,
        String workArrangement,
        LocalDate startDate,
        LocalDate endDate,
        boolean currentRole,
        String description,
        List<String> responsibilities,
        List<String> achievements,
        List<TailoredCvProject> projects
) {

    public TailoredCvPosition {
        responsibilities = responsibilities == null ? List.of() : List.copyOf(responsibilities);
        achievements = achievements == null ? List.of() : List.copyOf(achievements);
        projects = projects == null ? List.of() : List.copyOf(projects);
    }
}
