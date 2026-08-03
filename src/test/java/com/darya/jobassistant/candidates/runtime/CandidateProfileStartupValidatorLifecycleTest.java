package com.darya.jobassistant.candidates.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.JobAssistantApplication;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sprint 9 Step 4 correction: proves {@link CandidateProfileStartupValidator}'s pre-ready
 * placement against a real {@code SpringApplication} boot, not just the validator method called
 * directly (see {@code CandidateProfileStartupValidatorTest} for that unit-level coverage). Each
 * test method gets its own container ({@code @Container} on an instance field, not {@code
 * static}): the two scenarios need mutually exclusive database states (profile present vs. an
 * empty schema), and Flyway migration history must never be shared between them.
 */
@Testcontainers
class CandidateProfileStartupValidatorLifecycleTest {

    @Container
    private final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /**
     * Registered as a plain {@link ApplicationListener} (not a {@code @Component} in the
     * application context) so it observes real event ordering from outside the container,
     * independent of any bean registration/condition this correction changes.
     */
    private final AtomicBoolean applicationReadyPublished = new AtomicBoolean(false);
    private final ApplicationListener<ApplicationReadyEvent> readyListener =
            event -> applicationReadyPublished.set(true);

    @Test
    void existingProfile_startupValidatorRuns_thenApplicationReadyEventIsPublished() throws Exception {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(JobAssistantApplication.class)
                .web(WebApplicationType.NONE)
                .listeners(readyListener);

        ConfigurableApplicationContext context = builder.run(
                "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword(),
                "--spring.ai.openai.api-key=test-key",
                // Same test-only seed location AbstractIntegrationTest uses - never on the
                // production classpath (see CandidateProfileMigrationLocationsIsolationTest).
                "--spring.flyway.locations=classpath:db/migration,classpath:db/testdata");
        try {
            assertThat(applicationReadyPublished).isTrue();
            assertThat(context.getBean(CandidateProfileStartupValidator.class)).isNotNull();

            // Requirement 10: the validator performed no write - version is still exactly what
            // Flyway's seed inserted it as (default 0), never incremented by a read.
            try (Connection connection = context.getBean(javax.sql.DataSource.class).getConnection();
                 Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(
                         "SELECT version FROM candidate_profile WHERE profile_key = 'primary'")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getLong("version")).isZero();
            }
        } finally {
            context.close();
        }
    }

    @Test
    void missingProfile_startupValidatorFails_beforeApplicationReadyEventIsPublished() {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(JobAssistantApplication.class)
                .web(WebApplicationType.NONE)
                .listeners(readyListener);

        assertThatThrownBy(() -> builder.run(
                "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword(),
                "--spring.ai.openai.api-key=test-key",
                "--spring.flyway.locations=classpath:db/migration"))
                .isInstanceOf(CandidateProfileNotConfiguredException.class);

        assertThat(applicationReadyPublished).isFalse();
    }

    /** Trivial sanity check that {@link ApplicationRunner}/{@link ApplicationArguments} are the real Boot contract used. */
    @Test
    void applicationRunnerContract_isTheStandardSpringBootMechanism() {
        assertThat(ApplicationRunner.class.getMethods()).anyMatch(method ->
                method.getName().equals("run") && method.getParameterTypes()[0].equals(ApplicationArguments.class));
    }
}
