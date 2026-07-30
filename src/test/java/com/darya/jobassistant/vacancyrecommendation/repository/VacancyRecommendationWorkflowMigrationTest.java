package com.darya.jobassistant.vacancyrecommendation.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Exercises V15 (Sprint 8 Step 10) in isolation from the rest of the Spring context - plain JDBC
 * and Flyway's Java API only, matching {@code VacancyCanonicalUrlMigrationTest}'s convention.
 *
 * <p>V15 has two independent parts: widening {@code chk_job_analysis_origin} to also allow
 * {@code AUTOMATIC_DISCOVERY} and adding the nullable {@code job_analysis.manually_reviewed_at}
 * column, plus creating the new {@code vacancy_recommendation_task} table with its unique
 * constraint, FK, CHECK constraints, and partial index.
 *
 * <p>The container is deliberately a non-static {@code @Container} field so each test method gets
 * a fresh database and therefore a fresh Flyway schema history.
 */
@Testcontainers
class VacancyRecommendationWorkflowMigrationTest {

    @Container
    private final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    // ---- job_analysis provenance widening ----

    @Test
    void v15_widensOriginCheckConstraint_toAlsoAllowAutomaticDiscovery() throws Exception {
        migrateTo("14");
        UUID vacancyId = insertVacancy();

        migrateTo("15");

        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO job_analysis (id, vacancy_id, status, analysis_version, analysis_origin, score)
                    VALUES ('%s', '%s', 'COMPLETED', 1, 'AUTOMATIC_DISCOVERY', 80)
                    """.formatted(UUID.randomUUID(), vacancyId));
        }
    }

    @Test
    void v15_originCheckConstraint_stillRejectsUnknownValues() throws Exception {
        migrateTo("15");
        UUID vacancyId = insertVacancy();

        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            SQLException failure = assertThrows(SQLException.class, () -> statement.execute("""
                    INSERT INTO job_analysis (id, vacancy_id, status, analysis_version, analysis_origin, score)
                    VALUES ('%s', '%s', 'COMPLETED', 1, 'BOGUS_ORIGIN', 80)
                    """.formatted(UUID.randomUUID(), vacancyId)));
            assertThat(failure.getMessage()).contains("chk_job_analysis_origin");
        }
    }

    @Test
    void v15_existingJobAnalysisRows_getNullManuallyReviewedAt_noBackfill() throws Exception {
        migrateTo("14");
        UUID vacancyId = insertVacancy();
        UUID analysisId = UUID.randomUUID();
        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO job_analysis (id, vacancy_id, status, analysis_version, analysis_origin, score)
                    VALUES ('%s', '%s', 'COMPLETED', 1, 'MANUAL', 80)
                    """.formatted(analysisId, vacancyId));
        }

        migrateTo("15");

        try (Connection connection = jdbcConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT manually_reviewed_at FROM job_analysis WHERE id = '%s'".formatted(analysisId))) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getTimestamp("manually_reviewed_at")).isNull();
        }
    }

    @Test
    void v15_manuallyReviewedAtColumn_isNullable() throws Exception {
        migrateTo("15");

        try (Connection connection = jdbcConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("""
                        SELECT is_nullable FROM information_schema.columns
                        WHERE table_name = 'job_analysis' AND column_name = 'manually_reviewed_at'
                        """)) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString("is_nullable")).isEqualTo("YES");
        }
    }

    // ---- vacancy_recommendation_task table ----

    @Test
    void v15_createsVacancyRecommendationTaskTable_withExpectedColumns() throws Exception {
        migrateTo("15");

        try (Connection connection = jdbcConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("""
                        SELECT column_name FROM information_schema.columns
                        WHERE table_name = 'vacancy_recommendation_task'
                        """)) {
            java.util.Set<String> columns = new java.util.HashSet<>();
            while (resultSet.next()) {
                columns.add(resultSet.getString("column_name"));
            }
            assertThat(columns).containsExactlyInAnyOrder(
                    "id", "vacancy_id", "status", "outcome", "attempt_count", "next_attempt_at",
                    "lease_until", "lease_owner", "last_error_category", "created_at", "updated_at",
                    "completed_at");
        }
    }

    @Test
    void v15_vacancyRecommendationTask_uniqueConstraintOnVacancyId_rejectsSecondTaskForSameVacancy()
            throws Exception {
        migrateTo("15");
        UUID vacancyId = insertVacancy();
        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO vacancy_recommendation_task (id, vacancy_id, status, attempt_count, next_attempt_at)
                    VALUES ('%s', '%s', 'PENDING', 0, now())
                    """.formatted(UUID.randomUUID(), vacancyId));
        }

        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            SQLException failure = assertThrows(SQLException.class, () -> statement.execute("""
                    INSERT INTO vacancy_recommendation_task (id, vacancy_id, status, attempt_count, next_attempt_at)
                    VALUES ('%s', '%s', 'PENDING', 0, now())
                    """.formatted(UUID.randomUUID(), vacancyId)));
            assertThat(failure.getMessage()).contains("uk_vacancy_recommendation_task_vacancy");
        }
    }

    @Test
    void v15_vacancyRecommendationTask_foreignKeyCascadesOnVacancyDelete() throws Exception {
        migrateTo("15");
        UUID vacancyId = insertVacancy();
        UUID taskId = UUID.randomUUID();
        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO vacancy_recommendation_task (id, vacancy_id, status, attempt_count, next_attempt_at)
                    VALUES ('%s', '%s', 'PENDING', 0, now())
                    """.formatted(taskId, vacancyId));
            statement.execute("DELETE FROM vacancy WHERE id = '%s'".formatted(vacancyId));
        }

        try (Connection connection = jdbcConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT 1 FROM vacancy_recommendation_task WHERE id = '%s'".formatted(taskId))) {
            assertThat(resultSet.next()).isFalse();
        }
    }

    @Test
    void v15_attemptCount_rejectsNegativeValues() throws Exception {
        migrateTo("15");
        UUID vacancyId = insertVacancy();

        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            SQLException failure = assertThrows(SQLException.class, () -> statement.execute("""
                    INSERT INTO vacancy_recommendation_task (id, vacancy_id, status, attempt_count, next_attempt_at)
                    VALUES ('%s', '%s', 'PENDING', -1, now())
                    """.formatted(UUID.randomUUID(), vacancyId)));
            assertThat(failure.getMessage()).contains("chk_vacancy_recommendation_task_attempt_count_non_negative");
        }
    }

    @Test
    void v15_status_rejectsUnknownValues() throws Exception {
        migrateTo("15");
        UUID vacancyId = insertVacancy();

        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            SQLException failure = assertThrows(SQLException.class, () -> statement.execute("""
                    INSERT INTO vacancy_recommendation_task (id, vacancy_id, status, attempt_count, next_attempt_at)
                    VALUES ('%s', '%s', 'BOGUS_STATUS', 0, now())
                    """.formatted(UUID.randomUUID(), vacancyId)));
            assertThat(failure.getMessage()).contains("chk_vacancy_recommendation_task_status");
        }
    }

    @Test
    void v15_outcome_rejectsUnknownValues() throws Exception {
        migrateTo("15");
        UUID vacancyId = insertVacancy();

        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            SQLException failure = assertThrows(SQLException.class, () -> statement.execute("""
                    INSERT INTO vacancy_recommendation_task
                        (id, vacancy_id, status, outcome, attempt_count, next_attempt_at, completed_at)
                    VALUES ('%s', '%s', 'COMPLETED', 'BOGUS_OUTCOME', 0, now(), now())
                    """.formatted(UUID.randomUUID(), vacancyId)));
            assertThat(failure.getMessage()).contains("chk_vacancy_recommendation_task_outcome");
        }
    }

    @Test
    void v15_outcomeMustBeNull_forNonTerminalStatus() throws Exception {
        migrateTo("15");
        UUID vacancyId = insertVacancy();

        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            SQLException failure = assertThrows(SQLException.class, () -> statement.execute("""
                    INSERT INTO vacancy_recommendation_task (id, vacancy_id, status, outcome, attempt_count, next_attempt_at)
                    VALUES ('%s', '%s', 'PENDING', 'NOTIFIED', 0, now())
                    """.formatted(UUID.randomUUID(), vacancyId)));
            assertThat(failure.getMessage()).contains("chk_vacancy_recommendation_task_outcome_matches_status");
        }
    }

    @Test
    void v15_outcomeMustBeSet_forTerminalStatus() throws Exception {
        migrateTo("15");
        UUID vacancyId = insertVacancy();

        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            SQLException failure = assertThrows(SQLException.class, () -> statement.execute("""
                    INSERT INTO vacancy_recommendation_task (id, vacancy_id, status, attempt_count, next_attempt_at, completed_at)
                    VALUES ('%s', '%s', 'COMPLETED', 0, now(), now())
                    """.formatted(UUID.randomUUID(), vacancyId)));
            assertThat(failure.getMessage()).contains("chk_vacancy_recommendation_task_outcome_matches_status");
        }
    }

    @Test
    void v15_completedAtMustBeSet_forTerminalStatus() throws Exception {
        migrateTo("15");
        UUID vacancyId = insertVacancy();

        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            SQLException failure = assertThrows(SQLException.class, () -> statement.execute("""
                    INSERT INTO vacancy_recommendation_task (id, vacancy_id, status, outcome, attempt_count, next_attempt_at)
                    VALUES ('%s', '%s', 'DEAD', 'PERMANENT_FAILURE', 0, now())
                    """.formatted(UUID.randomUUID(), vacancyId)));
            assertThat(failure.getMessage()).contains("chk_vacancy_recommendation_task_completed_at_matches_status");
        }
    }

    @Test
    void v15_leaseColumns_mustBothBeSet_whenStatusIsProcessing() throws Exception {
        migrateTo("15");
        UUID vacancyId = insertVacancy();

        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            SQLException failure = assertThrows(SQLException.class, () -> statement.execute("""
                    INSERT INTO vacancy_recommendation_task (id, vacancy_id, status, attempt_count, next_attempt_at)
                    VALUES ('%s', '%s', 'PROCESSING', 0, now())
                    """.formatted(UUID.randomUUID(), vacancyId)));
            assertThat(failure.getMessage()).contains("chk_vacancy_recommendation_task_lease_matches_status");
        }
    }

    @Test
    void v15_leaseColumns_mustBothBeAbsent_whenStatusIsNotProcessing() throws Exception {
        migrateTo("15");
        UUID vacancyId = insertVacancy();

        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            SQLException failure = assertThrows(SQLException.class, () -> statement.execute("""
                    INSERT INTO vacancy_recommendation_task
                        (id, vacancy_id, status, attempt_count, next_attempt_at, lease_until, lease_owner)
                    VALUES ('%s', '%s', 'PENDING', 0, now(), now(), 'worker-1')
                    """.formatted(UUID.randomUUID(), vacancyId)));
            assertThat(failure.getMessage()).contains("chk_vacancy_recommendation_task_lease_matches_status");
        }
    }

    @Test
    void v15_processingRow_withBothLeaseColumnsSet_isAccepted() throws Exception {
        migrateTo("15");
        UUID vacancyId = insertVacancy();

        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO vacancy_recommendation_task
                        (id, vacancy_id, status, attempt_count, next_attempt_at, lease_until, lease_owner)
                    VALUES ('%s', '%s', 'PROCESSING', 1, now(), now() + interval '20 minutes', 'worker-1')
                    """.formatted(UUID.randomUUID(), vacancyId));
        }
    }

    @Test
    void v15_pendingWorkIndex_exists_coveringTheThreeEligibleStatuses() throws Exception {
        migrateTo("15");

        try (Connection connection = jdbcConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("""
                        SELECT indexdef FROM pg_indexes
                        WHERE tablename = 'vacancy_recommendation_task'
                        AND indexname = 'idx_vacancy_recommendation_task_pending_work'
                        """)) {
            assertThat(resultSet.next()).isTrue();
            String indexDef = resultSet.getString("indexdef");
            assertThat(indexDef).contains("next_attempt_at").contains("created_at").contains("id");
            assertThat(indexDef).contains("PENDING").contains("RETRY_WAIT").contains("PROCESSING");
        }
    }

    @Test
    void freshDatabase_migratesFromV1ThroughV15Successfully() throws Exception {
        migrateTo("latest");

        try (Connection connection = jdbcConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT 1 FROM information_schema.tables WHERE table_name = 'vacancy_recommendation_task'")) {
            assertThat(resultSet.next()).isTrue();
        }
    }

    @Test
    void v14ToV15Upgrade_preservesExistingVacancyAndJobAnalysisData() throws Exception {
        migrateTo("14");
        UUID vacancyId = insertVacancy();
        UUID analysisId = UUID.randomUUID();
        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO job_analysis (id, vacancy_id, status, analysis_version, analysis_origin, score)
                    VALUES ('%s', '%s', 'COMPLETED', 1, 'MONITORING', 42)
                    """.formatted(analysisId, vacancyId));
        }

        migrateTo("15");

        try (Connection connection = jdbcConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT score, analysis_origin FROM job_analysis WHERE id = '%s'".formatted(analysisId))) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getInt("score")).isEqualTo(42);
            assertThat(resultSet.getString("analysis_origin")).isEqualTo("MONITORING");
        }
    }

    private UUID insertVacancy() throws Exception {
        UUID vacancyId = UUID.randomUUID();
        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            UUID companyId = UUID.randomUUID();
            statement.execute("INSERT INTO company (id, name) VALUES ('%s', 'Acme')".formatted(companyId));
            statement.execute("""
                    INSERT INTO vacancy (id, company_id, title, url, canonical_url, source)
                    VALUES ('%s', '%s', 'Backend Engineer', 'https://example.com/job-%s',
                        'https://example.com/job-%s', 'remoteok')
                    """.formatted(vacancyId, companyId, vacancyId, vacancyId));
        }
        return vacancyId;
    }

    private void migrateTo(String target) {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }

    private Connection jdbcConnection() throws Exception {
        return java.sql.DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
