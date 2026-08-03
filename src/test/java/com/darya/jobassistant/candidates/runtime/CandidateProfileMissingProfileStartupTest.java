package com.darya.jobassistant.candidates.runtime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.JobAssistantApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sprint 9 Step 4 negative counterpart to {@code JobAssistantApplicationTests}: proves normal
 * runtime startup fails fast when the configured "primary" Candidate Profile does not exist,
 * against a real Flyway-migrated (V16-V18 only - no {@code AbstractIntegrationTest} test-data
 * seed location) empty PostgreSQL schema, with a valid {@code candidate-profile.yml}-equivalent
 * nowhere in the picture (the config import is never even set here) - so a present-but-unused
 * YAML source can never mask a missing database row.
 *
 * <p>Deliberately does not extend {@code AbstractIntegrationTest}: that base class's {@code
 * spring.flyway.locations} override seeds a valid profile, which is exactly the condition this
 * test needs to NOT hold. Builds and runs the real {@link JobAssistantApplication} directly
 * (bypassing {@code @SpringBootTest}'s context caching) so a startup failure surfaces as a thrown
 * exception this test can assert on, rather than as an opaque JUnit context-loading error.
 */
@Testcontainers
class CandidateProfileMissingProfileStartupTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void applicationStartup_failsFast_whenPersistentCandidateProfileIsMissing() {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(JobAssistantApplication.class)
                .web(WebApplicationType.NONE);

        assertThatThrownBy(() -> builder.run(
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--spring.ai.openai.api-key=test-key",
                // No classpath:db/testdata here, unlike AbstractIntegrationTest - candidate_profile
                // is migrated (V16-V18) but left empty.
                "--spring.flyway.locations=classpath:db/migration"))
                .isInstanceOf(CandidateProfileNotConfiguredException.class)
                .hasMessageContaining("primary");
    }
}
