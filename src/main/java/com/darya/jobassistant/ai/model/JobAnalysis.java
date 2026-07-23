package com.darya.jobassistant.ai.model;

import java.util.List;

public record JobAnalysis(
        int score,
        List<String> pros,
        List<String> cons,
        List<String> missingSkills,
        String summary
) {
    public JobAnalysis {
        pros = pros == null ? List.of() : List.copyOf(pros);
        cons = cons == null ? List.of() : List.copyOf(cons);
        missingSkills = missingSkills == null ? List.of() : List.copyOf(missingSkills);
    }
}
