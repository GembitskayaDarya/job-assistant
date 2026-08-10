package com.darya.jobassistant.integrations.ai.openai;

import java.util.List;

/** Narrowly-scoped AI-facing shape for one experience bullet - see {@link SpringAiApplicationMaterialsAdapter}. */
record GeneratedExperienceBulletResponseDto(String text, List<String> sourceIds) {
}
