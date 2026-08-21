package com.darya.jobassistant.integrations.ai.openai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * Sprint 11 Big Block 6 (typed-reference correction): the raw Spring AI structured-output shape for
 * {@link SpringAiCvTailoringAdapter}. Every field that used to carry a raw UUID string now carries a
 * short prompt-local reference token instead ({@link #orderedSkillRefs}, and every position/project/
 * bullet/technology/Personal-Project reference nested below) - the model is never shown, and never
 * asked to return, a real domain id. {@link CvTailoringReferenceIndex} is the only place that maps
 * these tokens back to real UUIDs, strictly and deterministically, before a {@code
 * CvTailoringResult} is constructed.
 *
 * <p>Every {@code @JsonPropertyDescription} here (and on the nested DTOs below) is passed through by
 * Spring AI's structured-output JSON schema generation to the model as part of the response schema
 * itself, not just prose in the system prompt - a second, provider-schema-level reinforcement of the
 * same reference-token/empty-list rules, on top of (not instead of) the prompt text.
 */
record CvTailoringResponseDto(
        String professionalSummary,
        @JsonPropertyDescription("Only SKILL_* reference tokens listed under CANDIDATE SKILLS. Never a "
                + "technology reference token (PROJECT_TECH_*/PERSONAL_PROJECT_TECH_*), even if its name matches a skill's name.")
        List<String> orderedSkillRefs,
        List<CvPositionTailoringResponseDto> positionTailoring,
        List<CvProjectTailoringResponseDto> projectTailoring,
        List<CvPersonalProjectTailoringResponseDto> personalProjectTailoring
) {
}
