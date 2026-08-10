package com.darya.jobassistant.integrations.ai.openai;

import java.util.List;

/** Narrowly-scoped AI-facing shape for one cover letter paragraph - see {@link SpringAiApplicationMaterialsAdapter}. */
record GeneratedCoverLetterParagraphResponseDto(String text, List<String> sourceIds) {
}
