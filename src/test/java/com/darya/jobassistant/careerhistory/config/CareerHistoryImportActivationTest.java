package com.darya.jobassistant.careerhistory.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.darya.jobassistant.careerhistory.importing.CareerHistoryImportUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sprint 9 Step 7: proves the exact bean activation matrix for {@link YamlCareerHistoryImportSource}
 * and {@link CareerHistoryImportRunner} without a real YAML file or database - both exist only for
 * {@code career-history.import.mode=DRY_RUN}/{@code =APPLY} (see {@link
 * CareerHistoryImportActiveCondition}), never for an absent property or {@code OFF}, and an
 * unrecognized value fails configuration binding before either condition is ever evaluated.
 */
class CareerHistoryImportActivationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MockDependenciesConfig.class, YamlCareerHistoryImportSource.class, CareerHistoryImportRunner.class);

    @Test
    void modeAbsent_neitherSourceNorRunnerBeanExists() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(YamlCareerHistoryImportSource.class);
            assertThat(context).doesNotHaveBean(CareerHistoryImportRunner.class);
        });
    }

    @Test
    void modeOff_neitherSourceNorRunnerBeanExists() {
        contextRunner.withPropertyValues("career-history.import.mode=OFF").run(context -> {
            assertThat(context).doesNotHaveBean(YamlCareerHistoryImportSource.class);
            assertThat(context).doesNotHaveBean(CareerHistoryImportRunner.class);
        });
    }

    @Test
    void modeDryRun_bothBeansExist() {
        contextRunner.withPropertyValues(
                "career-history.import.mode=DRY_RUN",
                "career-history.import.source=classpath:careerhistory/valid-career-history.yml").run(context -> {
            assertThat(context).hasSingleBean(YamlCareerHistoryImportSource.class);
            assertThat(context).hasSingleBean(CareerHistoryImportRunner.class);
        });
    }

    @Test
    void modeApply_bothBeansExist() {
        contextRunner.withPropertyValues(
                "career-history.import.mode=APPLY",
                "career-history.import.source=classpath:careerhistory/valid-career-history.yml").run(context -> {
            assertThat(context).hasSingleBean(YamlCareerHistoryImportSource.class);
            assertThat(context).hasSingleBean(CareerHistoryImportRunner.class);
        });
    }

    @Test
    void invalidMode_failsConfigurationBindingClearly() {
        contextRunner.withPropertyValues("career-history.import.mode=NOT_A_REAL_MODE")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration
    @AutoConfigurationPackage
    @EnableConfigurationProperties(CareerHistoryImportProperties.class)
    static class MockDependenciesConfig {

        @Bean
        CareerHistoryImportUseCase careerHistoryImportUseCase() {
            return mock(CareerHistoryImportUseCase.class);
        }
    }
}
