package com.darya.jobassistant.integrations.ai.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.ai.exception.JobAnalysisException;
import com.darya.jobassistant.ai.model.JobAnalysis;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.retry.NonTransientAiException;

@ExtendWith(MockitoExtension.class)
class SpringAiJobAnalysisAdapterTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    private SpringAiJobAnalysisAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SpringAiJobAnalysisAdapter(chatClient);
    }

    @Test
    void analyze_returnsStructuredResponseOnSuccess() {
        JobAnalysis expected = new JobAnalysis(
                80, List.of("Java"), List.of(), List.of(), List.of(),
                "6 years vs. no stated requirement.", "Remote preference matches.", "Good match");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system("system prompt")).thenReturn(requestSpec);
        when(requestSpec.user("user prompt")).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.entity(JobAnalysis.class)).thenReturn(expected);

        JobAnalysis result = adapter.analyze("system prompt", "user prompt");

        assertThat(result).isSameAs(expected);
    }

    @Test
    void analyze_wrapsRuntimeMappingFailureInJobAnalysisException() {
        RuntimeException mappingFailure = new IllegalStateException("malformed model response");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system("system prompt")).thenReturn(requestSpec);
        when(requestSpec.user("user prompt")).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.entity(JobAnalysis.class)).thenThrow(mappingFailure);

        assertThatThrownBy(() -> adapter.analyze("system prompt", "user prompt"))
                .isInstanceOf(JobAnalysisException.class)
                .hasMessage("Failed to obtain job analysis from AI provider")
                .hasCause(mappingFailure);
    }

    @Test
    void analyze_nonTransientAiExceptionFromProvider_isWrappedInJobAnalysisExceptionWithCause() {
        NonTransientAiException providerFailure =
                new NonTransientAiException("HTTP 429 - insufficient_quota");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system("system prompt")).thenReturn(requestSpec);
        when(requestSpec.user("user prompt")).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.entity(JobAnalysis.class)).thenThrow(providerFailure);

        assertThatThrownBy(() -> adapter.analyze("system prompt", "user prompt"))
                .isInstanceOf(JobAnalysisException.class)
                .isNotInstanceOf(NonTransientAiException.class)
                .hasMessage("Failed to obtain job analysis from AI provider")
                .hasCause(providerFailure);
    }

    @Test
    void analyze_jobAnalysisExceptionFromProviderCall_isRethrownUnchangedNotDoubleWrapped() {
        JobAnalysisException original = new JobAnalysisException("AI provider returned a blank summary");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system("system prompt")).thenReturn(requestSpec);
        when(requestSpec.user("user prompt")).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.entity(JobAnalysis.class)).thenThrow(original);

        assertThatThrownBy(() -> adapter.analyze("system prompt", "user prompt"))
                .isSameAs(original);
    }
}
