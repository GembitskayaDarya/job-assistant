package com.darya.jobassistant.personalprojects.migration;

import com.darya.jobassistant.candidates.config.CandidateProfileProperties;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProject;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProjectHighlight;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProjectTechnology;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Sprint 11 Step 5: maps the YAML-bound {@code candidate.personal-projects[]} section (see {@link
 * CandidateProfileProperties.PersonalProjectProperties}) to not-yet-persisted {@link
 * PersonalProject} aggregates for a given candidate - the same "extend the existing private
 * import approach" spirit as {@code candidates.migration.CandidateProfileYamlImportMapper},
 * deliberately without that class's fingerprint/parity/diff machinery: Personal Projects have no
 * dedicated import pipeline (unlike Career History's Sprint 9 Step 7 workflow) because nothing yet
 * requires one - each mapped project is simply handed to {@code
 * personalprojects.aggregate.PersonalProjectRepositoryPort#save} by whatever caller needs to seed
 * it (a one-off script, a future admin action, or a future runner if the need for one actually
 * materializes). {@code displayOrder} at every level is assigned from each YAML list's own
 * position, the same convention {@code CandidateProfileYamlImportMapper} uses for languages and
 * education.
 *
 * <p>Framework-free and stateless: no repository calls, no Spring dependencies, no persistence
 * write. Never invents a project, highlight, or technology - only source YAML content is mapped
 * through.
 *
 * <p><b>Acceptance correction:</b> every mapped project carries the id explicitly authored in
 * YAML ({@link CandidateProfileProperties.PersonalProjectProperties#id()}) - required, not
 * optional, since {@code PersonalProjectImportUseCase} uses it as the sole stable identity to
 * decide create-vs-update idempotently across repeated imports. A project's {@code name} is
 * deliberately never used for that purpose. Highlights/technologies are <em>not</em> given
 * explicit YAML ids: {@code PersonalProjectRepositoryAdapter} replaces a project's entire
 * highlight/technology graph on every save of that project (including a re-import), so any id
 * supplied for them would be silently discarded - asking YAML to supply one would misrepresent
 * their actual stability.
 */
public final class PersonalProjectYamlImportMapper {

    private PersonalProjectYamlImportMapper() {
    }

    public static List<PersonalProject> toPersonalProjects(CandidateProfileProperties properties, UUID candidateProfileId) {
        if (properties == null) {
            throw new IllegalArgumentException("Source candidate profile properties must not be null");
        }
        if (candidateProfileId == null) {
            throw new IllegalArgumentException("Candidate profile id must not be null");
        }
        List<PersonalProject> result = new ArrayList<>();
        List<CandidateProfileProperties.PersonalProjectProperties> source = properties.personalProjects();
        for (int i = 0; i < source.size(); i++) {
            result.add(toPersonalProject(source.get(i), candidateProfileId, i));
        }
        return result;
    }

    private static PersonalProject toPersonalProject(
            CandidateProfileProperties.PersonalProjectProperties source, UUID candidateProfileId, int displayOrder) {
        if (source.id() == null) {
            throw new IllegalArgumentException(
                    "Personal project '" + source.name() + "' in the private candidate configuration is missing its "
                            + "required stable id - assign a fixed UUID (see config/examples/candidate-profile.example.yml) "
                            + "so repeated imports update this exact project instead of creating a duplicate");
        }
        return new PersonalProject(
                source.id(),
                candidateProfileId,
                source.name(),
                source.description(),
                source.url(),
                source.startDate(),
                source.endDate(),
                displayOrder,
                toHighlights(source.highlights()),
                toTechnologies(source.technologies()),
                0L);
    }

    private static List<PersonalProjectHighlight> toHighlights(List<String> highlights) {
        List<PersonalProjectHighlight> result = new ArrayList<>();
        for (int i = 0; i < highlights.size(); i++) {
            result.add(new PersonalProjectHighlight(highlights.get(i), i));
        }
        return result;
    }

    private static List<PersonalProjectTechnology> toTechnologies(List<CandidateProfileProperties.TechnologyProperties> technologies) {
        List<PersonalProjectTechnology> result = new ArrayList<>();
        for (int i = 0; i < technologies.size(); i++) {
            CandidateProfileProperties.TechnologyProperties technology = technologies.get(i);
            result.add(new PersonalProjectTechnology(technology.name(), technology.category(), i));
        }
        return result;
    }
}
