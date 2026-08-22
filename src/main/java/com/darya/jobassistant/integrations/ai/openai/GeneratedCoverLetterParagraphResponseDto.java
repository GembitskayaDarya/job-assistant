package com.darya.jobassistant.integrations.ai.openai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * Narrowly-scoped AI-facing shape for one cover letter paragraph - see {@link
 * SpringAiApplicationMaterialsAdapter}. {@link #sourceRefs} are prompt-local reference tokens (never
 * raw UUIDs - see {@link CoverLetterEvidenceReferenceIndex}), resolved to real ids by the adapter
 * before a domain {@code GeneratedCoverLetterParagraph} is constructed.
 */
record GeneratedCoverLetterParagraphResponseDto(
        @JsonPropertyDescription(
                "The paragraph's user-facing prose only - natural English sentences a candidate would "
                        + "send to an employer. Never include a reference token, an id, or words like "
                        + "\"sourceRefs\"/\"sourceIds\"/\"ref\" anywhere inside this text - provenance belongs "
                        + "exclusively in the separate sourceRefs field below.")
        String text,
        @JsonPropertyDescription(
                "Only EVIDENCE_* reference tokens copied exactly from the data shown above, identifying which "
                        + "evidence this paragraph's factual claims are grounded in. Optional - purely connective "
                        + "or closing wording may have an empty list. Never rendered to the reader; never repeat "
                        + "these tokens inside the text field itself.")
        List<String> sourceRefs
) {
}
