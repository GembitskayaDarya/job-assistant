package com.darya.jobassistant.vacancies.repository;

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
 * Exercises the V12 migration (Sprint 8 Step 4B1) in isolation from the rest of the Spring context
 * - plain JDBC and Flyway's Java API only, no {@code @DataJpaTest}/JPA involved. Migrates only up
 * to V11 (the last pre-Step-4B1 schema version), inserts a row shaped exactly like a real
 * pre-migration vacancy, then migrates the rest of the way and asserts what V12 is responsible
 * for: a nullable {@code canonical_url} column that existing rows pick up as {@code null}, and a
 * partial unique index that protects only non-null values.
 *
 * <p>The container is deliberately a non-static {@code @Container} field, matching {@code
 * JobAnalysisMigrationTest}'s convention, so each test method gets a fresh database and therefore
 * a fresh Flyway schema history - Flyway's {@code target()} only ever migrates forward.
 */
@Testcontainers
class VacancyCanonicalUrlMigrationTest {

    @Container
    private final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void migration_addsNullableCanonicalUrlColumn_andExistingRowsRemainNull() throws Exception {
        migrateTo("11");
        UUID vacancyId = UUID.randomUUID();
        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            UUID companyId = UUID.randomUUID();
            statement.execute("INSERT INTO company (id, name) VALUES ('%s', 'Legacy Co')".formatted(companyId));
            statement.execute("""
                    INSERT INTO vacancy (id, company_id, title, url)
                    VALUES ('%s', '%s', 'Legacy Backend Role', 'https://example.com/legacy-job')
                    """.formatted(vacancyId, companyId));
        }

        migrateTo("latest");

        try (Connection connection = jdbcConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT canonical_url FROM vacancy WHERE id = '%s'".formatted(vacancyId))) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString("canonical_url")).isNull();
        }
    }

    @Test
    void migration_multipleLegacyNullCanonicalUrls_areAllowed() throws Exception {
        migrateTo("latest");
        UUID companyId = UUID.randomUUID();
        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO company (id, name) VALUES ('%s', 'Acme')".formatted(companyId));
            statement.execute("""
                    INSERT INTO vacancy (id, company_id, title, url, canonical_url)
                    VALUES ('%s', '%s', 'Role One', 'https://example.com/job-1', NULL)
                    """.formatted(UUID.randomUUID(), companyId));
            statement.execute("""
                    INSERT INTO vacancy (id, company_id, title, url, canonical_url)
                    VALUES ('%s', '%s', 'Role Two', 'https://example.com/job-2', NULL)
                    """.formatted(UUID.randomUUID(), companyId));
        }

        try (Connection connection = jdbcConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT COUNT(*) FROM vacancy WHERE company_id = '%s' AND canonical_url IS NULL"
                                .formatted(companyId))) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getLong(1)).isEqualTo(2L);
        }
    }

    @Test
    void migration_twoDistinctNonNullCanonicalUrls_areAllowed() throws Exception {
        migrateTo("latest");
        UUID companyId = UUID.randomUUID();
        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO company (id, name) VALUES ('%s', 'Acme')".formatted(companyId));
            statement.execute("""
                    INSERT INTO vacancy (id, company_id, title, url, canonical_url)
                    VALUES ('%s', '%s', 'Role One', 'https://example.com/job-1', 'https://example.com/job-1')
                    """.formatted(UUID.randomUUID(), companyId));
            statement.execute("""
                    INSERT INTO vacancy (id, company_id, title, url, canonical_url)
                    VALUES ('%s', '%s', 'Role Two', 'https://example.com/job-2', 'https://example.com/job-2')
                    """.formatted(UUID.randomUUID(), companyId));
        }

        try (Connection connection = jdbcConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT COUNT(*) FROM vacancy WHERE company_id = '%s'".formatted(companyId))) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getLong(1)).isEqualTo(2L);
        }
    }

    @Test
    void migration_partialUniqueIndex_rejectsTwoEqualNonNullCanonicalUrls() throws Exception {
        migrateTo("latest");
        UUID companyId = UUID.randomUUID();
        String canonicalUrl = "https://example.com/job-shared";
        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO company (id, name) VALUES ('%s', 'Acme')".formatted(companyId));
            statement.execute("""
                    INSERT INTO vacancy (id, company_id, title, url, canonical_url)
                    VALUES ('%s', '%s', 'Role One', 'https://example.com/job-a', '%s')
                    """.formatted(UUID.randomUUID(), companyId, canonicalUrl));
        }

        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            assertThrows(SQLException.class, () -> statement.execute("""
                    INSERT INTO vacancy (id, company_id, title, url, canonical_url)
                    VALUES ('%s', '%s', 'Role Two', 'https://example.com/job-b', '%s')
                    """.formatted(UUID.randomUUID(), companyId, canonicalUrl)));
        }
    }

    @Test
    void migration_doesNotBackfillOrModifyExistingRows() throws Exception {
        migrateTo("11");
        UUID vacancyId = UUID.randomUUID();
        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            UUID companyId = UUID.randomUUID();
            statement.execute("INSERT INTO company (id, name) VALUES ('%s', 'Another Co')".formatted(companyId));
            statement.execute("""
                    INSERT INTO vacancy (id, company_id, title, url) VALUES ('%s', '%s', 'Role', 'https://example.com/kept')
                    """.formatted(vacancyId, companyId));
        }

        migrateTo("latest");

        try (Connection connection = jdbcConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT url, canonical_url FROM vacancy WHERE id = '%s'".formatted(vacancyId))) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString("url")).isEqualTo("https://example.com/kept");
            assertThat(resultSet.getString("canonical_url")).isNull();
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
        return java.sql.DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
