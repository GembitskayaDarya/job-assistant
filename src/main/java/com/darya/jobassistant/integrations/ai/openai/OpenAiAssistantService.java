package com.darya.jobassistant.integrations.ai.openai;

import com.darya.jobassistant.config.OpenAiProperties;
import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OpenAiAssistantService {

    private final OpenAIClient openAIClient;
    private final OpenAiProperties openAiProperties;

    public String getChatCompletion(String systemPrompt, String userPrompt) {
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(openAiProperties.model())
                .addSystemMessage(systemPrompt)
                .addUserMessage(userPrompt)
                .build();

        ChatCompletion completion = openAIClient.chat().completions().create(params);

        return completion.choices().stream()
                .findFirst()
                .flatMap(choice -> choice.message().content())
                .orElse("");
    }
}
