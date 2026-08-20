package com.darya.jobassistant.integrations.ai.openai;

import java.util.List;

/** Sprint 11 Big Block 6: one Personal Project's tailoring decisions - highlight and technology selection/order only, no rewrite. */
record CvPersonalProjectTailoringResponseDto(
        String personalProjectId,
        List<String> orderedHighlightIds,
        List<String> orderedTechnologyIds
) {
}
