package com.darya.jobassistant.careerhistory.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfileProvider;
import com.darya.jobassistant.candidates.runtime.CandidateProfileRuntimeProperties;
import com.darya.jobassistant.candidates.runtime.CandidateProfileStartupValidator;
import com.darya.jobassistant.careerhistory.importing.CareerHistoryDiff;
import com.darya.jobassistant.careerhistory.importing.CareerHistoryImportMode;
import com.darya.jobassistant.careerhistory.importing.CareerHistoryImportResult;
import com.darya.jobassistant.careerhistory.importing.CareerHistoryImportSource;
import com.darya.jobassistant.careerhistory.importing.CareerHistoryImportStatus;
import com.darya.jobassistant.careerhistory.importing.CareerHistoryImportUseCase;
import com.darya.jobassistant.careerhistory.importing.source.CareerHistoryImportDocument;
import com.darya.jobassistant.config.StartupOrder;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

/**
 * Sprint 9 Step 7 final correction (item 2): direct proof of the resolved {@code ApplicationRunner}
 * order between {@link CandidateProfileStartupValidator} and {@link CareerHistoryImportRunner} -
 * both real beans, both real {@code @Order}-annotated {@code ApplicationRunner} implementations,
 * sorted by Spring's own {@link AnnotationAwareOrderComparator} inside a real {@code
 * SpringApplication.callRunners()} pass.
 *
 * <p>Deliberately lighter-weight than {@code CareerHistoryImportStartupOrderingTest}: every
 * dependency each runner actually calls ({@link CandidateProfileProvider}, {@link
 * CareerHistoryImportSource}, {@link CareerHistoryImportUseCase}) is a plain mock/lambda here, not
 * the real database-backed implementation - no Testcontainers, no Flyway, no JPA - because this
 * test's only job is proving <em>order</em>, not end-to-end persistence (that remains {@code
 * CareerHistoryImportStartupOrderingTest}'s job). Recording each runner's first collaborator call
 * to a shared list is "test-specific wrappers/spies," not an intrusive production hook: neither
 * {@link CandidateProfileStartupValidator} nor {@link CareerHistoryImportRunner} is modified,
 * wrapped, or proxied - only their real, already-injectable dependencies are swapped for
 * recording test doubles.
 */
class CareerHistoryImportRunnerOrderComparatorTest {

    private static final List<String> invocationOrder = new CopyOnWriteArrayList<>();

    @AfterEach
    void clearRecordedInvocationOrder() {
        invocationOrder.clear();
    }

    /**
     * Option A (invocation recorder) and Option B (order comparator) together, against one real
     * boot: the exact sequence {@code candidate-profile-validation} then {@code
     * career-history-import} is recorded from the real runners' real first collaborator calls, and
     * {@link AnnotationAwareOrderComparator} independently confirms the same relative order from
     * the beans' {@code @Order} values alone.
     */
    @Test
    void candidateProfileValidation_isRecordedAndOrdered_beforeCareerHistoryImport() {
        ConfigurableApplicationContext context = null;
        try {
            context = new SpringApplicationBuilder(TestConfig.class)
                    .web(WebApplicationType.NONE)
                    .run("--career-history.import.mode=DRY_RUN");

            assertThat(invocationOrder).containsExactly("candidate-profile-validation", "career-history-import");

            CandidateProfileStartupValidator validator = context.getBean(CandidateProfileStartupValidator.class);
            CareerHistoryImportRunner runner = context.getBean(CareerHistoryImportRunner.class);
            assertThat(AnnotationAwareOrderComparator.INSTANCE.compare(validator, runner)).isNegative();
            assertThat(StartupOrder.CANDIDATE_PROFILE_VALIDATION).isLessThan(StartupOrder.CAREER_HISTORY_IMPORT);
        } finally {
            if (context != null) {
                context.close();
            }
        }
    }

    @Configuration
    @EnableConfigurationProperties({CandidateProfileRuntimeProperties.class, CareerHistoryImportProperties.class})
    @Import({CandidateProfileStartupValidator.class, CareerHistoryImportRunner.class})
    static class TestConfig {

        @Bean
        CandidateProfileProvider candidateProfileProvider() {
            return () -> {
                invocationOrder.add("candidate-profile-validation");
                return minimalProfile();
            };
        }

        @Bean
        CareerHistoryImportSource careerHistoryImportSource() {
            return () -> {
                invocationOrder.add("career-history-import");
                return new CareerHistoryImportDocument(1, "primary", null, List.of());
            };
        }

        @Bean
        CareerHistoryImportUseCase careerHistoryImportUseCase() {
            CareerHistoryImportUseCase useCase = mock(CareerHistoryImportUseCase.class);
            CareerHistoryImportResult dryRunResult = new CareerHistoryImportResult(CareerHistoryImportMode.DRY_RUN,
                    CareerHistoryImportStatus.WOULD_CREATE, "primary", "fingerprint", null, null, null,
                    new CareerHistoryDiff(false, List.of(), 0));
            when(useCase.dryRun(any())).thenReturn(dryRunResult);
            return useCase;
        }

        private CandidateProfile minimalProfile() {
            CandidatePreferences preferences = new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null);
            return new CandidateProfile("Backend Engineer", "Senior", List.of(), List.of(), 5, preferences);
        }
    }
}
