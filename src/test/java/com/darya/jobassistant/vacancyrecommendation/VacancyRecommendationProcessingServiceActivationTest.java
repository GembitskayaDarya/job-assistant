package com.darya.jobassistant.vacancyrecommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.darya.jobassistant.ai.repository.JobAnalysisRepository;
import com.darya.jobassistant.candidates.CandidateProfileProvider;
import com.darya.jobassistant.integrations.ai.openai.JobAnalysisService;
import com.darya.jobassistant.integrations.notifier.JobNotificationFactory;
import com.darya.jobassistant.integrations.notifier.JobNotificationPort;
import com.darya.jobassistant.notifications.repository.NotificationDeliveryRepository;
import com.darya.jobassistant.vacancies.mapper.VacancyJobOfferMapper;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import com.darya.jobassistant.vacancyrecommendation.config.RecommendationPolicyProperties;
import com.darya.jobassistant.vacancyrecommendation.config.VacancyRecommendationProperties;
import com.darya.jobassistant.vacancyrecommendation.repository.VacancyRecommendationTaskRepository;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Verifies {@link VacancyRecommendationProcessingService}'s conditional activation using {@link
 * ApplicationContextRunner} - the same Testcontainers-free strategy already used by {@code
 * JobDiscoveryActivationTest} - so this never needs a database, OpenAI, or Telegram credentials.
 *
 * <p>{@link JobNotificationPort} is the one collaborator that is itself conditionally present in
 * the real application ({@code TelegramJobNotificationAdapter} only exists when {@code
 * telegram.enabled=true}) - every other collaborator here (repositories, mapper, AI service,
 * notification factory) is an always-registered concrete bean, so this is the activation scenario
 * that actually matters: enabling this feature without Telegram configured must fail startup
 * clearly, never silently omit the processing service.
 */
class VacancyRecommendationProcessingServiceActivationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(CommonCollaboratorsConfig.class, VacancyRecommendationProcessingService.class);

    @Test
    void service_absentByDefault() {
        contextRunner.withUserConfiguration(JobNotificationPortConfig.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(VacancyRecommendationProcessingService.class);
        });
    }

    @Test
    void service_absentWhenExplicitlyDisabled() {
        contextRunner.withUserConfiguration(JobNotificationPortConfig.class)
                .withPropertyValues(disabledProperties())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(VacancyRecommendationProcessingService.class);
                });
    }

    @Test
    void service_presentWhenEnabledWithJobNotificationPort() {
        contextRunner.withUserConfiguration(JobNotificationPortConfig.class)
                .withPropertyValues(enabledProperties())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(VacancyRecommendationProcessingService.class);
                });
    }

    @Test
    void service_missingJobNotificationPort_failsStartupClearly() {
        contextRunner.withPropertyValues(enabledProperties())
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("JobNotificationPort");
                });
    }

    @Test
    void telegramRemainsOptional_whenFeatureDisabled() {
        // No JobNotificationPort bean at all (mirrors telegram.enabled=false) and
        // vacancy-recommendation left at its default (disabled) - ordinary startup unaffected.
        contextRunner.run(context -> assertThat(context).hasNotFailed());
    }

    private String[] enabledProperties() {
        return new String[] {
                "vacancy-recommendation.enabled=true",
                "vacancy-recommendation.recipient-chat-id=555",
                "vacancy-recommendation.processing.batch-size=5",
                "vacancy-recommendation.processing.max-attempts=3",
                "vacancy-recommendation.processing.lease-duration=20m",
                "vacancy-recommendation.processing.retry-initial-delay=10m",
                "vacancy-recommendation.processing.retry-max-delay=2h",
                "recommendation-policy.minimum-score=70"
        };
    }

    private String[] disabledProperties() {
        return new String[] {"vacancy-recommendation.enabled=false"};
    }

    @Configuration
    @EnableConfigurationProperties({VacancyRecommendationProperties.class, RecommendationPolicyProperties.class})
    static class CommonCollaboratorsConfig {

        @Bean
        VacancyRecommendationTaskRepository vacancyRecommendationTaskRepository() {
            return mock(VacancyRecommendationTaskRepository.class);
        }

        @Bean
        VacancyRepository vacancyRepository() {
            return mock(VacancyRepository.class);
        }

        @Bean
        JobAnalysisRepository jobAnalysisRepository() {
            return mock(JobAnalysisRepository.class);
        }

        @Bean
        VacancyJobOfferMapper vacancyJobOfferMapper() {
            return mock(VacancyJobOfferMapper.class);
        }

        @Bean
        CandidateProfileProvider candidateProfileProvider() {
            return mock(CandidateProfileProvider.class);
        }

        @Bean
        JobAnalysisService jobAnalysisService() {
            return mock(JobAnalysisService.class);
        }

        @Bean
        JobNotificationFactory jobNotificationFactory() {
            return mock(JobNotificationFactory.class);
        }

        @Bean
        NotificationDeliveryRepository notificationDeliveryRepository() {
            return mock(NotificationDeliveryRepository.class);
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return mock(PlatformTransactionManager.class);
        }
    }

    @Configuration
    static class JobNotificationPortConfig {

        @Bean
        JobNotificationPort jobNotificationPort() {
            return mock(JobNotificationPort.class);
        }
    }
}
