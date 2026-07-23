package com.darya.jobassistant.ai.port;

import com.darya.jobassistant.integrations.ai.openai.JobAnalysis;

public interface JobAnalysisAiPort {

    JobAnalysis analyze(String systemPrompt, String userPrompt);
}
