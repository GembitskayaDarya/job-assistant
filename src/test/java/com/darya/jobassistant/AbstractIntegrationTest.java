package com.darya.jobassistant;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sprint 9 Step 4: {@code spring.flyway.locations} adds {@code classpath:db/testdata} - a
 * test-only classpath location (never on the production classpath) that seeds a valid "primary"
 * Candidate Profile row via {@code src/test/resources/db/testdata/V900__seed_test_primary_candidate_profile.sql}.
 * Flyway runs this during context refresh, before {@code CandidateProfileStartupValidator}'s
 * {@code ApplicationReadyEvent} listener fires, so every subclass's full application context can
 * pass normal-runtime fail-fast startup validation without each test seeding a profile itself.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestPropertySource(properties = {
        "spring.ai.openai.api-key=test-key",
        "spring.flyway.locations=classpath:db/migration,classpath:db/testdata"
})
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
}
