package com.darya.jobassistant.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.support.RetryTemplate;

/**
 * Sprint 8 Step 11A: proves - against the real, resolved {@code spring-ai-retry}/{@code
 * spring-ai-autoconfigure-retry} 1.0.0 classes, not a hand-rolled equivalent - that this project's
 * {@code spring.ai.retry.max-attempts=1} (see {@code application.yml}) means exactly one total
 * HTTP-layer attempt with zero automatic retries.
 *
 * <p>{@link SpringAiRetryProperties}'s own no-arg constructor sets {@code maxAttempts = 10}
 * (confirmed by decompiling the resolved 1.0.0 jar - this matches its documented default, but this
 * class does not take that documentation on faith). {@link
 * SpringAiRetryAutoConfiguration#retryTemplate(SpringAiRetryProperties)} builds a real {@link
 * RetryTemplate} via {@code RetryTemplateBuilder.maxAttempts(int)}, which installs a {@code
 * MaxAttemptsRetryPolicy(maxAttempts)} - the exact policy class whose {@code canRetry} was
 * decompiled during this audit and confirmed to compare {@code retryCount < maxAttempts}, i.e.
 * {@code maxAttempts} counts the total number of attempts including the first, not "retries in
 * addition to the first attempt". {@code retryOn(TransientAiException.class)} also means only that
 * specific exception type is eligible for a second attempt at all; this is exercised directly
 * below via the real bean, not simulated.
 *
 * <p>This is also the exact {@link RetryTemplate} bean {@code OpenAiChatAutoConfiguration
 * .openAiChatModel(...)} takes as a constructor parameter (confirmed by inspecting its bytecode
 * signature during this audit), so this proves the mechanism that actually wraps every OpenAI HTTP
 * call {@code SpringAiVacancyExtractionAdapter}/{@code SpringAiJobAnalysisAdapter} make through the
 * project's single shared {@code ChatClient} bean.
 */
class SpringAiRetryConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Test
    void propertyAbsent_bindsToTheFrameworksOwnDefaultOfTen() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            SpringAiRetryProperties properties = context.getBean(SpringAiRetryProperties.class);
            assertThat(properties.getMaxAttempts()).isEqualTo(10);
        });
    }

    @Test
    void applicationYmlDefault_bindsMaxAttemptsToOne() {
        // Mirrors this project's application.yml: spring.ai.retry.max-attempts: ${SPRING_AI_RETRY_MAX_ATTEMPTS:1}
        contextRunner.withPropertyValues("spring.ai.retry.max-attempts=1").run(context -> {
            assertThat(context).hasNotFailed();
            SpringAiRetryProperties properties = context.getBean(SpringAiRetryProperties.class);
            assertThat(properties.getMaxAttempts()).isEqualTo(1);
        });
    }

    @Test
    void environmentOverride_bindsToTheConfiguredValue() {
        // Simulates SPRING_AI_RETRY_MAX_ATTEMPTS=5 resolving through the ${...} placeholder.
        contextRunner.withPropertyValues("spring.ai.retry.max-attempts=5").run(context -> {
            assertThat(context).hasNotFailed();
            SpringAiRetryProperties properties = context.getBean(SpringAiRetryProperties.class);
            assertThat(properties.getMaxAttempts()).isEqualTo(5);
        });
    }

    @Test
    void retryTemplate_maxAttemptsOne_executesExactlyOnceThenPropagatesTransientFailure() {
        SpringAiRetryProperties properties = new SpringAiRetryProperties();
        properties.setMaxAttempts(1);
        RetryTemplate retryTemplate = new SpringAiRetryAutoConfiguration().retryTemplate(properties);
        AtomicInteger invocations = new AtomicInteger();
        RetryCallback<Object, TransientAiException> alwaysFails = context -> {
            invocations.incrementAndGet();
            throw new TransientAiException("simulated transient provider failure");
        };

        assertThatThrownBy(() -> retryTemplate.execute(alwaysFails)).isInstanceOf(TransientAiException.class);

        assertThat(invocations.get()).isEqualTo(1);
    }

    @Test
    void retryTemplate_maxAttemptsThree_executesExactlyThreeTimesThenPropagates() {
        // Contrast case: proves this is the general retry mechanism (not coincidentally always 1)
        // and that raising max-attempts really does add automatic retries at the HTTP layer.
        SpringAiRetryProperties properties = new SpringAiRetryProperties();
        properties.setMaxAttempts(3);
        RetryTemplate retryTemplate = new SpringAiRetryAutoConfiguration().retryTemplate(properties);
        AtomicInteger invocations = new AtomicInteger();
        RetryCallback<Object, TransientAiException> alwaysFails = context -> {
            invocations.incrementAndGet();
            throw new TransientAiException("simulated transient provider failure");
        };

        assertThatThrownBy(() -> retryTemplate.execute(alwaysFails)).isInstanceOf(TransientAiException.class);

        assertThat(invocations.get()).isEqualTo(3);
    }

    @Test
    void retryTemplate_nonTransientException_isNeverRetriedRegardlessOfMaxAttempts() {
        // retryOn(TransientAiException.class) scopes retryability - an ordinary RuntimeException
        // (e.g. what a malformed structured-output parse failure throws) always executes exactly
        // once, even with a generously high max-attempts.
        SpringAiRetryProperties properties = new SpringAiRetryProperties();
        properties.setMaxAttempts(5);
        RetryTemplate retryTemplate = new SpringAiRetryAutoConfiguration().retryTemplate(properties);
        AtomicInteger invocations = new AtomicInteger();
        RetryCallback<Object, RuntimeException> alwaysThrowsUnrelatedException = context -> {
            invocations.incrementAndGet();
            throw new IllegalStateException("simulated non-transient failure (e.g. JSON parse error)");
        };

        assertThatThrownBy(() -> retryTemplate.execute(alwaysThrowsUnrelatedException))
                .isInstanceOf(IllegalStateException.class);

        assertThat(invocations.get()).isEqualTo(1);
    }

    @Test
    void retryTemplate_maxAttemptsOne_successfulFirstAttempt_neverInvokedTwice() {
        SpringAiRetryProperties properties = new SpringAiRetryProperties();
        properties.setMaxAttempts(1);
        RetryTemplate retryTemplate = new SpringAiRetryAutoConfiguration().retryTemplate(properties);
        AtomicInteger invocations = new AtomicInteger();
        RetryCallback<String, RuntimeException> succeedsImmediately = context -> {
            invocations.incrementAndGet();
            return "ok";
        };

        String result = retryTemplate.execute(succeedsImmediately);

        assertThat(result).isEqualTo("ok");
        assertThat(invocations.get()).isEqualTo(1);
    }

    @EnableConfigurationProperties(SpringAiRetryProperties.class)
    static class TestConfig {
    }
}
