package com.darya.jobassistant.openai;

import com.darya.jobassistant.config.OpenAiProperties;
import com.openai.client.OpenAIClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OpenAiAssistantService {

    private static final String SYSTEM_PROMPT =
            "You are a helpful assistant that gives concise, practical advice on job applications, "
                    + "cover letters, and interview preparation.";

    private final OpenAIClient openAIClient;
    private final OpenAiProperties openAiProperties;

    public String generateCoverLetterSuggestion(String jobDescription) {
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(openAiProperties.model())
                .addSystemMessage(SYSTEM_PROMPT)
                .addUserMessage("Draft a short cover letter opening paragraph for this job description:\n" + jobDescription)
                .build();

        ChatCompletion completion = openAIClient.chat().completions().create(params);

        return completion.choices().stream()
                .findFirst()
                .flatMap(choice -> choice.message().content())
                .orElse("");
    }
}
