package com.darya.jobassistant.ai.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Exercises the V10 and V11 migrations' backward-compatible backfills in isolation from the rest
 * of the Spring context - plain JDBC and Flyway's Java API only, no {@code @DataJpaTest}/JPA
 * involved. Migrates only up to V9 (the last pre-Step-6 schema version), inserts a row shaped
 * exactly like a real pre-migration completed analysis, then migrates the rest of the way and
 * asserts what V10 and V11 are responsible for: the legacy {@code missing_skills} values land in
 * {@code missing_required_skills}, the new text assessments get a non-blank legacy fallback, the
 * row becomes {@code analysis_version = 1} / {@code analysis_origin = 'LEGACY'} (never {@code
 * MONITORING}), and the original {@code missing_skills} column is retained rather than dropped.
 *
 * <p>The container is deliberately a non-static {@code @Container} field, so Testcontainers
 * starts a fresh database - and therefore a fresh Flyway schema history - for every {@code @Test}
 * method. Flyway's {@code target()} only ever migrates forward, never rolls back, so a single
 * shared (static) container would let whichever test method happens to run first migrate the
 * database all the way to "latest"; every other test's own {@code migrateTo("9")} would then
 * silently become a no-op against an already-fully-migrated schema instead of the intended
 * pre-V10 snapshot, making the test outcome depend on JUnit's (unspecified) method execution
 * order.
 */
@Testcontainers
class JobAnalysisMigrationTest {

    @Container
    private final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void migration_backfillsLegacyMissingSkillsAndProvidesAssessmentFallbacksForCompletedRows() throws Exception {
        migrateTo("9");

        UUID vacancyId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            UUID companyId = UUID.randomUUID();
            statement.execute("""
                    INSERT INTO company (id, name) VALUES ('%s', 'Legacy Co')
                    """.formatted(companyId));
            statement.execute("""
                    INSERT INTO vacancy (id, company_id, title)
                    VALUES ('%s', '%s', 'Legacy Backend Role')
                    """.formatted(vacancyId, companyId));
            statement.execute("""
                    INSERT INTO job_analysis
                        (id, vacancy_id, status, score, summary, pros, cons, missing_skills)
                    VALUES
                        ('%s', '%s', 'COMPLETED', 80, 'Legacy summary',
                         '{"Strong Java"}', '{"No AWS"}', '{"Kubernetes","AWS"}')
                    """.formatted(analysisId, vacancyId));
        }

        // Only V10/V11 (job_analysis) matter to this test; migrating no further than V11 also
        // avoids V12/V13 (vacancy.canonical_url) entirely - relevant since Sprint 8 Step 4B2D's
        // V13 now refuses to migrate a database with a canonical_url IS NULL row, which this
        // test's raw-JDBC vacancy insert (deliberately not V12/V13-aware) would otherwise be.
        migrateTo("11");

        try (Connection connection = jdbcConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("""
                        SELECT missing_skills, missing_required_skills, missing_preferred_skills,
                               experience_assessment, preferences_assessment,
                               analysis_version, analysis_origin
                        FROM job_analysis WHERE id = '%s'
                        """.formatted(analysisId))) {
            assertThat(resultSet.next()).isTrue();
            java.sql.Array legacyMissingSkills = resultSet.getArray("missing_skills");
            java.sql.Array missingRequiredSkills = resultSet.getArray("missing_required_skills");
            java.sql.Array missingPreferredSkills = resultSet.getArray("missing_preferred_skills");

            // The old column is retained, not dropped or cleared.
            assertThat((Object[]) legacyMissingSkills.getArray()).containsExactlyInAnyOrder("Kubernetes", "AWS");
            // Backfilled: legacy missing_skills values become legacy required-gap data.
            assertThat((Object[]) missingRequiredSkills.getArray()).containsExactlyInAnyOrder("Kubernetes", "AWS");
            // No reliable way to retroactively split the old flat list, so preferred stays empty.
            assertThat((Object[]) missingPreferredSkills.getArray()).isEmpty();
            assertThat(resultSet.getString("experience_assessment")).isEqualTo("Not assessed in the legacy analysis.");
            assertThat(resultSet.getString("preferences_assessment")).isEqualTo("Not assessed in the legacy analysis.");
            // V11: every pre-existing row becomes version 1, origin LEGACY - never MONITORING,
            // so it can never be inferred as (or become eligible as) a monitoring-produced result.
            assertThat(resultSet.getInt("analysis_version")).isEqualTo(1);
            assertThat(resultSet.getString("analysis_origin")).isEqualTo("LEGACY");
        }
    }

    @Test
    void migration_doesNotDeleteOrTruncateExistingRows() throws Exception {
        migrateTo("9");

        UUID vacancyId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            UUID companyId = UUID.randomUUID();
            statement.execute("INSERT INTO company (id, name) VALUES ('%s', 'Another Co')".formatted(companyId));
            statement.execute("""
                    INSERT INTO vacancy (id, company_id, title) VALUES ('%s', '%s', 'Role')
                    """.formatted(vacancyId, companyId));
            statement.execute("""
                    INSERT INTO job_analysis (id, vacancy_id, status, score, summary, pros, cons, missing_skills)
                    VALUES ('%s', '%s', 'COMPLETED', 55, 'Kept row', '{}', '{}', '{}')
                    """.formatted(analysisId, vacancyId));
        }

        // Only V10/V11 (job_analysis) matter to this test; migrating no further than V11 also
        // avoids V12/V13 (vacancy.canonical_url) entirely - relevant since Sprint 8 Step 4B2D's
        // V13 now refuses to migrate a database with a canonical_url IS NULL row, which this
        // test's raw-JDBC vacancy insert (deliberately not V12/V13-aware) would otherwise be.
        migrateTo("11");

        try (Connection connection = jdbcConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM job_analysis WHERE id = '" + analysisId + "'")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getLong(1)).isEqualTo(1L);
        }
    }

    @Test
    void v11Constraints_rejectInvalidOriginAndNonPositiveVersions() throws Exception {
        // Only V10/V11 (job_analysis) matter to this test; migrating no further than V11 also
        // avoids V12/V13 (vacancy.canonical_url) entirely - relevant since Sprint 8 Step 4B2D's
        // V13 now refuses to migrate a database with a canonical_url IS NULL row, which this
        // test's raw-JDBC vacancy insert (deliberately not V12/V13-aware) would otherwise be.
        migrateTo("11");

        UUID vacancyId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO company (id, name) VALUES ('%s', 'Constraint Co')".formatted(companyId));
            statement.execute("""
                    INSERT INTO vacancy (id, company_id, title) VALUES ('%s', '%s', 'Role')
                    """.formatted(vacancyId, companyId));
        }

        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            org.junit.jupiter.api.Assertions.assertThrows(java.sql.SQLException.class, () -> statement.execute("""
                    INSERT INTO job_analysis
                        (vacancy_id, status, score, summary, pros, cons, analysis_version, analysis_origin)
                    VALUES ('%s', 'COMPLETED', 50, 's', '{}', '{}', 1, 'BOGUS')
                    """.formatted(vacancyId)));
        }
        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            org.junit.jupiter.api.Assertions.assertThrows(java.sql.SQLException.class, () -> statement.execute("""
                    INSERT INTO job_analysis
                        (vacancy_id, status, score, summary, pros, cons, analysis_version, analysis_origin)
                    VALUES ('%s', 'COMPLETED', 50, 's', '{}', '{}', 0, 'LEGACY')
                    """.formatted(vacancyId)));
        }
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
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
