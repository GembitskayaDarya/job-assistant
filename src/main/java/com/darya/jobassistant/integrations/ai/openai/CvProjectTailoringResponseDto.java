package com.darya.jobassistant.integrations.ai.openai;

import java.util.List;

/** Sprint 11 Big Block 6: one career project's tailoring decisions, including its technology selection/order. */
record CvProjectTailoringResponseDto(
        String careerProjectId,
        List<CvBulletTailoringResponseDto> responsibilities,
        List<CvBulletTailoringResponseDto> achievements,
        List<String> orderedTechnologyIds
) {
}
