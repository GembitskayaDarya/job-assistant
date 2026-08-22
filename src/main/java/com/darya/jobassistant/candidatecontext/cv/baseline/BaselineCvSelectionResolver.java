package com.darya.jobassistant.candidatecontext.cv.baseline;

import com.darya.jobassistant.candidatecontext.cv.baseline.BaselineCvSelectionProperties.PersonalProjectSelection;
import com.darya.jobassistant.candidatecontext.cv.baseline.BaselineCvSelectionProperties.PositionSelection;
import com.darya.jobassistant.candidatecontext.cv.baseline.BaselineCvSelectionProperties.ProjectSelection;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceAchievement;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceCompany;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourcePersonalProject;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourcePersonalProjectHighlight;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourcePersonalProjectTechnology;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourcePosition;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceProject;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceResponsibility;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceSnapshot;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceTechnology;
import com.darya.jobassistant.candidatecontext.cv.tailoring.CvAchievementTailoring;
import com.darya.jobassistant.candidatecontext.cv.tailoring.CvPersonalProjectTailoring;
import com.darya.jobassistant.candidatecontext.cv.tailoring.CvPositionTailoring;
import com.darya.jobassistant.candidatecontext.cv.tailoring.CvProjectTailoring;
import com.darya.jobassistant.candidatecontext.cv.tailoring.CvResponsibilityTailoring;
import com.darya.jobassistant.candidatecontext.cv.tailoring.CvTailoringResult;
import com.darya.jobassistant.candidates.CandidateSkillFacts;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Sprint 11 Big Block 7: resolves the approved, natural-value-authored {@link
 * BaselineCvSelectionProperties} against the current {@link CvSourceSnapshot} into an actual {@link
 * CvTailoringResult} - the smallest application-owned mechanism that can represent the approved
 * Universal CV's selection/rewrite decisions, reusing the entire Sprint 11 Block 6 pipeline (this
 * result still goes through {@code CvTailoringValidator} before {@code CvAssembler.assembleTailored},
 * exactly like an AI-produced result - see {@code GenerateBaselineCvUseCase}) rather than inventing a
 * second, parallel selection/assembly path.
 *
 * <h2>Why natural values, not ids (Part 2 identity-stability requirement)</h2>
 *
 * {@code CvSource*} child ids ({@code careerResponsibilityId}, {@code careerTechnologyId}, ...) are
 * derived deterministically from the private career-history source's own natural keys and display
 * order (see {@code careerhistory.importing.CareerHistoryImportIdGenerator}) - stable across a
 * re-import that leaves the underlying key/order unchanged, but <em>not</em> guaranteed stable
 * forever: reordering bullets, or restructuring the source YAML, changes them. A baseline config
 * hardcoding those ids would silently produce a wrong or incomplete CV the next time the private
 * career-history source is edited, with no signal anything had gone wrong. Matching by the same
 * factual display values already shown on the CV itself (company name, position title, project name,
 * exact bullet text, skill/technology name) - the same representation the approved content policy is
 * already written in terms of - avoids that risk entirely: an edit that changes one of these values
 * simply fails to match here, loudly (see {@link BaselineCvSelectionResolutionException}), rather
 * than resolving to the wrong node. No separate fingerprint/version guard is layered on top of this;
 * {@code CvTailoringValidator}, already run on this resolver's output before assembly, is itself a
 * sufficient cheap guard against any remaining drift (an id this resolver derives correctly but that
 * source-validation nonetheless rejects would only happen if the snapshot changed between resolution
 * and validation, an already-accepted narrow window elsewhere in this pipeline).
 *
 * <h2>Selection convention</h2>
 *
 * Every {@code List<String>} selection field in {@link BaselineCvSelectionProperties} has exactly
 * three interpretations, deliberately not a general rule language:
 *
 * <ul>
 *   <li>{@code ["*"]} (the sole element is the literal wildcard) - select every factual item for
 *       that node, unchanged, in source display order.
 *   <li>A list of exact factual values - select and order only those (an unmatched value throws).
 *   <li>An empty list - a deliberate, explicit "show none for this node" selection.
 * </ul>
 */
public final class BaselineCvSelectionResolver {

    private static final String SELECT_ALL = "*";

    private BaselineCvSelectionResolver() {
    }

    public static CvTailoringResult resolve(BaselineCvSelectionProperties config, CvSourceSnapshot snapshot) {
        if (config == null) {
            throw new IllegalArgumentException("Baseline CV selection config must not be null");
        }
        if (snapshot == null) {
            throw new IllegalArgumentException("CV source snapshot must not be null");
        }

        List<UUID> skillIds = resolveSkillIds(config.skills(), snapshot);

        List<CvPositionTailoring> positionTailoring = config.positions().stream()
                .map(selection -> resolvePosition(selection, snapshot))
                .toList();
        List<CvProjectTailoring> projectTailoring = config.positions().stream()
                .flatMap(selection -> resolveProjects(selection, snapshot).stream())
                .toList();
        List<CvPersonalProjectTailoring> personalProjectTailoring = config.personalProjects().stream()
                .map(selection -> resolvePersonalProject(selection, snapshot))
                .toList();
        CvPositionTailoring mentoring = config.mentoring() != null ? resolvePosition(config.mentoring(), snapshot) : null;

        String professionalSummary = blankToNull(config.professionalSummary());
        requireNonEmptyBaseline(professionalSummary, skillIds, positionTailoring, personalProjectTailoring);

        return new CvTailoringResult(professionalSummary, skillIds, positionTailoring, projectTailoring, personalProjectTailoring, mentoring);
    }

    /**
     * Sprint 11 Final CV Policy production fix: a real production incident had the live deployment
     * silently binding {@link BaselineCvSelectionProperties} to its all-empty default (the
     * {@code config/private/baseline-cv-selection.yml} file was not mounted into the container),
     * which resolved to a {@code CvTailoringResult} with no professional summary, no positions, and
     * no Personal Project - {@code CvAssembler} then produced a real, renderable, ATS-passing
     * {@code TailoredCvDocument} missing every approved section except header/skills/education/
     * languages, with no error anywhere in the pipeline. This is a configuration failure, never a
     * legitimate CV state.
     *
     * <p>Deliberately narrow: fires ONLY when {@code professionalSummary}, {@code skillIds}, {@code
     * positionTailoring}, AND {@code personalProjectTailoring} are ALL empty at once - the exact
     * "config file entirely absent" shape - never when a caller (most commonly a focused unit test)
     * legitimately exercises one facet of the config in isolation with the others left empty by
     * construction (e.g. an explicit "select zero skills" test with a real position present, or a
     * personal-project-only selection with no positions). A single empty field is a normal,
     * expected, valid selection (see this resolver's own "three-value convention" javadoc); every
     * field empty at once is not.
     */
    private static void requireNonEmptyBaseline(String professionalSummary, List<UUID> skillIds,
            List<CvPositionTailoring> positionTailoring, List<CvPersonalProjectTailoring> personalProjectTailoring) {
        if (professionalSummary == null && skillIds.isEmpty() && positionTailoring.isEmpty() && personalProjectTailoring.isEmpty()) {
            throw new BaselineCvSelectionResolutionException(
                    "Baseline CV selection config resolved to no content at all (professionalSummary=missing, skills=0"
                            + ", positions=0, personalProjects=0) - this almost always means the "
                            + "configured baseline-cv-selection.yml file was not found at startup (check BASELINE_CV_SELECTION_PATH "
                            + "and, in a container, that the file is actually mounted), not a genuinely empty approved CV.");
        }
    }

    private static List<UUID> resolveSkillIds(List<String> skillNames, CvSourceSnapshot snapshot) {
        if (isSelectAll(skillNames)) {
            return snapshot.candidateProfile().skills().stream()
                    .filter(skill -> skill.candidateSkillId() != null)
                    .map(CandidateSkillFacts::candidateSkillId)
                    .toList();
        }
        return skillNames.stream()
                .map(name -> requireSkillId(snapshot, name))
                .toList();
    }

    private static UUID requireSkillId(CvSourceSnapshot snapshot, String name) {
        for (CandidateSkillFacts skill : snapshot.candidateProfile().skills()) {
            if (skill.candidateSkillId() != null && skill.name().equals(name)) {
                return skill.candidateSkillId();
            }
        }
        throw new BaselineCvSelectionResolutionException("Baseline CV selection references an unknown skill: '" + name + "'");
    }

    private static CvPositionTailoring resolvePosition(PositionSelection selection, CvSourceSnapshot snapshot) {
        CvSourcePosition position = requirePosition(snapshot, selection.company(), selection.position());
        List<CvResponsibilityTailoring> responsibilities =
                resolveResponsibilities(position.responsibilities(), selection.responsibilities(), selection.position());
        List<CvAchievementTailoring> achievements =
                resolveAchievements(position.achievements(), selection.achievements(), selection.position());
        return new CvPositionTailoring(position.careerPositionId(), responsibilities, achievements);
    }

    private static List<CvProjectTailoring> resolveProjects(PositionSelection selection, CvSourceSnapshot snapshot) {
        CvSourcePosition position = requirePosition(snapshot, selection.company(), selection.position());
        return selection.projects().stream().map(projectSelection -> {
            CvSourceProject project = requireProject(position, projectSelection.project());
            List<UUID> technologyIds = resolveTechnologyIds(
                    project.technologies(), projectSelection.technologies(), CvSourceTechnology::careerTechnologyId,
                    CvSourceTechnology::name, projectSelection.project());
            return new CvProjectTailoring(project.careerProjectId(), List.of(), List.of(), technologyIds);
        }).toList();
    }

    private static CvPersonalProjectTailoring resolvePersonalProject(PersonalProjectSelection selection, CvSourceSnapshot snapshot) {
        CvSourcePersonalProject project = requirePersonalProject(snapshot, selection.project());
        List<UUID> highlightIds = resolveIds(
                project.highlights(), selection.highlights(), CvSourcePersonalProjectHighlight::personalProjectHighlightId,
                CvSourcePersonalProjectHighlight::text, selection.project());
        List<UUID> technologyIds = resolveTechnologyIds(
                project.technologies(), selection.technologies(), CvSourcePersonalProjectTechnology::personalProjectTechnologyId,
                CvSourcePersonalProjectTechnology::name, selection.project());
        return new CvPersonalProjectTailoring(project.personalProjectId(), highlightIds, technologyIds);
    }

    // ==================== Natural-value lookups ====================

    private static CvSourcePosition requirePosition(CvSourceSnapshot snapshot, String companyName, String positionTitle) {
        for (CvSourceCompany company : snapshot.companies()) {
            if (!company.name().equals(companyName)) {
                continue;
            }
            for (CvSourcePosition position : company.positions()) {
                if (position.title().equals(positionTitle)) {
                    return position;
                }
            }
        }
        throw new BaselineCvSelectionResolutionException(
                "Baseline CV selection references an unknown company/position: '" + companyName + "' / '" + positionTitle + "'");
    }

    private static CvSourceProject requireProject(CvSourcePosition position, String projectName) {
        for (CvSourceProject project : position.projects()) {
            if (project.name().equals(projectName)) {
                return project;
            }
        }
        throw new BaselineCvSelectionResolutionException(
                "Baseline CV selection references an unknown project '" + projectName + "' under position '" + position.title() + "'");
    }

    private static CvSourcePersonalProject requirePersonalProject(CvSourceSnapshot snapshot, String projectName) {
        for (CvSourcePersonalProject project : snapshot.personalProjects()) {
            if (project.name().equals(projectName)) {
                return project;
            }
        }
        throw new BaselineCvSelectionResolutionException("Baseline CV selection references an unknown Personal Project: '" + projectName + "'");
    }

    // ==================== Selection resolution (see class javadoc for the three-value convention) ====================

    private static List<CvResponsibilityTailoring> resolveResponsibilities(
            List<CvSourceResponsibility> source, List<String> selection, String ownerDescription) {
        if (isSelectAll(selection)) {
            return source.stream().map(r -> new CvResponsibilityTailoring(r.careerResponsibilityId(), null)).toList();
        }
        return selection.stream()
                .map(text -> new CvResponsibilityTailoring(requireResponsibilityId(source, text, ownerDescription), null))
                .toList();
    }

    private static List<CvAchievementTailoring> resolveAchievements(
            List<CvSourceAchievement> source, List<String> selection, String ownerDescription) {
        if (isSelectAll(selection)) {
            return source.stream().map(a -> new CvAchievementTailoring(a.careerAchievementId(), null)).toList();
        }
        return selection.stream()
                .map(text -> new CvAchievementTailoring(requireAchievementId(source, text, ownerDescription), null))
                .toList();
    }

    private static UUID requireResponsibilityId(List<CvSourceResponsibility> source, String text, String ownerDescription) {
        for (CvSourceResponsibility responsibility : source) {
            if (responsibility.text().equals(text)) {
                return responsibility.careerResponsibilityId();
            }
        }
        throw new BaselineCvSelectionResolutionException(
                "Baseline CV selection references an unknown responsibility under '" + ownerDescription + "': '" + text + "'");
    }

    private static UUID requireAchievementId(List<CvSourceAchievement> source, String text, String ownerDescription) {
        for (CvSourceAchievement achievement : source) {
            if (achievement.text().equals(text)) {
                return achievement.careerAchievementId();
            }
        }
        throw new BaselineCvSelectionResolutionException(
                "Baseline CV selection references an unknown achievement under '" + ownerDescription + "': '" + text + "'");
    }

    /** Generic ordered-id-selection resolver for technologies/highlights, sharing the same three-value convention. */
    private static <T> List<UUID> resolveIds(
            List<T> source, List<String> selection, Function<T, UUID> idExtractor, Function<T, String> valueExtractor, String ownerDescription) {
        if (isSelectAll(selection)) {
            return source.stream().map(idExtractor).toList();
        }
        return selection.stream().map(value -> requireId(source, value, idExtractor, valueExtractor, ownerDescription)).toList();
    }

    private static <T> List<UUID> resolveTechnologyIds(
            List<T> source, List<String> selection, Function<T, UUID> idExtractor, Function<T, String> nameExtractor, String ownerDescription) {
        return resolveIds(source, selection, idExtractor, nameExtractor, ownerDescription);
    }

    private static <T> UUID requireId(
            List<T> source, String value, Function<T, UUID> idExtractor, Function<T, String> valueExtractor, String ownerDescription) {
        for (T item : source) {
            if (valueExtractor.apply(item).equals(value)) {
                return idExtractor.apply(item);
            }
        }
        throw new BaselineCvSelectionResolutionException(
                "Baseline CV selection references an unknown value under '" + ownerDescription + "': '" + value + "'");
    }

    private static boolean isSelectAll(List<String> selection) {
        return selection.size() == 1 && SELECT_ALL.equals(selection.get(0));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
