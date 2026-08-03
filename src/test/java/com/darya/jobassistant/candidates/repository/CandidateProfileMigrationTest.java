package com.darya.jobassistant.candidates.repository;

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
 * Exercises V16 (Sprint 9 Step 1) in isolation from the rest of the Spring context - plain JDBC
 * and Flyway's Java API only, matching {@code VacancyRecommendationWorkflowMigrationTest}'s
 * convention. This is specifically where {@code chk_candidate_profile_skill_proficiency} is
 * proven at the database level: {@link CandidateProfileRepositoryTest} cannot exercise an
 * out-of-enum value (e.g. {@code NONE} or {@code ADVANCED}) because {@code
 * CandidateProfileSkillEntity.proficiency}'s {@code @Enumerated(STRING)} conversion has no Java
 * constant to convert, so JPA would never even send such an INSERT.
 *
 * <p>The container is deliberately a non-static {@code @Container} field so each test method gets
 * a fresh database and therefore a fresh Flyway schema history.
 */
@Testcontainers
class CandidateProfileMigrationTest {

    @Container
    private final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void freshDatabase_migratesFromV1ThroughV16Successfully() throws Exception {
        migrateTo("latest");

        try (Connection connection = jdbcConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("""
                        SELECT table_name FROM information_schema.tables
                        WHERE table_name IN ('candidate_profile', 'candidate_profile_skill', 'candidate_profile_language')
                        """)) {
            java.util.Set<String> tables = new java.util.HashSet<>();
            while (resultSet.next()) {
                tables.add(resultSet.getString("table_name"));
            }
            assertThat(tables).containsExactlyInAnyOrder(
                    "candidate_profile", "candidate_profile_skill", "candidate_profile_language");
        }
    }

    @Test
    void v16_skillProficiency_rejectsNoneValue() throws Exception {
        migrateTo("16");
        UUID profileId = insertProfile();

        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            SQLException failure = assertThrows(SQLException.class, () -> statement.execute("""
                    INSERT INTO candidate_profile_skill (id, candidate_profile_id, skill_name, proficiency)
                    VALUES ('%s', '%s', 'Java', 'NONE')
                    """.formatted(UUID.randomUUID(), profileId)));
            assertThat(failure.getMessage()).contains("chk_candidate_profile_skill_proficiency");
        }
    }

    @Test
    void v16_skillProficiency_rejectsArbitraryUnsupportedValue() throws Exception {
        migrateTo("16");
        UUID profileId = insertProfile();

        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            SQLException failure = assertThrows(SQLException.class, () -> statement.execute("""
                    INSERT INTO candidate_profile_skill (id, candidate_profile_id, skill_name, proficiency)
                    VALUES ('%s', '%s', 'Java', 'ADVANCED')
                    """.formatted(UUID.randomUUID(), profileId)));
            assertThat(failure.getMessage()).contains("chk_candidate_profile_skill_proficiency");
        }
    }

    @Test
    void v16_skillProficiency_acceptsAllFourSupportedValues() throws Exception {
        migrateTo("16");
        UUID profileId = insertProfile();

        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            for (String proficiency : new String[] {"BASIC", "WORKING", "STRONG", "EXPERT"}) {
                statement.execute("""
                        INSERT INTO candidate_profile_skill (id, candidate_profile_id, skill_name, proficiency)
                        VALUES ('%s', '%s', 'Skill-%s', '%s')
                        """.formatted(UUID.randomUUID(), profileId, proficiency, proficiency));
            }
        }

        try (Connection connection = jdbcConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT COUNT(*) FROM candidate_profile_skill WHERE candidate_profile_id = '%s'".formatted(profileId))) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getLong(1)).isEqualTo(4L);
        }
    }

    @Test
    void v16_salaryCurrency_rejectsValueLongerThanThreeCharacters() throws Exception {
        migrateTo("16");

        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            assertThrows(SQLException.class, () -> statement.execute("""
                    INSERT INTO candidate_profile
                        (id, profile_key, target_role, experience_years, salary_currency)
                    VALUES ('%s', 'currency-too-long-%s', 'Backend Engineer', 5, 'EURO')
                    """.formatted(UUID.randomUUID(), UUID.randomUUID())));
        }
    }

    @Test
    void v16_candidateProfileSkill_foreignKeyCascadesOnProfileDelete() throws Exception {
        migrateTo("16");
        UUID profileId = insertProfile();
        UUID skillId = UUID.randomUUID();
        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO candidate_profile_skill (id, candidate_profile_id, skill_name, proficiency)
                    VALUES ('%s', '%s', 'Java', 'EXPERT')
                    """.formatted(skillId, profileId));
            statement.execute("DELETE FROM candidate_profile WHERE id = '%s'".formatted(profileId));
        }

        try (Connection connection = jdbcConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT 1 FROM candidate_profile_skill WHERE id = '%s'".formatted(skillId))) {
            assertThat(resultSet.next()).isFalse();
        }
    }

    // ---- V18: candidate_profile_preference.priority_order (Sprint 9 Step 3 correction) ----

    @Test
    void v18_priorityOrder_rejectsNegativeValue() throws Exception {
        migrateTo("18");
        UUID profileId = insertProfile();

        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            SQLException failure = assertThrows(SQLException.class, () -> statement.execute("""
                    INSERT INTO candidate_profile_preference (id, candidate_profile_id, preference_type, preference_value, importance, priority_order)
                    VALUES ('%s', '%s', 'CONTRACT_TYPE', 'B2B', 'PREFERRED', -1)
                    """.formatted(UUID.randomUUID(), profileId)));
            assertThat(failure.getMessage()).contains("chk_candidate_profile_preference_priority_order_non_negative");
        }
    }

    @Test
    void v18_priorityMatchesType_rejectsPriorityOrderOnNonOrderSignificantType() throws Exception {
        migrateTo("18");
        UUID profileId = insertProfile();

        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            SQLException failure = assertThrows(SQLException.class, () -> statement.execute("""
                    INSERT INTO candidate_profile_preference (id, candidate_profile_id, preference_type, preference_value, importance, priority_order)
                    VALUES ('%s', '%s', 'ALLOWED_WORK_COUNTRY', 'Poland', NULL, 0)
                    """.formatted(UUID.randomUUID(), profileId)));
            assertThat(failure.getMessage()).contains("chk_candidate_profile_preference_priority_matches_type");
        }
    }

    @Test
    void v18_priorityMatchesType_rejectsOrderSignificantTypeWithoutPriorityOrder() throws Exception {
        migrateTo("18");
        UUID profileId = insertProfile();

        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            SQLException failure = assertThrows(SQLException.class, () -> statement.execute("""
                    INSERT INTO candidate_profile_preference (id, candidate_profile_id, preference_type, preference_value, importance, priority_order)
                    VALUES ('%s', '%s', 'CONTRACT_TYPE', 'B2B', 'PREFERRED', NULL)
                    """.formatted(UUID.randomUUID(), profileId)));
            assertThat(failure.getMessage()).contains("chk_candidate_profile_preference_priority_matches_type");
        }
    }

    @Test
    void v18_uniquePriorityOrder_rejectsDuplicateWithinSameProfileAndType() throws Exception {
        migrateTo("18");
        UUID profileId = insertProfile();

        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO candidate_profile_preference (id, candidate_profile_id, preference_type, preference_value, importance, priority_order)
                    VALUES ('%s', '%s', 'CONTRACT_TYPE', 'B2B', 'PREFERRED', 0)
                    """.formatted(UUID.randomUUID(), profileId));
            SQLException failure = assertThrows(SQLException.class, () -> statement.execute("""
                    INSERT INTO candidate_profile_preference (id, candidate_profile_id, preference_type, preference_value, importance, priority_order)
                    VALUES ('%s', '%s', 'CONTRACT_TYPE', 'Full-time', 'PREFERRED', 0)
                    """.formatted(UUID.randomUUID(), profileId)));
            assertThat(failure.getMessage()).contains("uk_candidate_profile_preference_profile_type_priority");
        }
    }

    /** Standard SQL/PostgreSQL treats every NULL as distinct in a unique constraint - many rows of a non-order-significant type must not collide. */
    @Test
    void v18_multipleNullPriorityOrders_forSameProfileAndType_areAllAllowed() throws Exception {
        migrateTo("18");
        UUID profileId = insertProfile();

        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO candidate_profile_preference (id, candidate_profile_id, preference_type, preference_value, importance, priority_order)
                    VALUES ('%s', '%s', 'ALLOWED_WORK_COUNTRY', 'Poland', NULL, NULL)
                    """.formatted(UUID.randomUUID(), profileId));
            statement.execute("""
                    INSERT INTO candidate_profile_preference (id, candidate_profile_id, preference_type, preference_value, importance, priority_order)
                    VALUES ('%s', '%s', 'ALLOWED_WORK_COUNTRY', 'Germany', NULL, NULL)
                    """.formatted(UUID.randomUUID(), profileId));
        }

        try (Connection connection = jdbcConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(
                        "SELECT COUNT(*) FROM candidate_profile_preference WHERE candidate_profile_id = '%s'".formatted(profileId))) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getLong(1)).isEqualTo(2L);
        }
    }

    private UUID insertProfile() throws Exception {
        UUID profileId = UUID.randomUUID();
        try (Connection connection = jdbcConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO candidate_profile (id, profile_key, target_role, experience_years)
                    VALUES ('%s', 'profile-%s', 'Backend Engineer', 5)
                    """.formatted(profileId, profileId));
        }
        return profileId;
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
