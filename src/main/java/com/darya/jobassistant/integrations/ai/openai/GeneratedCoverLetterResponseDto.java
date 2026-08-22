package com.darya.jobassistant.integrations.ai.openai;

import java.util.List;

/**
 * Narrowly-scoped AI-facing shape for the cover letter half of the response - see {@link
 * SpringAiApplicationMaterialsAdapter}. Every nested paragraph's evidence reference is a prompt-local
 * token (never a raw UUID - see {@link CoverLetterEvidenceReferenceIndex}).
 */
record GeneratedCoverLetterResponseDto(String greeting, List<GeneratedCoverLetterParagraphResponseDto> paragraphs, String closing) {
}
