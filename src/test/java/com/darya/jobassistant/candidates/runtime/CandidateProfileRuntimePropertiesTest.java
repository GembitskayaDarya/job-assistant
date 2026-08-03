package com.darya.jobassistant.candidates.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CandidateProfileRuntimePropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Test
    void profileKey_defaultsToPrimary_whenNotConfigured() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(CandidateProfileRuntimeProperties.class).profileKey()).isEqualTo("primary");
        });
    }

    @Test
    void profileKey_acceptsAnExplicitOverride() {
        contextRunner.withPropertyValues("candidate-profile.runtime.profile-key=secondary")
                .run(context -> assertThat(context.getBean(CandidateProfileRuntimeProperties.class).profileKey())
                        .isEqualTo("secondary"));
    }

    @Test
    void profileKey_blankOverride_failsValidation() {
        contextRunner.withPropertyValues("candidate-profile.runtime.profile-key=   ")
                .run(context -> assertThat(context).hasFailed());
    }

    @EnableConfigurationProperties(CandidateProfileRuntimeProperties.class)
    static class TestConfig {
    }
}
