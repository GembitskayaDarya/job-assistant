package com.darya.jobassistant.integrations.ai.openai;

import java.util.List;

/** Sprint 11 Big Block 6: one position's own (non-project) tailoring decisions. */
record CvPositionTailoringResponseDto(
        String careerPositionId,
        List<CvBulletTailoringResponseDto> responsibilities,
        List<CvBulletTailoringResponseDto> achievements
) {
}
