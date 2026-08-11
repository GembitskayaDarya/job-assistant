package com.darya.jobassistant.applicationmaterials.render.model;

import java.util.List;

/**
 * Sprint 10 Step 4: the complete canonical cover letter content ready to render. {@link #greeting}
 * is nullable, mirroring {@code GeneratedCoverLetter#greeting()}. {@link #vacancyTitle}/{@link
 * #vacancyCompany} are trusted {@code Vacancy}/{@code JobOffer} metadata (never AI-supplied),
 * included only for presentation (e.g. a "Re: <title> at <company>" line) - not candidate facts.
 */
public record RenderableCoverLetter(
        String greeting,
        List<String> paragraphs,
        String closing,
        String vacancyTitle,
        String vacancyCompany
) {

    public RenderableCoverLetter {
        paragraphs = paragraphs == null ? List.of() : List.copyOf(paragraphs);
        if (paragraphs.isEmpty()) {
            throw new IllegalArgumentException("Renderable cover letter must contain at least one paragraph");
        }
        if (closing == null || closing.isBlank()) {
            throw new IllegalArgumentException("Renderable cover letter closing must not be blank");
        }
    }
}
