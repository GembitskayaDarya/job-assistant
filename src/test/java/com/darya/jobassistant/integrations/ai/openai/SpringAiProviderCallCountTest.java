package com.darya.jobassistant.integrations.ai.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.ai.exception.JobAnalysisException;
import com.darya.jobassistant.vacancyextraction.exception.VacancyExtractionException;
import com.darya.jobassistant.vacancyextraction.model.VacancyExtractionRequest;
import java.io.IOException;
import java.time.Duration;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResponseErrorHandler;

/**
 * Sprint 8 Step 11A: proves, against the real, resolved {@code spring-ai-openai}/{@code
 * spring-ai-retry} 1.0.0 classes wired exactly like production (this project's single {@code
 * ChatClient} bean over {@code OpenAiChatModel}, {@code max-attempts=1}), that one adapter
 * invocation ({@link SpringAiJobAnalysisAdapter}/{@link SpringAiVacancyExtractionAdapter}) makes
 * exactly one outbound HTTP request - never a second request for a transient provider error,
 * malformed structured output, or a connection timeout.
 *
 * <p>Every {@link ChatClient} here is built with real production classes
 * ({@code OpenAiApi.builder()}, {@code OpenAiChatModel.builder()}, {@code
 * SpringAiRetryAutoConfiguration}) pointed at a local {@link MockWebServer} instead of {@code
 * https://api.openai.com} - never {@code api.openai.com} itself. No live OpenAI credentials or
 * network access are used or required.
 */
class SpringAiProviderCallCountTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    // --- Job Analysis --------------------------------------------------------------------------

    @Test
    void jobAnalysis_transientProviderError_makesExactlyOneRequest_thenFails() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("{\"error\":\"upstream failure\"}"));
        SpringAiJobAnalysisAdapter adapter = new SpringAiJobAnalysisAdapter(chatClient(maxAttempts(1)));

        assertThatThrownBy(() -> adapter.analyze("system prompt", "user prompt"))
                .isInstanceOf(JobAnalysisException.class);

        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void jobAnalysis_malformedStructuredResponse_makesExactlyOneRequest_noRepairRequest() throws Exception {
        server.enqueue(successResponse("this is not valid JSON at all"));
        SpringAiJobAnalysisAdapter adapter = new SpringAiJobAnalysisAdapter(chatClient(maxAttempts(1)));

        assertThatThrownBy(() -> adapter.analyze("system prompt", "user prompt"))
                .isInstanceOf(JobAnalysisException.class);

        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void jobAnalysis_connectionTimeout_makesExactlyOneAttempt() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        SpringAiJobAnalysisAdapter adapter =
                new SpringAiJobAnalysisAdapter(chatClientWithShortTimeout(maxAttempts(1)));

        assertThatThrownBy(() -> adapter.analyze("system prompt", "user prompt"))
                .isInstanceOf(JobAnalysisException.class);

        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    // --- Vacancy extraction ----------------------------------------------------------------------

    @Test
    void extraction_transientProviderError_makesExactlyOneRequest_thenFails() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("{\"error\":\"upstream failure\"}"));
        SpringAiVacancyExtractionAdapter adapter = new SpringAiVacancyExtractionAdapter(chatClient(maxAttempts(1)));

        assertThatThrownBy(() -> adapter.extract(VacancyExtractionRequest.ofPastedDescription("Backend Engineer role")))
                .isInstanceOf(VacancyExtractionException.class);

        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void extraction_malformedStructuredResponse_makesExactlyOneRequest_noRepairRequest() throws Exception {
        server.enqueue(successResponse("not valid JSON either"));
        SpringAiVacancyExtractionAdapter adapter = new SpringAiVacancyExtractionAdapter(chatClient(maxAttempts(1)));

        assertThatThrownBy(() -> adapter.extract(VacancyExtractionRequest.ofPastedDescription("Backend Engineer role")))
                .isInstanceOf(VacancyExtractionException.class);

        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void extraction_connectionTimeout_makesExactlyOneAttempt() throws Exception {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        SpringAiVacancyExtractionAdapter adapter =
                new SpringAiVacancyExtractionAdapter(chatClientWithShortTimeout(maxAttempts(1)));

        assertThatThrownBy(() -> adapter.extract(VacancyExtractionRequest.ofPastedDescription("Backend Engineer role")))
                .isInstanceOf(VacancyExtractionException.class);

        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    // --- Contrast: proves max-attempts actually governs the count, not a MockWebServer artifact ---

    @Test
    void jobAnalysis_transientProviderError_withMaxAttemptsThree_makesExactlyThreeRequests() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setResponseCode(500));
        SpringAiJobAnalysisAdapter adapter = new SpringAiJobAnalysisAdapter(chatClient(maxAttempts(3)));

        assertThatThrownBy(() -> adapter.analyze("system prompt", "user prompt"))
                .isInstanceOf(JobAnalysisException.class);

        assertThat(server.getRequestCount()).isEqualTo(3);
    }

    // --- Fixtures -------------------------------------------------------------------------------

    private RetryTemplate maxAttempts(int attempts) {
        SpringAiRetryProperties properties = new SpringAiRetryProperties();
        properties.setMaxAttempts(attempts);
        properties.getBackoff().setInitialInterval(java.time.Duration.ofMillis(1));
        return new SpringAiRetryAutoConfiguration().retryTemplate(properties);
    }

    private ChatClient chatClient(RetryTemplate retryTemplate) {
        return ChatClient.builder(chatModel(retryTemplate, RestClient.builder())).build();
    }

    private ChatClient chatClientWithShortTimeout(RetryTemplate retryTemplate) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(500);
        requestFactory.setReadTimeout(500);
        return ChatClient.builder(chatModel(retryTemplate, RestClient.builder().requestFactory(requestFactory))).build();
    }

    private OpenAiChatModel chatModel(RetryTemplate retryTemplate, RestClient.Builder restClientBuilder) {
        SpringAiRetryProperties errorHandlerProperties = new SpringAiRetryProperties();
        ResponseErrorHandler responseErrorHandler = new SpringAiRetryAutoConfiguration().responseErrorHandler(errorHandlerProperties);
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(server.url("/").toString().replaceAll("/$", ""))
                .apiKey("test-key")
                .restClientBuilder(restClientBuilder)
                .responseErrorHandler(responseErrorHandler)
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder().model("gpt-4o-mini").build())
                .retryTemplate(retryTemplate)
                .build();
    }

    private MockResponse successResponse(String assistantContent) {
        String escaped = assistantContent.replace("\"", "\\\"").replace("\n", "\\n");
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "id": "chatcmpl-test",
                          "object": "chat.completion",
                          "created": 1700000000,
                          "model": "gpt-4o-mini",
                          "choices": [
                            {
                              "index": 0,
                              "message": {"role": "assistant", "content": "%s"},
                              "finish_reason": "stop"
                            }
                          ],
                          "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}
                        }
                        """.formatted(escaped));
    }
}
