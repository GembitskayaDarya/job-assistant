package com.darya.jobassistant.jobdiscovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.darya.jobassistant.candidates.CandidateProfileProvider;
import com.darya.jobassistant.integrations.jobsearch.JobPageFetchPort;
import com.darya.jobassistant.integrations.jobsearch.JobSearchPort;
import com.darya.jobassistant.jobdiscovery.budget.JobDiscoveryBudgetPort;
import com.darya.jobassistant.jobdiscovery.config.JobDiscoveryProperties;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import com.darya.jobassistant.vacancies.service.VacancyIngestionService;
import com.darya.jobassistant.vacancyextraction.VacancyExtractionService;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Verifies {@link JobDiscoveryService}'s conditional activation using {@link
 * ApplicationContextRunner} - the same Testcontainers-free strategy already used by {@code
 * FirecrawlActivationTest} - so this never needs a database or a live provider account.
 */
class JobDiscoveryActivationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(CommonCollaboratorsConfig.class, JobDiscoveryService.class);

    @Test
    void service_absentByDefault() {
        contextRunner.withUserConfiguration(AllRequiredPortsConfig.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(JobDiscoveryService.class);
        });
    }

    @Test
    void service_absentWhenExplicitlyDisabled() {
        contextRunner.withUserConfiguration(AllRequiredPortsConfig.class)
                .withPropertyValues(disabledProperties())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(JobDiscoveryService.class);
                });
    }

    @Test
    void service_presentWhenEnabledWithBothRequiredPorts() {
        contextRunner.withUserConfiguration(AllRequiredPortsConfig.class)
                .withPropertyValues(enabledProperties())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JobDiscoveryService.class);
                });
    }

    @Test
    void service_missingJobSearchPort_failsStartupClearly() {
        contextRunner.withUserConfiguration(JobPageFetchAndBudgetPortsConfig.class)
                .withPropertyValues(enabledProperties())
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("JobSearchPort");
                });
    }

    @Test
    void service_missingJobPageFetchPort_failsStartupClearly() {
        contextRunner.withUserConfiguration(JobSearchAndBudgetPortsConfig.class)
                .withPropertyValues(enabledProperties())
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("JobPageFetchPort");
                });
    }

    @Test
    void service_missingJobDiscoveryBudgetPort_failsStartupClearly() {
        contextRunner.withUserConfiguration(SearchAndPageFetchPortsOnlyConfig.class)
                .withPropertyValues(enabledProperties())
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("JobDiscoveryBudgetPort");
                });
    }

    @Test
    void firecrawlRemainsOptional_whenDiscoveryDisabled() {
        // No JobSearchPort/JobPageFetchPort bean at all (mirrors firecrawl.enabled=false) and
        // job-discovery left at its default (disabled) - ordinary startup must stay unaffected.
        contextRunner.run(context -> assertThat(context).hasNotFailed());
    }

    private String[] enabledProperties() {
        return new String[] {
                "job-discovery.enabled=true",
                "job-discovery.execution.max-queries-per-run=3",
                "job-discovery.execution.max-scrapes-per-run=5",
                "job-discovery.execution.max-extractions-per-run=5",
                "job-discovery.execution.max-unique-references-per-run=30",
                "job-discovery.execution.max-reported-issues=50",
                "job-discovery.budget.monthly-credit-limit=800",
                "job-discovery.budget.reserve-credits=200",
                "job-discovery.budget.max-estimated-credits-per-run=15"
        };
    }

    private String[] disabledProperties() {
        return new String[] {"job-discovery.enabled=false"};
    }

    @Configuration
    @EnableConfigurationProperties(JobDiscoveryProperties.class)
    static class CommonCollaboratorsConfig {

        @Bean
        CandidateProfileProvider candidateProfileProvider() {
            return mock(CandidateProfileProvider.class);
        }

        @Bean
        JobSearchQueryPlanner jobSearchQueryPlanner() {
            return mock(JobSearchQueryPlanner.class);
        }

        @Bean
        VacancyExtractionService vacancyExtractionService() {
            return mock(VacancyExtractionService.class);
        }

        @Bean
        VacancyIngestionService vacancyIngestionService() {
            return mock(VacancyIngestionService.class);
        }

        @Bean
        VacancyRepository vacancyRepository() {
            return mock(VacancyRepository.class);
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    @Configuration
    static class AllRequiredPortsConfig {

        @Bean
        JobSearchPort jobSearchPort() {
            return mock(JobSearchPort.class);
        }

        @Bean
        JobPageFetchPort jobPageFetchPort() {
            return mock(JobPageFetchPort.class);
        }

        @Bean
        JobDiscoveryBudgetPort jobDiscoveryBudgetPort() {
            return mock(JobDiscoveryBudgetPort.class);
        }
    }

    /** JobPageFetchPort + JobDiscoveryBudgetPort present, JobSearchPort missing. */
    @Configuration
    static class JobPageFetchAndBudgetPortsConfig {

        @Bean
        JobPageFetchPort jobPageFetchPort() {
            return mock(JobPageFetchPort.class);
        }

        @Bean
        JobDiscoveryBudgetPort jobDiscoveryBudgetPort() {
            return mock(JobDiscoveryBudgetPort.class);
        }
    }

    /** JobSearchPort + JobDiscoveryBudgetPort present, JobPageFetchPort missing. */
    @Configuration
    static class JobSearchAndBudgetPortsConfig {

        @Bean
        JobSearchPort jobSearchPort() {
            return mock(JobSearchPort.class);
        }

        @Bean
        JobDiscoveryBudgetPort jobDiscoveryBudgetPort() {
            return mock(JobDiscoveryBudgetPort.class);
        }
    }

    /** JobSearchPort + JobPageFetchPort present, JobDiscoveryBudgetPort missing. */
    @Configuration
    static class SearchAndPageFetchPortsOnlyConfig {

        @Bean
        JobSearchPort jobSearchPort() {
            return mock(JobSearchPort.class);
        }

        @Bean
        JobPageFetchPort jobPageFetchPort() {
            return mock(JobPageFetchPort.class);
        }
    }
}
