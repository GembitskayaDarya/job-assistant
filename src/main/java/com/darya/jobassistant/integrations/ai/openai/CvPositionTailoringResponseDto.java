package com.darya.jobassistant.integrations.ai.openai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * Sprint 11 Big Block 6 (typed-reference correction): one position's own (non-project) tailoring
 * decisions. {@link #positionRef} is the prompt-local reference token of the source position (never
 * a raw UUID - see {@link CvTailoringReferenceIndex}).
 */
record CvPositionTailoringResponseDto(
        @JsonPropertyDescription("The exact POSITION_* reference token this entry tailors.")
        String positionRef,
        @JsonPropertyDescription(
                "Only reference tokens listed under this exact position's POSITION_RESPONSIBILITIES "
                        + "section. If that section says NONE AVAILABLE, this MUST be an empty list [] - "
                        + "never a reference token from a different position or from a project.")
        List<CvBulletTailoringResponseDto> responsibilities,
        @JsonPropertyDescription(
                "Only reference tokens listed under this exact position's POSITION_ACHIEVEMENTS section. "
                        + "If that section says NONE AVAILABLE, this MUST be an empty list [] - never a "
                        + "reference token from a different position or from a project.")
        List<CvBulletTailoringResponseDto> achievements
) {
}
