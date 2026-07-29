package com.darya.jobassistant.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Exercises the V14 (Sprint 8 Step 9) migration - the standard ShedLock PostgreSQL lock table -
 * the same plain-JDBC-and-Flyway-Java-API strategy as {@code VacancyCanonicalUrlMigrationTest},
 * independent of the rest of the Spring context.
 *
 * <p>The container is a non-static {@code @Container} field, matching {@code
 * VacancyCanonicalUrlMigrationTest}'s convention, so each test method gets a fresh database and
 * therefore a fresh Flyway schema history.
 */
@Testcontainers
class ShedLockMigrationTest {

    @Container
    private final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void freshDatabase_migratesFromV1ThroughV14Successfully() throws Exception {
        migrateTo("latest");

        assertShedlockTableMetadataIsCorrect();
    }

    @Test
    void v13Database_migratesToV14Successfully() throws Exception {
        migrateTo("13");

        migrateTo("14");

        assertShedlockTableMetadataIsCorrect();
    }

    @Test
    void shedlockTable_requiresNoSeedRow() throws Exception {
        migrateTo("14");

        try (Connection connection = jdbcConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM shedlock")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getLong(1)).isZero();
        }
    }

    @Test
    void shedlockTable_columnsMatchStandardShedLockContract() throws Exception {
        migrateTo("14");

        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            try (ResultSet nameColumn = statement.executeQuery("""
                    SELECT data_type, character_maximum_length, is_nullable
                    FROM information_schema.columns
                    WHERE table_name = 'shedlock' AND column_name = 'name'
                    """)) {
                assertThat(nameColumn.next()).isTrue();
                assertThat(nameColumn.getString("data_type")).isEqualTo("character varying");
                assertThat(nameColumn.getInt("character_maximum_length")).isEqualTo(64);
                assertThat(nameColumn.getString("is_nullable")).isEqualTo("NO");
            }
            try (ResultSet lockedByColumn = statement.executeQuery("""
                    SELECT data_type, character_maximum_length, is_nullable
                    FROM information_schema.columns
                    WHERE table_name = 'shedlock' AND column_name = 'locked_by'
                    """)) {
                assertThat(lockedByColumn.next()).isTrue();
                assertThat(lockedByColumn.getString("data_type")).isEqualTo("character varying");
                assertThat(lockedByColumn.getInt("character_maximum_length")).isEqualTo(255);
                assertThat(lockedByColumn.getString("is_nullable")).isEqualTo("NO");
            }
            try (ResultSet lockUntilColumn = statement.executeQuery("""
                    SELECT data_type, is_nullable
                    FROM information_schema.columns
                    WHERE table_name = 'shedlock' AND column_name = 'lock_until'
                    """)) {
                assertThat(lockUntilColumn.next()).isTrue();
                assertThat(lockUntilColumn.getString("data_type")).isEqualTo("timestamp without time zone");
                assertThat(lockUntilColumn.getString("is_nullable")).isEqualTo("NO");
            }
            try (ResultSet lockedAtColumn = statement.executeQuery("""
                    SELECT data_type, is_nullable
                    FROM information_schema.columns
                    WHERE table_name = 'shedlock' AND column_name = 'locked_at'
                    """)) {
                assertThat(lockedAtColumn.next()).isTrue();
                assertThat(lockedAtColumn.getString("data_type")).isEqualTo("timestamp without time zone");
                assertThat(lockedAtColumn.getString("is_nullable")).isEqualTo("NO");
            }
        }
    }

    @Test
    void shedlockTable_primaryKeyIsOnName() throws Exception {
        migrateTo("14");

        try (Connection connection = jdbcConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("""
                        SELECT a.attname
                        FROM pg_index i
                        JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY(i.indkey)
                        WHERE i.indrelid = 'shedlock'::regclass AND i.indisprimary
                        """)) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString("attname")).isEqualTo("name");
            assertThat(resultSet.next()).isFalse();
        }

        try (Connection connection = jdbcConnection();
                Statement statement = connection.createStatement();
                ResultSet constraintName = statement.executeQuery("""
                        SELECT conname FROM pg_constraint
                        WHERE conrelid = 'shedlock'::regclass AND contype = 'p'
                        """)) {
            assertThat(constraintName.next()).isTrue();
            assertThat(constraintName.getString("conname")).isEqualTo("pk_shedlock");
        }
    }

    @Test
    void v14_doesNotBackfillOrModifyExistingVacancyData() throws Exception {
        migrateTo("13");
        UUID companyId = UUID.randomUUID();
        UUID vacancyId = UUID.randomUUID();
        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO company (id, name) VALUES ('%s', 'Acme')".formatted(companyId));
            statement.execute("""
                    INSERT INTO vacancy (id, company_id, title, url, canonical_url)
                    VALUES ('%s', '%s', 'Role', 'https://example.com/job-preserved', 'https://example.com/job-preserved')
                    """.formatted(vacancyId, companyId));
        }

        migrateTo("14");

        try (Connection connection = jdbcConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT title, url, canonical_url FROM vacancy WHERE id = '%s'".formatted(vacancyId))) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString("title")).isEqualTo("Role");
            assertThat(resultSet.getString("url")).isEqualTo("https://example.com/job-preserved");
            assertThat(resultSet.getString("canonical_url")).isEqualTo("https://example.com/job-preserved");
        }
    }

    @Test
    void v14_failedMigration_leavesPreExistingTableUnchanged() throws Exception {
        // Simulates an unexpected pre-existing "shedlock" relation (e.g. a leftover from a manual
        // DBA intervention) - V14's CREATE TABLE has no IF NOT EXISTS guard by design, so it must
        // fail loudly rather than silently accept an unknown schema, and must not partially alter
        // whatever was already there.
        migrateTo("13");
        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE shedlock (unexpected_column INT)");
        }

        assertThrows(FlywayException.class, () -> migrateTo("14"));

        try (Connection connection = jdbcConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("""
                        SELECT column_name FROM information_schema.columns WHERE table_name = 'shedlock'
                        """)) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString("column_name")).isEqualTo("unexpected_column");
            assertThat(resultSet.next()).isFalse();
        }
    }

    private void assertShedlockTableMetadataIsCorrect() throws Exception {
        try (Connection connection = jdbcConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("""
                        SELECT 1 FROM information_schema.tables WHERE table_name = 'shedlock'
                        """)) {
            assertThat(resultSet.next()).isTrue();
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
