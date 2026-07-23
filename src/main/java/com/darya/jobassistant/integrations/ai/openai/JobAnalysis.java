package com.darya.jobassistant.integrations.ai.openai;

import java.util.List;

public record JobAnalysis(
        int score,
        List<String> pros,
        List<String> cons,
        List<String> missingSkills,
        String summary
) {
}
