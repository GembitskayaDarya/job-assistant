package com.darya.jobassistant.candidates;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sprint 9 Step 4 correction requirement 4: proves the test-only Candidate Profile seed
 * ({@code src/test/resources/db/testdata/V900__seed_test_primary_candidate_profile.sql}) never
 * reaches production Flyway locations. Deliberately does not extend {@code AbstractIntegrationTest}
 * (whose {@code spring.flyway.locations} override adds {@code classpath:db/testdata}) - this test
 * relies on the plain default from {@code application.yml} ({@code classpath:db/migration} only),
 * exactly what a real deployment uses.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class CandidateProfileMigrationLocationsIsolationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private DataSource dataSource;

    @Test
    void productionFlywayLocations_applyV16ThroughV18_withoutTheTestOnlySeed() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank")) {
            List<String> appliedVersions = new ArrayList<>();
            while (resultSet.next()) {
                appliedVersions.add(resultSet.getString("version"));
            }
            assertThat(appliedVersions).contains("16", "17", "18");
            assertThat(appliedVersions).doesNotContain("900");
        }

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT count(*) FROM candidate_profile")) {
            resultSet.next();
            // No profile is seeded in production Flyway locations - this table stays empty.
            assertThat(resultSet.getInt(1)).isZero();
        }
    }
}
