package com.darya.jobassistant.personalprojects.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.candidates.entity.CandidateProfileEntity;
import com.darya.jobassistant.candidates.repository.CandidateProfileRepository;
import com.darya.jobassistant.config.JpaAuditingConfig;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProject;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProjectHighlight;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProjectRepositoryPort;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProjectTechnology;
import com.darya.jobassistant.personalprojects.persistence.PersonalProjectRepositoryAdapter;
import com.darya.jobassistant.personalprojects.repository.PersonalProjectHighlightRepository;
import com.darya.jobassistant.personalprojects.repository.PersonalProjectRepository;
import com.darya.jobassistant.personalprojects.repository.PersonalProjectTechnologyRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sprint 11 Step 5 acceptance correction: proves {@link PersonalProjectImportUseCase}'s semantic
 * no-op detection end to end against real PostgreSQL via the real {@link
 * PersonalProjectRepositoryAdapter} - specifically that a genuine no-op import leaves the
 * persisted root version and every child (highlight/technology) UUID byte-for-byte unchanged,
 * which a mock-based test cannot itself prove since nothing would actually be written either way.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(JpaAuditingConfig.class)
class PersonalProjectImportUseCaseIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private CandidateProfileRepository candidateProfileRepository;

    @Autowired
    private PersonalProjectRepository personalProjectRepository;

    @Autowired
    private PersonalProjectHighlightRepository personalProjectHighlightRepository;

    @Autowired
    private PersonalProjectTechnologyRepository personalProjectTechnologyRepository;

    @Autowired
    private EntityManager entityManager;

    private PersonalProjectRepositoryPort port() {
        return new PersonalProjectRepositoryAdapter(
                personalProjectRepository, personalProjectHighlightRepository, personalProjectTechnologyRepository,
                candidateProfileRepository, Clock.systemUTC(), entityManager);
    }

    private PersonalProjectImportUseCase useCase() {
        return new PersonalProjectImportUseCase(port());
    }

    // ---- a. first import creates the project ----

    @Test
    void apply_firstImport_createsTheProject() {
        UUID candidateProfileId = candidateProfile("first-import-" + UUID.randomUUID()).getId();
        UUID projectId = UUID.randomUUID();
        PersonalProject source = project(projectId, candidateProfileId, "Example Project");

        PersonalProjectImportResult result = useCase().apply(List.of(source), candidateProfileId);

        assertThat(result.created()).isEqualTo(1);
        assertThat(port().findAllByCandidateProfileId(candidateProfileId)).extracting(PersonalProject::id).containsExactly(projectId);
    }

    // ---- b/c/d/e. repeated identical import: no save, root version and child UUIDs preserved ----

    @Test
    void apply_repeatedIdenticalImport_preservesVersionAndHighlightAndTechnologyUuids() {
        UUID candidateProfileId = candidateProfile("identical-import-" + UUID.randomUUID()).getId();
        UUID projectId = UUID.randomUUID();
        PersonalProject source = projectWithChildren(projectId, candidateProfileId, "Example Project");

        useCase().apply(List.of(source), candidateProfileId);
        PersonalProject afterFirstImport = port().findAllByCandidateProfileId(candidateProfileId).get(0);
        UUID highlightId = afterFirstImport.highlights().get(0).id();
        UUID technologyId = afterFirstImport.technologies().get(0).id();

        // Same source content again, in a fresh mapper-shaped instance (id-less children, version 0),
        // exactly as a second application startup would produce it.
        PersonalProject sourceAgain = projectWithChildren(projectId, candidateProfileId, "Example Project");
        PersonalProjectImportResult result = useCase().apply(List.of(sourceAgain), candidateProfileId);

        assertThat(result.created()).isZero();
        assertThat(result.updated()).isZero();
        assertThat(result.unchanged()).isEqualTo(1);

        PersonalProject afterSecondImport = port().findAllByCandidateProfileId(candidateProfileId).get(0);
        assertThat(afterSecondImport.version()).isEqualTo(afterFirstImport.version());
        assertThat(afterSecondImport.highlights().get(0).id()).isEqualTo(highlightId);
        assertThat(afterSecondImport.technologies().get(0).id()).isEqualTo(technologyId);
    }

    // ---- f. changing a scalar project field causes exactly one update ----

    @Test
    void apply_changedScalarField_causesExactlyOneUpdate() {
        UUID candidateProfileId = candidateProfile("changed-scalar-" + UUID.randomUUID()).getId();
        UUID projectId = UUID.randomUUID();
        useCase().apply(List.of(project(projectId, candidateProfileId, "Example Project")), candidateProfileId);
        PersonalProject afterFirstImport = port().findAllByCandidateProfileId(candidateProfileId).get(0);

        PersonalProjectImportResult result =
                useCase().apply(List.of(project(projectId, candidateProfileId, "Renamed Project")), candidateProfileId);

        assertThat(result.updated()).isEqualTo(1);
        PersonalProject reloaded = port().findAllByCandidateProfileId(candidateProfileId).get(0);
        assertThat(reloaded.name()).isEqualTo("Renamed Project");
        assertThat(reloaded.version()).isEqualTo(afterFirstImport.version() + 1);
    }

    // ---- g. changing a highlight causes exactly one update ----

    @Test
    void apply_changedHighlight_causesExactlyOneUpdate() {
        UUID candidateProfileId = candidateProfile("changed-highlight-" + UUID.randomUUID()).getId();
        UUID projectId = UUID.randomUUID();
        useCase().apply(List.of(projectWithChildren(projectId, candidateProfileId, "Example Project")), candidateProfileId);

        PersonalProject changedHighlight = new PersonalProject(
                projectId, candidateProfileId, "Example Project", null, null, null, null, 0,
                List.of(new PersonalProjectHighlight("A different highlight", 0)),
                List.of(new PersonalProjectTechnology("Java", "Language", 0)), 0L);
        PersonalProjectImportResult result = useCase().apply(List.of(changedHighlight), candidateProfileId);

        assertThat(result.updated()).isEqualTo(1);
        assertThat(port().findAllByCandidateProfileId(candidateProfileId).get(0).highlights())
                .extracting(PersonalProjectHighlight::text).containsExactly("A different highlight");
    }

    // ---- h. changing a technology causes exactly one update ----

    @Test
    void apply_changedTechnology_causesExactlyOneUpdate() {
        UUID candidateProfileId = candidateProfile("changed-technology-" + UUID.randomUUID()).getId();
        UUID projectId = UUID.randomUUID();
        useCase().apply(List.of(projectWithChildren(projectId, candidateProfileId, "Example Project")), candidateProfileId);

        PersonalProject changedTechnology = new PersonalProject(
                projectId, candidateProfileId, "Example Project", null, null, null, null, 0,
                List.of(new PersonalProjectHighlight("Built a REST API", 0)),
                List.of(new PersonalProjectTechnology("PostgreSQL", "Database", 0)), 0L);
        PersonalProjectImportResult result = useCase().apply(List.of(changedTechnology), candidateProfileId);

        assertThat(result.updated()).isEqualTo(1);
        assertThat(port().findAllByCandidateProfileId(candidateProfileId).get(0).technologies())
                .extracting(PersonalProjectTechnology::name).containsExactly("PostgreSQL");
    }

    // ---- i. a sibling project remains untouched ----

    @Test
    void apply_siblingProjectNotInSource_remainsUntouched() {
        UUID candidateProfileId = candidateProfile("sibling-untouched-" + UUID.randomUUID()).getId();
        UUID mentionedId = UUID.randomUUID();
        UUID siblingId = UUID.randomUUID();
        useCase().apply(List.of(
                project(mentionedId, candidateProfileId, "Mentioned Project"),
                project(siblingId, candidateProfileId, "Sibling Project")), candidateProfileId);
        PersonalProject siblingBefore = port().findAllByCandidateProfileId(candidateProfileId).stream()
                .filter(p -> p.id().equals(siblingId)).findFirst().orElseThrow();

        // Second import only mentions the first project, with a real change - the sibling is absent from source entirely.
        useCase().apply(List.of(project(mentionedId, candidateProfileId, "Mentioned Project Renamed")), candidateProfileId);

        PersonalProject siblingAfter = port().findAllByCandidateProfileId(candidateProfileId).stream()
                .filter(p -> p.id().equals(siblingId)).findFirst().orElseThrow();
        assertThat(siblingAfter.version()).isEqualTo(siblingBefore.version());
        assertThat(siblingAfter.name()).isEqualTo("Sibling Project");
    }

    private PersonalProject project(UUID id, UUID candidateProfileId, String name) {
        return new PersonalProject(id, candidateProfileId, name, null, null, null, null, 0, List.of(), List.of(), 0L);
    }

    private PersonalProject projectWithChildren(UUID id, UUID candidateProfileId, String name) {
        return new PersonalProject(
                id, candidateProfileId, name, null, null, null, null, 0,
                List.of(new PersonalProjectHighlight("Built a REST API", 0)),
                List.of(new PersonalProjectTechnology("Java", "Language", 0)), 0L);
    }

    private CandidateProfileEntity candidateProfile(String profileKey) {
        return candidateProfileRepository.save(CandidateProfileEntity.builder()
                .profileKey(profileKey)
                .targetRole("Demo Backend Engineer")
                .seniority("Senior")
                .experienceYears(5)
                .build());
    }
}
