package com.darya.jobassistant.careerhistory.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.JobAssistantApplication;
import com.darya.jobassistant.candidates.runtime.CandidateProfileNotConfiguredException;
import com.darya.jobassistant.candidates.runtime.CandidateProfileStartupValidator;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sprint 9 Step 7 final correction: proves {@link CandidateProfileStartupValidator}'s and {@link
 * CareerHistoryImportRunner}'s real, end-to-end startup behavior - real database persistence, real
 * {@link ApplicationReadyEvent} timing - against a real {@code SpringApplication} boot (mirrors
 * {@code CandidateProfileStartupValidatorLifecycleTest}'s exact technique: real {@code
 * SpringApplicationBuilder}, real Testcontainers PostgreSQL, real Flyway-seeded "primary"
 * candidate profile). A plain {@code ApplicationContextRunner} context refresh does not exercise
 * {@code ApplicationRunner}/{@code ApplicationReadyEvent} timing at all, so it cannot prove any of
 * this on its own.
 *
 * <p>The precise <em>invocation order</em> and <em>resolved {@code @Order} comparison</em> between
 * the two runners - both now real {@code ApplicationRunner} implementations sorted by {@link
 * com.darya.jobassistant.config.StartupOrder} - are proven directly, with mocked collaborators, by
 * {@code CareerHistoryImportRunnerOrderComparatorTest}; the precise "never invoked at all" proof
 * for a missing Candidate Profile is proven directly, again with mocked collaborators, by {@code
 * CareerHistoryImportMissingProfileShortCircuitTest}. This class complements both with the
 * heavier, fully real, end-to-end persistence proof neither of those lighter tests attempts.
 */
@Testcontainers
class CareerHistoryImportStartupOrderingTest {

    @Container
    private final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private final AtomicBoolean applicationReadyPublished = new AtomicBoolean(false);
    private final ApplicationListener<ApplicationReadyEvent> readyListener =
            event -> applicationReadyPublished.set(true);

    /**
     * Sprint 9 Step 7 final correction (item 4): with a real, Flyway-seeded "primary" Candidate
     * Profile present, (1) validation succeeds (proven the same way {@code
     * CandidateProfileStartupValidatorLifecycleTest} does: {@link ApplicationReadyEvent} could not
     * otherwise have been published at all - see class javadoc), (2) Career History import then
     * actually runs and persists a complete graph through the real port - only observable at all
     * because validation already completed first - and (3) {@link ApplicationReadyEvent} is
     * observed only after {@code builder.run()} returns, which itself only happens after every
     * {@code ApplicationRunner} (both of these, in {@link
     * com.darya.jobassistant.config.StartupOrder} order) has completed - so the persisted row is
     * always already committed by the time the event fires, never the reverse.
     */
    @Test
    void existingCandidateProfile_startupValidatorSucceeds_thenCareerHistoryImportRuns() throws Exception {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(JobAssistantApplication.class)
                .web(WebApplicationType.NONE)
                .listeners(readyListener);

        ConfigurableApplicationContext context = builder.run(
                "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword(),
                "--spring.ai.openai.api-key=test-key",
                "--spring.flyway.locations=classpath:db/migration,classpath:db/testdata",
                "--career-history.import.mode=APPLY",
                "--career-history.import.source=classpath:careerhistory/valid-career-history.yml");
        try {
            assertThat(applicationReadyPublished).isTrue();
            assertThat(context.getBean(CandidateProfileStartupValidator.class)).isNotNull();

            // Career History import (StartupOrder.CAREER_HISTORY_IMPORT, strictly after
            // StartupOrder.CANDIDATE_PROFILE_VALIDATION) actually ran APPLY end to end through the
            // real port - one career_history row now exists for the seeded "primary" profile.
            assertThat(context.getBean(CareerHistoryImportRunner.class)).isNotNull();
            assertThat(careerHistoryRowCount(context.getBean(DataSource.class))).isEqualTo(1);
        } finally {
            context.close();
        }
    }

    /**
     * Sprint 9 Step 7 final correction (item 3): the real-database complement to {@code
     * CareerHistoryImportMissingProfileShortCircuitTest} - the same short-circuit, proven this
     * time against a real empty schema (no {@code classpath:db/testdata} seed) rather than a mock
     * that throws, confirming no {@code career_history} row exists afterward through the real
     * schema too.
     */
    @Test
    void missingCandidateProfile_startupFails_careerHistoryImportNeverRuns_noRowWritten() throws Exception {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(JobAssistantApplication.class)
                .web(WebApplicationType.NONE)
                .listeners(readyListener);

        assertThatThrownBy(() -> builder.run(
                "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword(),
                "--spring.ai.openai.api-key=test-key",
                // Deliberately omits classpath:db/testdata - no candidate profile exists.
                "--spring.flyway.locations=classpath:db/migration",
                "--career-history.import.mode=APPLY",
                "--career-history.import.source=classpath:careerhistory/valid-career-history.yml"))
                .isInstanceOf(CandidateProfileNotConfiguredException.class);

        assertThat(applicationReadyPublished).isFalse();
        try (Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            assertThat(careerHistoryRowCount(connection)).isZero();
        }
    }

    /**
     * Sprint 9 Step 7 correction (item 3, startup exclusivity): {@link
     * CareerHistoryStartupExclusivityValidator}'s constructor check fails {@link
     * ConfigurableApplicationContext} refresh - itself a step inside {@code
     * SpringApplication.run()} that completes before the {@code ApplicationRunner}-calling phase -
     * so neither {@code CandidateProfileStartupValidator} nor {@link CareerHistoryImportRunner}
     * (nor, transitively, either workflow's use case) ever runs, {@link ApplicationReadyEvent} is
     * never published, and no {@code career_history} row exists afterward. Connects directly to
     * the container (not through the failed context's {@code DataSource}, which was never
     * successfully created) to verify that last point.
     */
    @Test
    void conflictingStartupModes_contextCreationFails_beforeAnyRunnerOrWrite() throws Exception {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(JobAssistantApplication.class)
                .web(WebApplicationType.NONE)
                .listeners(readyListener);

        assertThatThrownBy(() -> builder.run(
                "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword(),
                "--spring.ai.openai.api-key=test-key",
                "--spring.flyway.locations=classpath:db/migration,classpath:db/testdata",
                "--candidate-profile.migration.mode=DRY_RUN",
                "--career-history.import.mode=APPLY",
                "--career-history.import.source=classpath:careerhistory/valid-career-history.yml"))
                .hasRootCauseInstanceOf(CareerHistoryImportStartupConflictException.class);

        assertThat(applicationReadyPublished).isFalse();
        try (Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            assertThat(careerHistoryRowCount(connection)).isZero();
        }
    }

    private long careerHistoryRowCount(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            return careerHistoryRowCount(connection);
        }
    }

    private long careerHistoryRowCount(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT count(*) FROM career_history");
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }
}
