package com.darya.jobassistant.careerhistory.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.candidates.CandidateProfileProvider;
import com.darya.jobassistant.candidates.runtime.CandidateProfileNotConfiguredException;
import com.darya.jobassistant.candidates.runtime.CandidateProfileRuntimeProperties;
import com.darya.jobassistant.candidates.runtime.CandidateProfileStartupValidator;
import com.darya.jobassistant.careerhistory.importing.CareerHistoryImportSource;
import com.darya.jobassistant.careerhistory.importing.CareerHistoryImportUseCase;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Sprint 9 Step 7 final correction (item 3): direct proof that a missing persistent Candidate
 * Profile short-circuits Career History import - not merely that {@code
 * CareerHistoryImportUseCase} would separately fail with its own "candidate profile missing"
 * exception if it ran (it never gets the chance to). {@link CareerHistoryImportSource} and {@link
 * CareerHistoryImportUseCase} are mocks here specifically so {@code verifyNoInteractions} can
 * prove {@link CareerHistoryImportRunner#run} itself was never invoked at all - the strongest
 * available proof, stronger than inferring non-invocation from an absent side effect.
 *
 * <p>Lightweight - like {@link CareerHistoryImportRunnerOrderComparatorTest} - no Testcontainers,
 * no real database: {@link CandidateProfileStartupValidator}'s only dependency ({@link
 * CandidateProfileProvider}) is itself a mock configured to throw, exactly reproducing "the
 * persistent Candidate Profile is absent" without needing a real empty database.
 */
class CareerHistoryImportMissingProfileShortCircuitTest {

    private static final CandidateProfileProvider candidateProfileProvider = mock(CandidateProfileProvider.class);
    private static final CareerHistoryImportSource careerHistoryImportSource = mock(CareerHistoryImportSource.class);
    private static final CareerHistoryImportUseCase careerHistoryImportUseCase = mock(CareerHistoryImportUseCase.class);

    @BeforeEach
    void resetMocks() {
        reset(candidateProfileProvider, careerHistoryImportSource, careerHistoryImportUseCase);
    }

    @Test
    void missingCandidateProfile_startupFails_careerHistoryImportRunnerNeverInvoked_readyEventNeverPublished() {
        when(candidateProfileProvider.getProfile()).thenThrow(new CandidateProfileNotConfiguredException("primary"));
        AtomicBoolean applicationReadyPublished = new AtomicBoolean(false);
        ApplicationListener<ApplicationReadyEvent> readyListener = event -> applicationReadyPublished.set(true);

        SpringApplicationBuilder builder = new SpringApplicationBuilder(TestConfig.class)
                .web(WebApplicationType.NONE)
                .listeners(readyListener);

        // Matches CandidateProfileStartupValidatorLifecycleTest's established, empirically-verified
        // propagation shape for this exact Spring Boot version: unwrapped, not IllegalStateException.
        assertThatThrownBy(() -> builder.run("--career-history.import.mode=DRY_RUN"))
                .isInstanceOf(CandidateProfileNotConfiguredException.class);

        // 1. CandidateProfileStartupValidator executed - it is the only path that can have thrown this.
        verify(candidateProfileProvider, times(1)).getProfile();
        // 3 & 4. CareerHistoryImportRunner.run() - and therefore CareerHistoryImportUseCase - never invoked.
        verifyNoInteractions(careerHistoryImportSource, careerHistoryImportUseCase);
        // 6. ApplicationReadyEvent never published.
        assertThat(applicationReadyPublished).isFalse();
    }

    @Configuration
    @EnableConfigurationProperties({CandidateProfileRuntimeProperties.class, CareerHistoryImportProperties.class})
    @Import({CandidateProfileStartupValidator.class, CareerHistoryImportRunner.class})
    static class TestConfig {

        @Bean
        CandidateProfileProvider candidateProfileProvider() {
            return candidateProfileProvider;
        }

        @Bean
        CareerHistoryImportSource careerHistoryImportSource() {
            return careerHistoryImportSource;
        }

        @Bean
        CareerHistoryImportUseCase careerHistoryImportUseCase() {
            return careerHistoryImportUseCase;
        }
    }
}
