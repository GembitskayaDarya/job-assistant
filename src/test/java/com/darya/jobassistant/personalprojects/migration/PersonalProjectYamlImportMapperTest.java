package com.darya.jobassistant.personalprojects.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.candidates.config.CandidateProfileProperties;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProject;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PersonalProjectYamlImportMapperTest {

    private final UUID candidateProfileId = UUID.randomUUID();

    @Test
    void toPersonalProjects_mapsIdNameDescriptionUrlAndDates() {
        UUID projectId = UUID.randomUUID();
        CandidateProfileProperties.PersonalProjectProperties source = new CandidateProfileProperties.PersonalProjectProperties(
                projectId, "Example Project", "A factual description", "https://github.com/example/project",
                null, null, List.of(), List.of());
        CandidateProfileProperties properties = propertiesWith(List.of(source));

        List<PersonalProject> result = PersonalProjectYamlImportMapper.toPersonalProjects(properties, candidateProfileId);

        assertThat(result).hasSize(1);
        PersonalProject project = result.get(0);
        assertThat(project.id()).isEqualTo(projectId);
        assertThat(project.name()).isEqualTo("Example Project");
        assertThat(project.description()).isEqualTo("A factual description");
        assertThat(project.url()).isEqualTo("https://github.com/example/project");
        assertThat(project.candidateProfileId()).isEqualTo(candidateProfileId);
        assertThat(project.version()).isZero();
    }

    @Test
    void toPersonalProjects_missingId_isRejected() {
        CandidateProfileProperties.PersonalProjectProperties source = new CandidateProfileProperties.PersonalProjectProperties(
                null, "Example Project", null, null, null, null, List.of(), List.of());
        CandidateProfileProperties properties = propertiesWith(List.of(source));

        assertThatThrownBy(() -> PersonalProjectYamlImportMapper.toPersonalProjects(properties, candidateProfileId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Example Project");
    }

    @Test
    void toPersonalProjects_displayOrderMatchesSourceListPosition() {
        CandidateProfileProperties.PersonalProjectProperties second = new CandidateProfileProperties.PersonalProjectProperties(
                UUID.randomUUID(), "Second Project", null, null, null, null, List.of(), List.of());
        CandidateProfileProperties.PersonalProjectProperties first = new CandidateProfileProperties.PersonalProjectProperties(
                UUID.randomUUID(), "First Project", null, null, null, null, List.of(), List.of());
        CandidateProfileProperties properties = propertiesWith(List.of(second, first));

        List<PersonalProject> result = PersonalProjectYamlImportMapper.toPersonalProjects(properties, candidateProfileId);

        assertThat(result.get(0).name()).isEqualTo("Second Project");
        assertThat(result.get(0).displayOrder()).isZero();
        assertThat(result.get(1).name()).isEqualTo("First Project");
        assertThat(result.get(1).displayOrder()).isEqualTo(1);
    }

    @Test
    void toPersonalProjects_highlightsAndTechnologies_displayOrderMatchesSourceListPosition() {
        CandidateProfileProperties.PersonalProjectProperties source = new CandidateProfileProperties.PersonalProjectProperties(
                UUID.randomUUID(), "Example Project", null, null, null, null,
                List.of("First highlight", "Second highlight"),
                List.of(new CandidateProfileProperties.TechnologyProperties("Java", "Language"),
                        new CandidateProfileProperties.TechnologyProperties("Kafka", "Messaging")));
        CandidateProfileProperties properties = propertiesWith(List.of(source));

        PersonalProject project = PersonalProjectYamlImportMapper.toPersonalProjects(properties, candidateProfileId).get(0);

        assertThat(project.highlights()).extracting(h -> h.text()).containsExactly("First highlight", "Second highlight");
        assertThat(project.highlights().get(1).displayOrder()).isEqualTo(1);
        assertThat(project.technologies()).extracting(t -> t.name()).containsExactly("Java", "Kafka");
        assertThat(project.technologies().get(0).category()).isEqualTo("Language");
        // Neither highlights nor technologies are given explicit ids - PersonalProjectRepositoryAdapter
        // replaces both lists on every save of their owning project, so a supplied id would be discarded.
        assertThat(project.highlights()).allSatisfy(h -> assertThat(h.id()).isNull());
        assertThat(project.technologies()).allSatisfy(t -> assertThat(t.id()).isNull());
    }

    @Test
    void toPersonalProjects_noPersonalProjectsConfigured_returnsEmptyList() {
        CandidateProfileProperties properties = propertiesWith(List.of());

        assertThat(PersonalProjectYamlImportMapper.toPersonalProjects(properties, candidateProfileId)).isEmpty();
    }

    @Test
    void toPersonalProjects_nullProperties_isRejected() {
        assertThatThrownBy(() -> PersonalProjectYamlImportMapper.toPersonalProjects(null, candidateProfileId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toPersonalProjects_nullCandidateProfileId_isRejected() {
        CandidateProfileProperties properties = propertiesWith(List.of());

        assertThatThrownBy(() -> PersonalProjectYamlImportMapper.toPersonalProjects(properties, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private CandidateProfileProperties propertiesWith(List<CandidateProfileProperties.PersonalProjectProperties> personalProjects) {
        return new CandidateProfileProperties(
                "Backend Engineer", "Senior", List.of(), List.of(), 6, null,
                null, null, null, null, null, null, List.of(), personalProjects);
    }
}
