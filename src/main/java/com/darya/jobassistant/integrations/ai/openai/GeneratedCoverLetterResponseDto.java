package com.darya.jobassistant.integrations.ai.openai;

import java.util.List;

/** Narrowly-scoped AI-facing shape for the cover letter half of the response - see {@link SpringAiApplicationMaterialsAdapter}. */
record GeneratedCoverLetterResponseDto(String greeting, List<GeneratedCoverLetterParagraphResponseDto> paragraphs, String closing) {
}
