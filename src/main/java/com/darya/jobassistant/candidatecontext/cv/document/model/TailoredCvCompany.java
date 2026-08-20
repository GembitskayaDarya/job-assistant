package com.darya.jobassistant.candidatecontext.cv.document.model;

import java.util.List;

/**
 * Sprint 11 Big Block 6: one company within {@link TailoredCvDocument#experience()} - name and every
 * other company-level fact are copied unchanged from {@code CvSourceCompany}, since company identity
 * and order are never AI-controlled (see {@code CvTailoringResult}'s javadoc). {@link #positions}
 * preserves every position the company factually has, in its existing display order.
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
