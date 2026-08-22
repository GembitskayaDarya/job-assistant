package com.darya.jobassistant.candidatecontext.cv.baseline;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Sprint 11 Big Block 7: Spring-binding shape for the {@code baseline-cv.*} configuration tree - the
 * approved, manually-curated Universal CV selection/rewrite decisions, expressed by natural factual
 * values (company name, position title, project name, exact bullet text, skill/technology name -
 * never a raw id) rather than the {@code CvSource*} UUIDs those values currently resolve to. See
 * {@link BaselineCvSelectionResolver}'s javadoc for exactly why natural values were chosen over ids
 * here, and {@code config/examples/baseline-cv-selection.example.yml} for a fully-worked fake
 * example.
 *
 * <p>Deliberately unvalidated at binding time (no {@code @Validated}/JSR-303 constraints), mirroring
 * {@code CandidateProfileProperties}'s convention: {@code spring.config.import} for this file is
 * {@code optional:}, so this bean must always bind successfully - including with every field left
 * {@code null}/empty when the file is entirely absent (the normal case for a fresh checkout with no
 * private baseline configured yet). {@link GenerateBaselineCvUseCase} is the one place that decides
 * whether an empty/absent configuration is acceptable to act on.
 *
 * <p>{@link #mentoring} is {@code null} when there is no dedicated Mentoring section - {@code
 * CvAssembler} never emits one in that case, matching every other optional facet's null-means-absent
 * rule. Deliberately a single canonical constructor: a second, narrower convenience overload used to
 * exist here to keep pre-Mentoring call sites compiling, but a record with more than one constructor
 * defeats Spring's automatic constructor-binding detection for {@code @ConfigurationProperties}
 * (it silently falls back to JavaBean-style binding and fails with "No default constructor found") -
 * every caller must pass all five fields explicitly, {@code mentoring} included.
 */
@ConfigurationProperties(prefix = "baseline-cv")
public record BaselineCvSelectionProperties(
        String professionalSummary,
        List<String> skills,
        List<PositionSelection> positions,
        List<PersonalProjectSelection> personalProjects,
        PositionSelection mentoring
) {

    public BaselineCvSelectionProperties {
        skills = skills == null ? List.of() : List.copyOf(skills);
        positions = positions == null ? List.of() : List.copyOf(positions);
        personalProjects = personalProjects == null ? List.of() : List.copyOf(personalProjects);
    }

    /**
     * One {@code baseline-cv.positions[]} entry - {@link #company}/{@link #position} match {@code
     * CvSourceCompany#name()}/{@code CvSourcePosition#title()} exactly. {@link #responsibilities}/
     * {@link #achievements} follow {@link BaselineCvSelectionResolver}'s three-value selection
     * convention: {@code ["*"]} selects every factual bullet in source order, an explicit list of
     * exact source bullet text selects and orders only those, and an empty list is a deliberate
     * "show none" selection.
     */
    public record PositionSelection(
            String company,
            String position,
            List<String> responsibilities,
            List<String> achievements,
            List<ProjectSelection> projects
    ) {
        public PositionSelection {
            responsibilities = responsibilities == null ? List.of() : List.copyOf(responsibilities);
            achievements = achievements == null ? List.of() : List.copyOf(achievements);
            projects = projects == null ? List.of() : List.copyOf(projects);
        }
    }

    /** One {@code positions[].projects[]} entry - {@link #project} matches {@code CvSourceProject#name()} exactly. */
    public record ProjectSelection(String project, List<String> technologies) {
        public ProjectSelection {
            technologies = technologies == null ? List.of() : List.copyOf(technologies);
        }
    }

    /** One {@code baseline-cv.personal-projects[]} entry - {@link #project} matches {@code CvSourcePersonalProject#name()} exactly. */
    public record PersonalProjectSelection(String project, List<String> highlights, List<String> technologies) {
        public PersonalProjectSelection {
            highlights = highlights == null ? List.of() : List.copyOf(highlights);
            technologies = technologies == null ? List.of() : List.copyOf(technologies);
        }
    }
}
