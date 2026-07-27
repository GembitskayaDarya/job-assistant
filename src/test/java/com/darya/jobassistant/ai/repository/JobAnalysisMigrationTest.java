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
 * Exercises the V10 migration's backward-compatible backfill in isolation from the rest of the
 * Spring context - plain JDBC and Flyway's Java API only, no {@code @DataJpaTest}/JPA involved.
 * Migrates only up to V9 (the last pre-Step-6 schema version), inserts a row shaped exactly like
 * a real pre-migration completed analysis, then migrates the rest of the way and asserts what
 * V10 is responsible for: the legacy {@code missing_skills} values land in
 * {@code missing_required_skills}, the new text assessments get a non-blank legacy fallback, and
 * the original {@code missing_skills} column is retained rather than dropped.
 */
@Testcontainers
class JobAnalysisMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

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

        migrateTo("latest");

        try (Connection connection = jdbcConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("""
                        SELECT missing_skills, missing_required_skills, missing_preferred_skills,
                               experience_assessment, preferences_assessment
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

        migrateTo("latest");

        try (Connection connection = jdbcConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM job_analysis WHERE id = '" + analysisId + "'")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getLong(1)).isEqualTo(1L);
        }
    }

    private void migrateTo(String target) {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }

    private Connection jdbcConnection() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
