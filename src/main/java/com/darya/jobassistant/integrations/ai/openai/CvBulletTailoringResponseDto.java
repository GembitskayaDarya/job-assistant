package com.darya.jobassistant.integrations.ai.openai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Sprint 11 Big Block 6 (typed-reference correction): one responsibility/achievement selection
 * within {@link CvPositionTailoringResponseDto}/{@link CvProjectTailoringResponseDto} - {@link #ref}
 * is the prompt-local reference token of the source responsibility/achievement being selected
 * (never a raw UUID - see {@link CvTailoringReferenceIndex}), {@link #rewrittenText} is {@code null}
 * when the AI chose not to rewrite it (show the original source text unchanged).
 */
record CvBulletTailoringResponseDto(
        @JsonPropertyDescription(
                "The exact reference token of the source responsibility/achievement being selected - "
                        + "must be one of the reference tokens listed under this entry's own owner "
                        + "(the exact position/project this bullet belongs to), never from a different owner.")
        String ref,
        @JsonPropertyDescription(
                "Optional lightly reworded text for this bullet. Set to null to keep the original source "
                        + "text unchanged - never invent a fact the original text did not contain.")
        String rewrittenText
) {
}
