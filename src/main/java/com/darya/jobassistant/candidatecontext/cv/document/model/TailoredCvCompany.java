package com.darya.jobassistant.candidatecontext.cv.document.model;

import java.util.List;

/**
 * Sprint 11 Big Block 6 (final acceptance correction): one company within {@link
 * TailoredCvDocument#experience()} - name and every other company-level fact are copied unchanged
 * from {@code CvSourceCompany}, since company identity is never AI-controlled (see {@code
 * CvTailoringResult}'s javadoc). {@link #positions} preserves every position the company factually
 * has - {@code CvAssembler} never drops one - but in its final, most-recent/current-first display
 * order, not the source's persisted display order; both this list's own order and {@link
 * TailoredCvDocument#experience()}'s company order are decided exactly once, by {@code CvAssembler}
 * (see its "Canonical company/position display order" javadoc section) - a renderer/verifier must
 * never re-sort either.
 */
public record TailoredCvCompany(
        String name,
        String website,
        String industry,
        String location,
        String description,
        List<TailoredCvPosition> positions
) {

    public TailoredCvCompany {
        positions = positions == null ? List.of() : List.copyOf(positions);
    }
}
