package com.darya.jobassistant.candidates.runtime;

import com.darya.jobassistant.candidates.CandidateProfileProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Sprint 9 Step 4 correction: fail-fast startup check implemented as an {@link ApplicationRunner}
 * - {@code ApplicationRunner}/{@code CommandLineRunner} beans run as part of {@code
 * SpringApplication.run()} itself, strictly before {@code ApplicationReadyEvent} is published
 * (which marks the application as ready to serve traffic), unlike this class's original {@code
 * ApplicationReadyEvent}-listener implementation - too late for a check whose entire purpose is
 * to prevent the application from ever being considered ready in the first place.
 *
 * <p>{@link #run} calls the real runtime {@link CandidateProfileProvider} bean ({@code
 * PersistentCandidateProfileProvider}) exactly once. A successful call proves, against the real
 * code path {@code JobAnalysisService} will use, that the configured persistent aggregate exists,
 * loads completely, and assembles into a valid analysis {@code CandidateProfile}. A failure -
 * most commonly {@link CandidateProfileNotConfiguredException} - is deliberately left uncaught
 * here (never converted into a logged-and-swallowed warning, and never turned into a readiness
 * health indicator that lets startup succeed anyway): it propagates out of {@code run}, which
 * fails {@code SpringApplication.run()} before {@code ApplicationReadyEvent} is ever published -
 * normal runtime must never become ready against a missing or broken profile. Spring Boot's own
 * default startup-failure reporting logs the propagated exception; this class does not duplicate
 * that by catching and re-logging it, and never logs profile field values either way.
 *
 * <p>Read-only: this class performs no write and never seeds a profile, regardless of outcome.
 *
 * <h2>Ordering</h2>
 *
 * {@code @Order(Ordered.HIGHEST_PRECEDENCE)}: this is the only {@code ApplicationRunner}/{@code
 * CommandLineRunner} bean in the application (see the class-level search performed for this
 * correction), so there is no real ordering conflict to resolve - the explicit order simply
 * documents the intent that Candidate Profile validation must run before any startup runner added
 * later, rather than relying on Spring's unspecified default ordering among same-precedence beans.
 *
 * <h2>Disabled during explicit migration</h2>
 *
 * This bean exists only when {@code candidate-profile.migration.mode} is absent or {@code OFF} -
 * i.e. normal runtime. {@code DRY_RUN}/{@code APPLY} intentionally disable it: migration must be
 * able to run against a database that does not have the profile yet, without normal-runtime
 * validation getting in the way first.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(prefix = "candidate-profile.migration", name = "mode", havingValue = "OFF", matchIfMissing = true)
public class CandidateProfileStartupValidator implements ApplicationRunner {

    private final CandidateProfileProvider candidateProfileProvider;
    private final CandidateProfileRuntimeProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        candidateProfileProvider.getProfile();
        log.info("Persistent Candidate Profile validated: profileKey={}", properties.profileKey());
    }
}
