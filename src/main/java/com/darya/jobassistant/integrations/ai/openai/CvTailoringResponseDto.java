package com.darya.jobassistant.integrations.ai.openai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * Sprint 11 Final CV Policy: the raw Spring AI structured-output shape for {@link
 * SpringAiCvTailoringAdapter} - the complete AI CV-tailoring contract as of this block. AI answers
 * exactly one question: which factual Technical Skills are relevant to this vacancy, and in what
 * order. Everything this DTO previously also carried (Professional Summary, position/project/bullet/
 * Personal-Project selection and rewrite decisions) is now deterministic, manually-approved baseline
 * content resolved by {@code BaselineCvSelectionResolver} - never an AI decision, so there is no
 * field for any of it here any more.
 *
 * <p>{@link #orderedSkillRefs} never carries a raw UUID - only short prompt-local reference tokens
 * (e.g. {@code SKILL_003}) that {@link CvTailoringReferenceIndex} resolves back to a real candidate
 * skill id, strictly and deterministically, before a {@code CvSkillTailoringResult} is constructed.
 *
 * <p>The {@code @JsonPropertyDescription} below is passed through by Spring AI's structured-output
 * JSON schema generation to the model as part of the response schema itself, not just prose in the
 * system prompt - a second, provider-schema-level reinforcement of the same reference-token rule, on
 * top of (not instead of) the prompt text.
 */
record CvTailoringResponseDto(
        @JsonPropertyDescription("Only SKILL_* reference tokens listed under CANDIDATE SKILLS, most relevant first. "
                + "Never invented, never a technology or any other kind of reference token.")
        List<String> orderedSkillRefs
) {
}
