package com.darya.jobassistant.candidatecontext.cv.model;

import java.util.List;
import java.util.UUID;

/**
 * Sprint 11 Step 1: one company within a {@link CvSourceSnapshot} - the complete-graph, no-AI
 * counterpart of {@code com.darya.jobassistant.careerhistory.aggregate.CareerCompany}. {@link
 * #careerCompanyId} is preserved so a future tailoring step can reference this exact source company
 * rather than asking the AI to recreate it by text.
 *
 * <p>Unlike {@code candidatecontext.applicationmaterials.model.SelectedCareerCompany}, {@link
 * #positions} is never filtered or ranked against a vacancy - it holds every position the company
 * actually has (which may legitimately be empty, mirroring {@code CareerCompany} itself), in its
 * existing display order, because CV generation needs the complete factual universe to tailor from.
 */
public record CvSourceCompany(
        UUID careerCompanyId,
        String name,
        String website,
        String industry,
        String location,
        String description,
        List<CvSourcePosition> positions
) {

    public CvSourceCompany {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("CV source company name must not be blank");
        }
        positions = positions == null ? List.of() : List.copyOf(positions);
    }
}
