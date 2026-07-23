package com.darya.jobassistant.integrations.ai.openai;

import com.darya.jobassistant.ai.exception.JobAnalysisException;
import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.ai.port.JobAnalysisAiPort;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SpringAiJobAnalysisAdapter implements JobAnalysisAiPort {

    private static final String FAILURE_MESSAGE = "Failed to obtain job analysis from AI provider";

    private final ChatClient chatClient;

    @Override
    public JobAnalysis analyze(String systemPrompt, String userPrompt) {
        try {
            return chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .entity(JobAnalysis.class);
        } catch (JobAnalysisException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new JobAnalysisException(FAILURE_MESSAGE, e);
        }
    }
}
