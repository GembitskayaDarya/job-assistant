package com.darya.jobassistant.integrations.ai.openai;

import java.util.List;

/** Narrowly-scoped AI-facing shape for one CV experience entry - see {@link SpringAiApplicationMaterialsAdapter}. */
record GeneratedCvExperienceResponseDto(String careerPositionId, List<GeneratedExperienceBulletResponseDto> bullets) {
}
