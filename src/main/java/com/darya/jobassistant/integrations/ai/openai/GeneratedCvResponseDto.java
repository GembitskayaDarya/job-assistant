package com.darya.jobassistant.integrations.ai.openai;

import java.util.List;

/**
 * Narrowly-scoped AI-facing shape for the CV half of the response - deliberately has no skill
 * proficiency or canonical position/company/date fields; see {@link SpringAiApplicationMaterialsAdapter}
 * and {@code GeneratedApplicationMaterialsValidator} for why those are always Java-resolved, never
 * AI-supplied.
 */
record GeneratedCvResponseDto(
        String headline,
        String professionalSummary,
        List<String> skills,
        List<GeneratedCvExperienceResponseDto> experiences
) {
}
