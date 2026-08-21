package com.darya.jobassistant.integrations.ai.openai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * Sprint 11 Big Block 6 (typed-reference correction): one Personal Project's tailoring decisions -
 * highlight and technology selection/order only, no rewrite. {@link #personalProjectRef} and every
 * entry in {@link #orderedHighlightRefs}/{@link #orderedTechnologyRefs} are prompt-local reference
 * tokens (never raw UUIDs - see {@link CvTailoringReferenceIndex}); a highlight/technology reference
 * is only ever valid under the exact Personal Project it was shown under.
 */
record CvPersonalProjectTailoringResponseDto(
        @JsonPropertyDescription("The exact PERSONAL_PROJECT_* reference token this entry tailors.")
        String personalProjectRef,
        @JsonPropertyDescription(
                "Only reference tokens listed under this exact Personal Project's "
                        + "PERSONAL_PROJECT_HIGHLIGHTS section. If that section says NONE AVAILABLE, this "
                        + "MUST be an empty list [] - never a reference token from a different Personal Project.")
        List<String> orderedHighlightRefs,
        @JsonPropertyDescription(
                "Only PERSONAL_PROJECT_TECH_* reference tokens listed under this exact Personal Project's "
                        + "PERSONAL_PROJECT_TECHNOLOGIES section. If that section says NONE AVAILABLE, this "
                        + "MUST be an empty list [] - never a technology reference token from a different "
                        + "Personal Project.")
        List<String> orderedTechnologyRefs
) {
}
