package com.darya.jobassistant.vacancies.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Exercises the V12 (Sprint 8 Step 4B1) and V13 (Sprint 8 Step 4B2D) migrations in isolation from
 * the rest of the Spring context - plain JDBC and Flyway's Java API only, no {@code
 * @DataJpaTest}/JPA involved.
 *
 * <p>V12 tests migrate only up to V11 (the last pre-Step-4B1 schema version), insert a row shaped
 * exactly like a real pre-migration vacancy, then migrate to V12 specifically (not {@code latest})
 * and assert what V12 alone is responsible for: a nullable {@code canonical_url} column that
 * existing rows pick up as {@code null}, and a partial unique index that protects only non-null
 * values. They target V12 explicitly, not {@code latest}, because V13 now refuses to migrate a
 * database that still has a {@code canonical_url IS NULL} row - exactly the state these tests
 * deliberately construct to prove V12's own, narrower behavior.
 *
 * <p>V13 tests migrate a V12 database to V13 (or a fresh database straight to {@code latest}) and
 * assert what V13 alone is responsible for: {@code canonical_url} becomes {@code NOT NULL}, the
 * transitional partial index is replaced by a full unique index under the exact same name, and a
 * database that isn't actually ready (a null, blank, or duplicate canonical URL somewhere) is
 * refused with the schema left completely unchanged.
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

        // Targets V12 specifically, not "latest": V13 refuses to migrate a database that still
        // has a canonical_url IS NULL row, which is exactly the state asserted below.
        migrateTo("12");

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
        // Targets V12 specifically: this proves V12's own partial-index behavior (multiple nulls
        // are allowed), which V13 later replaces with NOT NULL - migrating to "latest" here would
        // no longer be able to insert either row at all.
        migrateTo("12");
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
    void migration_uniqueIndex_rejectsTwoEqualNonNullCanonicalUrls() throws Exception {
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

        // Targets V12 specifically, for the same reason as the other tests above.
        migrateTo("12");

        try (Connection connection = jdbcConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT url, canonical_url FROM vacancy WHERE id = '%s'".formatted(vacancyId))) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString("url")).isEqualTo("https://example.com/kept");
            assertThat(resultSet.getString("canonical_url")).isNull();
        }
    }

    // ---- V13 (Sprint 8 Step 4B2D) ----

    @Test
    void v13_succeeds_setsNotNull_andReplacesThePartialIndexWithAFullUniqueIndexOfTheSameName() throws Exception {
        migrateTo("12");
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

        migrateTo("13"); // item 1: succeeds without throwing

        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            try (ResultSet columnInfo = statement.executeQuery("""
                    SELECT is_nullable FROM information_schema.columns
                    WHERE table_name = 'vacancy' AND column_name = 'canonical_url'
                    """)) {
                assertThat(columnInfo.next()).isTrue();
                assertThat(columnInfo.getString("is_nullable")).isEqualTo("NO"); // item 2
            }
            try (ResultSet indexInfo = statement.executeQuery("""
                    SELECT indisunique, indpred FROM pg_index WHERE indexrelid = 'uk_vacancy_canonical_url'::regclass
                    """)) {
                assertThat(indexInfo.next()).isTrue(); // item 3: the index exists under the exact name
                assertThat(indexInfo.getBoolean("indisunique")).isTrue(); // item 4: unique
                assertThat(indexInfo.getString("indpred")).isNull(); // item 5: predicate is null - not partial
            }
            try (ResultSet temporaryIndex = statement.executeQuery("""
                    SELECT 1 FROM pg_indexes WHERE tablename = 'vacancy' AND indexname = 'uk_vacancy_canonical_url_full'
                    """)) {
                assertThat(temporaryIndex.next()).isFalse(); // item 6: temporary index name does not remain
            }
        }
    }

    @Test
    void v13_preservesExistingCanonicalUrlAndOriginalUrlValues() throws Exception {
        migrateTo("12");
        UUID companyId = UUID.randomUUID();
        UUID vacancyId = UUID.randomUUID();
        String url = "https://example.com/job-preserved?utm_source=linkedin";
        String canonicalUrl = "https://example.com/job-preserved";
        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO company (id, name) VALUES ('%s', 'Acme')".formatted(companyId));
            statement.execute("""
                    INSERT INTO vacancy (id, company_id, title, url, canonical_url)
                    VALUES ('%s', '%s', 'Role', '%s', '%s')
                    """.formatted(vacancyId, companyId, url, canonicalUrl));
        }

        migrateTo("13");

        try (Connection connection = jdbcConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT url, canonical_url FROM vacancy WHERE id = '%s'".formatted(vacancyId))) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString("url")).isEqualTo(url); // item 8
            assertThat(resultSet.getString("canonical_url")).isEqualTo(canonicalUrl); // item 7
        }
    }

    @Test
    void v13_refusesDatabase_withNullCanonicalUrl() throws Exception {
        migrateTo("12");
        UUID companyId = UUID.randomUUID();
        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO company (id, name) VALUES ('%s', 'Acme')".formatted(companyId));
            statement.execute("""
                    INSERT INTO vacancy (id, company_id, title, url)
                    VALUES ('%s', '%s', 'Role', 'https://example.com/job-null')
                    """.formatted(UUID.randomUUID(), companyId));
        }

        assertThrows(FlywayException.class, () -> migrateTo("13"));
    }

    @Test
    void v13_refusesDatabase_withBlankCanonicalUrl() throws Exception {
        migrateTo("12");
        UUID companyId = UUID.randomUUID();
        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO company (id, name) VALUES ('%s', 'Acme')".formatted(companyId));
            statement.execute("""
                    INSERT INTO vacancy (id, company_id, title, url, canonical_url)
                    VALUES ('%s', '%s', 'Role', 'https://example.com/job-blank', '   ')
                    """.formatted(UUID.randomUUID(), companyId));
        }

        assertThrows(FlywayException.class, () -> migrateTo("13"));
    }

    @Test
    void v13_refusesDatabase_withDuplicateCanonicalUrlValues() throws Exception {
        migrateTo("12");
        UUID companyId = UUID.randomUUID();
        String sharedCanonicalUrl = "https://example.com/job-shared-for-v13-precondition-test";
        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO company (id, name) VALUES ('%s', 'Acme')".formatted(companyId));
            // V12's own uk_vacancy_canonical_url index would itself reject a second row sharing
            // this value - dropped here, deliberately, purely to construct the otherwise
            // unreachable "duplicate already exists" state V13's own precondition must
            // independently catch (e.g. after a hypothetical manual DBA intervention).
            statement.execute("DROP INDEX uk_vacancy_canonical_url");
            statement.execute("""
                    INSERT INTO vacancy (id, company_id, title, url, canonical_url)
                    VALUES ('%s', '%s', 'Role One', 'https://example.com/job-a', '%s')
                    """.formatted(UUID.randomUUID(), companyId, sharedCanonicalUrl));
            statement.execute("""
                    INSERT INTO vacancy (id, company_id, title, url, canonical_url)
                    VALUES ('%s', '%s', 'Role Two', 'https://example.com/job-b', '%s')
                    """.formatted(UUID.randomUUID(), companyId, sharedCanonicalUrl));
        }

        assertThrows(FlywayException.class, () -> migrateTo("13"));
    }

    @Test
    void v13_failedMigration_leavesNoPartialSchemaChanges() throws Exception {
        migrateTo("12");
        UUID companyId = UUID.randomUUID();
        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO company (id, name) VALUES ('%s', 'Acme')".formatted(companyId));
            statement.execute("""
                    INSERT INTO vacancy (id, company_id, title, url)
                    VALUES ('%s', '%s', 'Role', 'https://example.com/job-null-2')
                    """.formatted(UUID.randomUUID(), companyId));
        }

        assertThrows(FlywayException.class, () -> migrateTo("13"));

        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            try (ResultSet columnInfo = statement.executeQuery("""
                    SELECT is_nullable FROM information_schema.columns
                    WHERE table_name = 'vacancy' AND column_name = 'canonical_url'
                    """)) {
                assertThat(columnInfo.next()).isTrue();
                assertThat(columnInfo.getString("is_nullable")).isEqualTo("YES");
            }
            try (ResultSet indexInfo = statement.executeQuery("""
                    SELECT indpred FROM pg_index WHERE indexrelid = 'uk_vacancy_canonical_url'::regclass
                    """)) {
                assertThat(indexInfo.next()).isTrue();
                assertThat(indexInfo.getString("indpred")).isNotNull(); // still the original partial index
            }
            try (ResultSet temporaryIndex = statement.executeQuery("""
                    SELECT 1 FROM pg_indexes WHERE tablename = 'vacancy' AND indexname = 'uk_vacancy_canonical_url_full'
                    """)) {
                assertThat(temporaryIndex.next()).isFalse();
            }
        }
    }

    @Test
    void freshDatabase_migratesFromV1ThroughV13Successfully() throws Exception {
        migrateTo("latest");

        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            try (ResultSet columnInfo = statement.executeQuery("""
                    SELECT is_nullable FROM information_schema.columns
                    WHERE table_name = 'vacancy' AND column_name = 'canonical_url'
                    """)) {
                assertThat(columnInfo.next()).isTrue();
                assertThat(columnInfo.getString("is_nullable")).isEqualTo("NO");
            }
            try (ResultSet indexInfo = statement.executeQuery("""
                    SELECT indisunique, indpred FROM pg_index WHERE indexrelid = 'uk_vacancy_canonical_url'::regclass
                    """)) {
                assertThat(indexInfo.next()).isTrue();
                assertThat(indexInfo.getBoolean("indisunique")).isTrue();
                assertThat(indexInfo.getString("indpred")).isNull();
            }
        }
    }

    @Test
    void v12DatabaseAfterCompletingBackfill_migratesToV13Successfully() throws Exception {
        migrateTo("12");
        UUID companyId = UUID.randomUUID();
        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO company (id, name) VALUES ('%s', 'Acme')".formatted(companyId));
            for (int i = 0; i < 5; i++) {
                String url = "https://example.com/backfilled-job-" + i;
                statement.execute("""
                        INSERT INTO vacancy (id, company_id, title, url, canonical_url)
                        VALUES ('%s', '%s', '%s', '%s', '%s')
                        """.formatted(UUID.randomUUID(), companyId, "Backfilled Role " + i, url, url));
            }
        }

        migrateTo("13");

        try (Connection connection = jdbcConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM vacancy WHERE canonical_url IS NULL")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getLong(1)).isZero();
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
