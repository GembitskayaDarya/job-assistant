package com.darya.jobassistant.candidatecontext.cv.document.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Sprint 11 Golden Master CV Lock: the dedicated "MENTORING EXPERIENCE" section - a distinct
 * top-level block in {@link TailoredCvDocument}, never nested inside {@link
 * TailoredCvDocument#experience()}. The golden-master reference CV shows Mentoring as its own
 * section with its own organization/role/date heading and a single flat bullet list (no
 * "Responsibilities"/"Achievements" sub-headings the way a normal position has) - {@link
 * #bullets} is exactly that combined, already-ordered list.
 *
 * <p>{@link #organization}/{@link #title}/dates remain the exact factual values from the source
 * company/position this resolves to - never selected, reordered, or rewritten, matching every
 * other fixed identity fact in this document. {@code null} (the whole {@link TailoredCvDocument#
 * mentoring()} field, not this record) means "no Mentoring section configured" - {@code
 * CvAssembler} never invents one.
 */
public record TailoredCvMentoring(
        String organization,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        boolean currentRole,
        List<String> bullets
) {

    public TailoredCvMentoring {
        bullets = bullets == null ? List.of() : List.copyOf(bullets);
    }
}
