package com.darya.jobassistant.applicationmaterials.generation.model;

import java.util.List;

/**
 * Sprint 10 Step 3: the tailored cover letter half of a {@link GeneratedApplicationMaterials}
 * result. {@link #greeting} is nullable - "where appropriate" per the generation contract, since a
 * greeting may not always be meaningful (e.g. no named recipient is known).
 */
public record GeneratedCoverLetter(String greeting, List<GeneratedCoverLetterParagraph> paragraphs, String closing) {

    public GeneratedCoverLetter {
        paragraphs = paragraphs == null ? List.of() : List.copyOf(paragraphs);
        if (paragraphs.isEmpty()) {
            throw new IllegalArgumentException("Generated cover letter must contain at least one paragraph");
        }
        if (closing == null || closing.isBlank()) {
            throw new IllegalArgumentException("Generated cover letter closing must not be blank");
        }
    }
}
