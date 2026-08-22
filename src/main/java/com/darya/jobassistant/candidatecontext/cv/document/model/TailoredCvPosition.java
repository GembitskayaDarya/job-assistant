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
 *
 * <p>{@link #technologies} (Sprint 11 Golden Master CV Lock) is the position-level aggregate
 * Technologies line the golden-master reference CV shows for a multi-project position (e.g.
 * Netcracker Technology's "Software Engineer") - the union, in project order, of every one of
 * {@link #projects}' own {@code technologies()}, deduplicated by {@code CvAssembler}. A renderer
 * draws this line once, above {@link #projects}, and must never also draw each project's own
 * technologies separately - that would duplicate the same list. Empty when no project under this
 * position has any technologies selected (the common case, e.g. every Spribe position).
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
        List<TailoredCvProject> projects,
        List<String> technologies
) {

    public TailoredCvPosition {
        responsibilities = responsibilities == null ? List.of() : List.copyOf(responsibilities);
        achievements = achievements == null ? List.of() : List.copyOf(achievements);
        projects = projects == null ? List.of() : List.copyOf(projects);
        technologies = technologies == null ? List.of() : List.copyOf(technologies);
    }

    /**
     * Convenience constructor matching this type's pre-Golden-Master-Lock shape (no {@link
     * #technologies}) - defaults it to empty, so existing call sites (mostly tests) keep compiling
     * unchanged.
     */
    public TailoredCvPosition(
            String title, String employmentType, String location, String workArrangement, LocalDate startDate,
            LocalDate endDate, boolean currentRole, String description, List<String> responsibilities,
            List<String> achievements, List<TailoredCvProject> projects) {
        this(title, employmentType, location, workArrangement, startDate, endDate, currentRole, description,
                responsibilities, achievements, projects, List.of());
    }
}
