package com.darya.jobassistant.applicationmaterials.generation.model;

import java.util.List;
import java.util.UUID;

/**
 * Sprint 10 Step 3: one paragraph of a {@link GeneratedCoverLetter}. Unlike {@link
 * GeneratedExperienceBullet}, {@link #sourceIds} may legitimately be empty - purely connective or
 * closing wording ("I would welcome the opportunity to discuss...") carries no factual claim to
 * trace, and forcing a reference onto it would only produce a meaningless one. Any id that IS
 * present, however, is validated exactly like a CV bullet's: it must exist in the bounded context
 * used for this generation - see {@code GeneratedApplicationMaterialsValidator}.
 */
public record GeneratedCoverLetterParagraph(String text, List<UUID> sourceIds) {

    public GeneratedCoverLetterParagraph {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Generated cover letter paragraph text must not be blank");
        }
        sourceIds = sourceIds == null ? List.of() : List.copyOf(sourceIds);
    }
}
