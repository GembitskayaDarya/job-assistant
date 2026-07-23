package com.darya.jobassistant.ai.port;

import com.darya.jobassistant.ai.model.JobAnalysis;

public interface JobAnalysisAiPort {

    JobAnalysis analyze(String systemPrompt, String userPrompt);
}
