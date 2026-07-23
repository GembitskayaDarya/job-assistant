package com.darya.jobassistant.integrations.ai.openai;

import java.util.List;

public record JobAnalysis(
        int matchScore,
        String summary,
        List<String> strengths,
        List<String> concerns,
        MatchRecommendation recommendation
) {
}
