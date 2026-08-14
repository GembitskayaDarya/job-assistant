package com.darya.jobassistant.personalprojects.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.personalprojects.aggregate.PersonalProject;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProjectHighlight;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProjectRepositoryPort;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProjectTechnology;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Sprint 11 Step 5 acceptance correction: proves {@link PersonalProjectImportUseCase}'s dispatch
 * logic - create when the id is unseen, skip {@code save} entirely when semantically unchanged
 * (see {@link PersonalProjectSemanticComparator}), update-with-the-existing-version when a real
 * factual difference is found - and that it never touches a project outside the current source
 * list. {@code PersonalProjectImportUseCaseIntegrationTest} proves the same scenarios end-to-end
 * against real PostgreSQL, including that a genuine no-op leaves the persisted root version and
 * every child UUID byte-for-byte unchanged - a guarantee this mock-based test cannot itself prove,
 * since nothing here is actually written.
 */
@ExtendWith(MockitoExtension.class)
class PersonalProjectImportUseCaseTest {

    @Mock
    private PersonalProjectRepositoryPort repositoryPort;

    private final UUID candidateProfileId = UUID.randomUUID();

    // ---- a. first import creates the project ----

    @Test
    void apply_projectIdNotAmongExisting_savesSourceProjectAsIsToCreateWithThatId() {
        UUID projectId = UUID.randomUUID();
        PersonalProject source = project(projectId, "Example Project", 0L);
        when(repositoryPort.findAllByCandidateProfileId(candidateProfileId)).thenReturn(List.of());

        PersonalProjectImportResult result = new PersonalProjectImportUseCase(repositoryPort).apply(List.of(source), candidateProfileId);

        verify(repositoryPort).save(source);
        assertThat(result.sourceCount()).isEqualTo(1);
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.updated()).isZero();
        assertThat(result.unchanged()).isZero();
    }

    // ---- b/c/d/e. repeated identical import performs no save, so version and child UUIDs (owned by the untouched row) are preserved ----

    @Test
    void apply_semanticallyUnchangedProject_neverCallsSave() {
        UUID projectId = UUID.randomUUID();
        PersonalProject source = project(projectId, "Example Project", 0L);
        PersonalProject existing = project(projectId, "Example Project", 7L);
        when(repositoryPort.findAllByCandidateProfileId(candidateProfileId)).thenReturn(List.of(existing));

        PersonalProjectImportResult result = new PersonalProjectImportUseCase(repositoryPort).apply(List.of(source), candidateProfileId);

        verify(repositoryPort, never()).save(any());
        assertThat(result.created()).isZero();
        assertThat(result.updated()).isZero();
        assertThat(result.unchanged()).isEqualTo(1);
    }

    @Test
    void apply_semanticallyUnchangedProjectWithHighlightsAndTechnologies_neverCallsSave() {
        UUID projectId = UUID.randomUUID();
        List<PersonalProjectHighlight> highlights = List.of(new PersonalProjectHighlight("Built a REST API", 0));
        List<PersonalProjectTechnology> technologies = List.of(new PersonalProjectTechnology("Kafka", "Messaging", 0));
        PersonalProject source = projectWithChildren(projectId, "Example Project", highlights, technologies, 0L);
        // Existing carries different (real, persisted) child ids and version - still semantically equal.
        PersonalProject existing = projectWithChildren(
                projectId, "Example Project",
                List.of(new PersonalProjectHighlight(UUID.randomUUID(), "Built a REST API", 0)),
                List.of(new PersonalProjectTechnology(UUID.randomUUID(), "kafka", "Messaging", 0)),
                7L);
        when(repositoryPort.findAllByCandidateProfileId(candidateProfileId)).thenReturn(List.of(existing));

        PersonalProjectImportResult result = new PersonalProjectImportUseCase(repositoryPort).apply(List.of(source), candidateProfileId);

        verify(repositoryPort, never()).save(any());
        assertThat(result.unchanged()).isEqualTo(1);
    }

    @Test
    void apply_repeatedIdenticalImport_performsNoSaveOnSecondRun() {
        UUID projectId = UUID.randomUUID();
        PersonalProject source = project(projectId, "Example Project", 0L);
        PersonalProject afterFirstImport = project(projectId, "Example Project", 0L);

        when(repositoryPort.findAllByCandidateProfileId(candidateProfileId))
                .thenReturn(List.of())
                .thenReturn(List.of(afterFirstImport));

        PersonalProjectImportUseCase useCase = new PersonalProjectImportUseCase(repositoryPort);
        PersonalProjectImportResult first = useCase.apply(List.of(source), candidateProfileId);
        PersonalProjectImportResult second = useCase.apply(List.of(source), candidateProfileId);

        assertThat(first.created()).isEqualTo(1);
        assertThat(second.created()).isZero();
        assertThat(second.updated()).isZero();
        assertThat(second.unchanged()).isEqualTo(1);
        verify(repositoryPort, org.mockito.Mockito.times(1)).save(any());
    }

    // ---- f. changing a scalar project field causes exactly one update ----

    @Test
    void apply_changedScalarField_causesExactlyOneUpdate() {
        UUID projectId = UUID.randomUUID();
        PersonalProject source = project(projectId, "Renamed Project", 0L);
        PersonalProject existing = project(projectId, "Example Project", 7L);
        when(repositoryPort.findAllByCandidateProfileId(candidateProfileId)).thenReturn(List.of(existing));

        PersonalProjectImportResult result = new PersonalProjectImportUseCase(repositoryPort).apply(List.of(source), candidateProfileId);

        ArgumentCaptor<PersonalProject> saved = ArgumentCaptor.forClass(PersonalProject.class);
        verify(repositoryPort, org.mockito.Mockito.times(1)).save(saved.capture());
        assertThat(saved.getValue().name()).isEqualTo("Renamed Project");
        assertThat(saved.getValue().version()).isEqualTo(7L);
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.unchanged()).isZero();
    }

    // ---- g. changing a highlight causes exactly one update ----

    @Test
    void apply_changedHighlight_causesExactlyOneUpdate() {
        UUID projectId = UUID.randomUUID();
        PersonalProject source = projectWithChildren(
                projectId, "Example Project", List.of(new PersonalProjectHighlight("Updated highlight", 0)), List.of(), 0L);
        PersonalProject existing = projectWithChildren(
                projectId, "Example Project", List.of(new PersonalProjectHighlight(UUID.randomUUID(), "Original highlight", 0)), List.of(), 7L);
        when(repositoryPort.findAllByCandidateProfileId(candidateProfileId)).thenReturn(List.of(existing));

        PersonalProjectImportResult result = new PersonalProjectImportUseCase(repositoryPort).apply(List.of(source), candidateProfileId);

        verify(repositoryPort, org.mockito.Mockito.times(1)).save(any());
        assertThat(result.updated()).isEqualTo(1);
    }

    // ---- h. changing a technology causes exactly one update ----

    @Test
    void apply_changedTechnology_causesExactlyOneUpdate() {
        UUID projectId = UUID.randomUUID();
        PersonalProject source = projectWithChildren(
                projectId, "Example Project", List.of(), List.of(new PersonalProjectTechnology("PostgreSQL", "Database", 0)), 0L);
        PersonalProject existing = projectWithChildren(
                projectId, "Example Project", List.of(), List.of(new PersonalProjectTechnology(UUID.randomUUID(), "Kafka", "Messaging", 0)), 7L);
        when(repositoryPort.findAllByCandidateProfileId(candidateProfileId)).thenReturn(List.of(existing));

        PersonalProjectImportResult result = new PersonalProjectImportUseCase(repositoryPort).apply(List.of(source), candidateProfileId);

        verify(repositoryPort, org.mockito.Mockito.times(1)).save(any());
        assertThat(result.updated()).isEqualTo(1);
    }

    // ---- i. a sibling project remains untouched ----

    @Test
    void apply_projectNotMentionedInSource_isNeverTouched() {
        UUID mentionedId = UUID.randomUUID();
        UUID untouchedId = UUID.randomUUID();
        PersonalProject source = project(mentionedId, "Mentioned Project", 0L);
        PersonalProject untouched = project(untouchedId, "Sibling Project", 3L);
        when(repositoryPort.findAllByCandidateProfileId(candidateProfileId)).thenReturn(List.of(untouched));

        new PersonalProjectImportUseCase(repositoryPort).apply(List.of(source), candidateProfileId);

        ArgumentCaptor<PersonalProject> saved = ArgumentCaptor.forClass(PersonalProject.class);
        verify(repositoryPort).save(saved.capture());
        assertThat(saved.getValue().id()).isEqualTo(mentionedId);
        verify(repositoryPort, never()).save(untouched);
    }

    @Test
    void apply_nullSourceProjects_isRejected() {
        assertThatThrownBy(() -> new PersonalProjectImportUseCase(repositoryPort).apply(null, candidateProfileId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void apply_nullCandidateProfileId_isRejected() {
        assertThatThrownBy(() -> new PersonalProjectImportUseCase(repositoryPort).apply(List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private PersonalProject project(UUID id, String name, long version) {
        return new PersonalProject(id, candidateProfileId, name, null, null, null, null, 0, List.of(), List.of(), version);
    }

    private PersonalProject projectWithChildren(
            UUID id, String name, List<PersonalProjectHighlight> highlights, List<PersonalProjectTechnology> technologies, long version) {
        return new PersonalProject(id, candidateProfileId, name, null, null, null, null, 0, highlights, technologies, version);
    }
}
