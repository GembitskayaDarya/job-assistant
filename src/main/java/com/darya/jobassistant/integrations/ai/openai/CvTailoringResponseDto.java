package com.darya.jobassistant.integrations.ai.openai;

import java.util.List;

/**
 * Sprint 11 Big Block 6: the raw Spring AI structured-output shape for {@link
 * SpringAiCvTailoringAdapter} - every id is a {@code String} here (never {@code UUID} directly), the
 * same convention {@code GeneratedApplicationMaterialsResponseDto} already uses: the adapter's own
 * mapping step is the one place responsible for strict id parsing (see {@link
 * SpringAiCvTailoringAdapter#parseUuid}).
 */
record CvTailoringResponseDto(
        String professionalSummary,
        List<String> orderedSkillIds,
        List<CvPositionTailoringResponseDto> positionTailoring,
        List<CvProjectTailoringResponseDto> projectTailoring,
        List<CvPersonalProjectTailoringResponseDto> personalProjectTailoring
) {
}
