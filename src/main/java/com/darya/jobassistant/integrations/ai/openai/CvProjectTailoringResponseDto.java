package com.darya.jobassistant.integrations.ai.openai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * Sprint 11 Big Block 6 (typed-reference correction): one career project's tailoring decisions,
 * including its technology selection/order. {@link #projectRef} and every entry in {@link
 * #orderedTechnologyRefs} are prompt-local reference tokens (never raw UUIDs - see {@link
 * CvTailoringReferenceIndex}); a technology reference is only ever valid under the exact project it
 * was shown under.
 */
record CvProjectTailoringResponseDto(
        @JsonPropertyDescription("The exact PROJECT_* reference token this entry tailors.")
        String projectRef,
        @JsonPropertyDescription(
                "Only reference tokens listed under this exact project's PROJECT_RESPONSIBILITIES "
                        + "section. If that section says NONE AVAILABLE, this MUST be an empty list [] - "
                        + "never a reference token from a different project or from a position.")
        List<CvBulletTailoringResponseDto> responsibilities,
        @JsonPropertyDescription(
                "Only reference tokens listed under this exact project's PROJECT_ACHIEVEMENTS section. "
                        + "If that section says NONE AVAILABLE, this MUST be an empty list [] - never a "
                        + "reference token from a different project or from a position.")
        List<CvBulletTailoringResponseDto> achievements,
        @JsonPropertyDescription(
                "Only PROJECT_TECH_* reference tokens listed under this exact project's "
                        + "PROJECT_TECHNOLOGIES section. If that section says NONE AVAILABLE, this MUST be "
                        + "an empty list [] - never a technology reference token from a different project.")
        List<String> orderedTechnologyRefs
) {
}
