package com.darya.jobassistant.integrations.ai.openai;

import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.ai.port.JobAnalysisAiPort;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SpringAiJobAnalysisAdapter implements JobAnalysisAiPort {

    private final ChatClient chatClient;

    @Override
    public JobAnalysis analyze(String systemPrompt, String userPrompt) {
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .entity(JobAnalysis.class);
    }
}
