package com.darya.jobassistant.personalprojects.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.candidates.entity.CandidateProfileEntity;
import com.darya.jobassistant.candidates.repository.CandidateProfileRepository;
import com.darya.jobassistant.config.JpaAuditingConfig;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProject;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProjectCandidateProfileNotFoundException;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProjectConcurrentModificationException;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProjectHighlight;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProjectTechnology;
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
 * Sprint 11 Step 5: proves {@link PersonalProjectRepositoryAdapter} against real PostgreSQL -
 * most importantly, that each Personal Project is an independent aggregate whose save never
 * touches a sibling project's rows or a Candidate Profile save's rows (the exact bug the revised
 * design exists to eliminate - see {@code PersonalProject}'s javadoc).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(JpaAuditingConfig.class)
class PersonalProjectRepositoryAdapterTest {

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

    private PersonalProjectRepositoryAdapter adapter() {
        return new PersonalProjectRepositoryAdapter(
                personalProjectRepository, personalProjectHighlightRepository, personalProjectTechnologyRepository,
                candidateProfileRepository, Clock.systemUTC(), entityManager);
    }

    @Test
    void save_newProject_persistsProjectHighlightsAndTechnologies() {
        UUID candidateProfileId = candidateProfile("save-new-" + UUID.randomUUID()).getId();

        PersonalProject saved = adapter().save(project(candidateProfileId, "Example Project", 0));

        assertThat(saved.id()).isNotNull();
        assertThat(saved.version()).isZero();
        assertThat(saved.highlights()).extracting(PersonalProjectHighlight::text).containsExactly("Built a REST API");
        assertThat(saved.technologies()).extracting(PersonalProjectTechnology::name).containsExactly("Java");
    }

    @Test
    void save_unknownCandidateProfile_throwsCandidateProfileNotFoundException() {
        UUID unknownCandidateProfileId = UUID.randomUUID();

        assertThatThrownBy(() -> adapter().save(project(unknownCandidateProfileId, "Example Project", 0)))
                .isInstanceOf(PersonalProjectCandidateProfileNotFoundException.class);
    }

    /** Acceptance correction: a caller-assigned id that does not yet exist creates the row using exactly that id. */
    @Test
    void save_nonNullIdNotYetExisting_createsRowWithExactlyThatId() {
        UUID candidateProfileId = candidateProfile("explicit-id-" + UUID.randomUUID()).getId();
        UUID explicitId = UUID.randomUUID();
        PersonalProject withExplicitId = new PersonalProject(
                explicitId, candidateProfileId, "Example Project", null, null, null, null, 0,
                List.of(new PersonalProjectHighlight("Built a REST API", 0)),
                List.of(new PersonalProjectTechnology("Java", null, 0)), 0L);

        PersonalProject saved = adapter().save(withExplicitId);

        assertThat(saved.id()).isEqualTo(explicitId);
        assertThat(saved.version()).isZero();
        assertThat(personalProjectRepository.findById(explicitId)).isPresent();
    }

    /**
     * Acceptance correction: the exact scenario {@code PersonalProjectImportUseCase} relies on -
     * saving with the same explicit, not-yet-existing id twice in a row updates the same row
     * (second call finds it via {@code existsById}) rather than creating a second one.
     */
    @Test
    void save_sameExplicitIdSavedTwice_updatesTheSameRow_neverCreatesADuplicate() {
        UUID candidateProfileId = candidateProfile("explicit-id-repeat-" + UUID.randomUUID()).getId();
        UUID explicitId = UUID.randomUUID();
        PersonalProject first = new PersonalProject(
                explicitId, candidateProfileId, "Example Project", null, null, null, null, 0, List.of(), List.of(), 0L);

        PersonalProject firstSaved = adapter().save(first);
        PersonalProject second = new PersonalProject(
                explicitId, candidateProfileId, "Example Project Renamed", null, null, null, null, 0,
                List.of(), List.of(), firstSaved.version());
        PersonalProject secondSaved = adapter().save(second);

        assertThat(secondSaved.id()).isEqualTo(explicitId);
        assertThat(secondSaved.version()).isEqualTo(firstSaved.version() + 1);
        assertThat(secondSaved.name()).isEqualTo("Example Project Renamed");
        assertThat(adapter().findAllByCandidateProfileId(candidateProfileId)).hasSize(1);
    }

    @Test
    void findAllByCandidateProfileId_ordersByDisplayOrderThenId() {
        UUID candidateProfileId = candidateProfile("find-ordered-" + UUID.randomUUID()).getId();
        adapter().save(project(candidateProfileId, "Second Project", 1));
        adapter().save(project(candidateProfileId, "First Project", 0));

        List<PersonalProject> projects = adapter().findAllByCandidateProfileId(candidateProfileId);

        assertThat(projects).extracting(PersonalProject::name).containsExactly("First Project", "Second Project");
    }

    @Test
    void save_staleVersion_throwsConcurrentModificationExceptionAndLeavesRowUntouched() {
        UUID candidateProfileId = candidateProfile("stale-version-" + UUID.randomUUID()).getId();
        PersonalProject saved = adapter().save(project(candidateProfileId, "Example Project", 0));

        // saved.version() is 0 after a fresh save - 99 is guaranteed stale/nonexistent.
        PersonalProject staleUpdate = new PersonalProject(
                saved.id(), candidateProfileId, "Renamed Project", null, null, null, null, 0,
                saved.highlights(), saved.technologies(), 99L);

        assertThatThrownBy(() -> adapter().save(staleUpdate))
                .isInstanceOf(PersonalProjectConcurrentModificationException.class);

        PersonalProject reloaded = adapter().findAllByCandidateProfileId(candidateProfileId).get(0);
        assertThat(reloaded.name()).isEqualTo("Example Project");
    }

    @Test
    void save_updatingHighlightsOnlyWithNoScalarChange_stillBumpsVersion() {
        UUID candidateProfileId = candidateProfile("child-only-change-" + UUID.randomUUID()).getId();
        PersonalProject saved = adapter().save(project(candidateProfileId, "Example Project", 0));

        PersonalProject withNewHighlightOnly = new PersonalProject(
                saved.id(), candidateProfileId, saved.name(), saved.description(), saved.url(),
                saved.startDate(), saved.endDate(), saved.displayOrder(),
                List.of(new PersonalProjectHighlight("A different highlight", 0)),
                saved.technologies(), saved.version());
        PersonalProject updated = adapter().save(withNewHighlightOnly);

        assertThat(updated.version()).isEqualTo(saved.version() + 1);
        assertThat(updated.highlights()).extracting(PersonalProjectHighlight::text).containsExactly("A different highlight");
    }

    @Test
    void save_updatingOneProject_neverModifiesASiblingProject() {
        UUID candidateProfileId = candidateProfile("isolation-" + UUID.randomUUID()).getId();
        PersonalProject savedA = adapter().save(project(candidateProfileId, "Project A", 0));
        PersonalProject savedB = adapter().save(project(candidateProfileId, "Project B", 1));

        UUID highlightIdB = savedB.highlights().get(0).id();
        UUID technologyIdB = savedB.technologies().get(0).id();

        PersonalProject renamedA = new PersonalProject(
                savedA.id(), candidateProfileId, "Project A Renamed", savedA.description(), savedA.url(),
                savedA.startDate(), savedA.endDate(), savedA.displayOrder(), savedA.highlights(), savedA.technologies(), savedA.version());
        adapter().save(renamedA);

        entityManager.flush();
        entityManager.clear();

        PersonalProject reloadedB = adapter().findAllByCandidateProfileId(candidateProfileId).stream()
                .filter(p -> p.id().equals(savedB.id()))
                .findFirst()
                .orElseThrow();
        assertThat(reloadedB.name()).isEqualTo("Project B");
        assertThat(reloadedB.version()).isEqualTo(savedB.version());
        assertThat(reloadedB.highlights().get(0).id()).isEqualTo(highlightIdB);
        assertThat(reloadedB.technologies().get(0).id()).isEqualTo(technologyIdB);

        // Direct database counts for B's rows - proves no unscoped delete ever touched them.
        assertThat(personalProjectHighlightRepository.findAllByPersonalProjectIdInOrderByDisplayOrderAsc(List.of(savedB.id())))
                .hasSize(1);
        assertThat(personalProjectTechnologyRepository.findAllByPersonalProjectIdInOrderByDisplayOrderAsc(List.of(savedB.id())))
                .hasSize(1);

        // A really did change - proves this isn't a no-op test.
        PersonalProject reloadedA = adapter().findAllByCandidateProfileId(candidateProfileId).stream()
                .filter(p -> p.id().equals(savedA.id()))
                .findFirst()
                .orElseThrow();
        assertThat(reloadedA.name()).isEqualTo("Project A Renamed");
        assertThat(reloadedA.version()).isEqualTo(savedA.version() + 1);
    }

    @Test
    void save_candidateProfileSave_neverRewritesPersonalProjects() {
        CandidateProfileEntity candidateProfile = candidateProfile("profile-independence-" + UUID.randomUUID());
        UUID candidateProfileId = candidateProfile.getId();
        PersonalProject saved = adapter().save(project(candidateProfileId, "Example Project", 0));
        UUID projectId = saved.id();
        UUID highlightId = saved.highlights().get(0).id();

        // Simulate an unrelated Candidate Profile scalar-field update - never touches personal_project.
        int updatedRows = candidateProfileRepository.updateIfVersionMatches(
                candidateProfileId, candidateProfile.getProfileKey(), "Updated Role", "Senior", 6,
                null, null, null, null, null, null, null, false, null,
                null, null, null, null, null, null, java.time.Instant.now(), candidateProfile.getVersion());
        assertThat(updatedRows).isEqualTo(1);
        entityManager.flush();
        entityManager.clear();

        PersonalProject reloaded = adapter().findAllByCandidateProfileId(candidateProfileId).get(0);
        assertThat(reloaded.id()).isEqualTo(projectId);
        assertThat(reloaded.highlights().get(0).id()).isEqualTo(highlightId);
        assertThat(reloaded.version()).isEqualTo(saved.version());
    }

    private PersonalProject project(UUID candidateProfileId, String name, int displayOrder) {
        return new PersonalProject(
                candidateProfileId, name, "A factual description", "https://github.com/example/project",
                null, null, displayOrder,
                List.of(new PersonalProjectHighlight("Built a REST API", 0)),
                List.of(new PersonalProjectTechnology("Java", "Language", 0)));
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
