package com.darya.jobassistant.personalprojects.aggregate;

import java.util.UUID;

/**
 * Sprint 11 Step 5: one factual highlight bullet within a {@link PersonalProject} (V29's {@code
 * personal_project_highlight}) - fixed factual source text. A future AI tailoring step may select
 * or order highlights by {@link #id}, but must never invent or rewrite {@link #text}.
 *
 * <p>{@link #id} is snapshot-scoped to its owning {@link PersonalProject}'s own save (see that
 * type's javadoc): stable across unrelated Candidate Profile edits and across edits to sibling
 * Personal Projects, but not guaranteed stable across a re-save of this exact project's own
 * highlight list - the same accepted, Tier-1-consistent trade-off Candidate Profile's
 * skills/languages/preferences already have.
 */
public record PersonalProjectHighlight(
        UUID id,
        String text,
        int displayOrder
) {

    public PersonalProjectHighlight {
        text = PersonalProjectValidation.requireNonBlank(text, "Personal project highlight text must not be blank");
        PersonalProjectValidation.requireNonNegative(displayOrder, "Personal project highlight display order must not be negative");
    }

    /** Convenience constructor for a highlight with no persisted identity yet. */
    public PersonalProjectHighlight(String text, int displayOrder) {
        this(null, text, displayOrder);
    }
}
