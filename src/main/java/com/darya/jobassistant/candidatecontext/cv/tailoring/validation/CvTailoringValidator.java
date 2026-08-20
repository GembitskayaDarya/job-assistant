package com.darya.jobassistant.candidatecontext.cv.tailoring.validation;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Sprint 11 Step 3: deterministic, framework-free (no AI call, no HTTP/network call, no persistence
 * write/read, no random choice, no current-time dependency) validation of one {@code
 * CvTailoringResult}'s factual references against the exact {@link CvSourceSnapshot} it was
 * produced from - the {@code CvSourceSnapshot + CvTailoringResult -> CvTailoringValidationResult}
 * stage of the pipeline.
 *
 * <h2>Scope</h2>
 *
 * This validator proves reference integrity and ownership only: every id the tailoring result
 * mentions must exist in the snapshot, and every responsibility/achievement reference must belong
 * to the exact position or project it is claimed under - not merely exist somewhere else in Career
 * History. It never judges whether a rewrite is well-written, relevant to a vacancy, or factually
 * accurate prose (that is out of scope for this step and for this validator entirely - see the
 * class-level warning against heuristic text checks below). {@code CvSourceSnapshot} itself is
 * trusted as already-valid factual data; this validator re-derives no Career History invariant that
 * the snapshot's own construction already guarantees (unique display orders, non-blank text, ...).
 *
 * <h2>Skill validation</h2>
 *
 * Every {@code CvTailoringResult#orderedSkillIds()} entry must match a {@code
 * CandidateSkillFacts#candidateSkillId()} in the snapshot's candidate profile - matched strictly by
 * id, never by falling back to skill name.
 *
 * <h2>Position/project ownership and the hierarchy rule</h2>
 *
 * {@code CvSourceSnapshot} carries responsibilities/achievements at two independent levels: a
 * position's own bullets, and each of its projects' own bullets. A responsibility or achievement id
 * that exists at one level is never treated as valid at the other, even though {@link
 * CvResponsibilityTailoring}/{@link CvAchievementTailoring} use the same UUID-based shape for both -
 * a position-level reference is checked only against that exact position's own {@code
 * responsibilities()}/{@code achievements()}, and a project-level reference only against that exact
 * project's own, never against any other position's or project's bullets and never against the
 * other hierarchy level.
 *
 * <h2>Unknown-parent policy</h2>
 *
 * When a {@link CvPositionTailoring#careerPositionId()} or {@link CvProjectTailoring#careerProjectId()}
 * does not exist in the snapshot at all, this validator reports exactly one {@link
 * CvTailoringViolationCategory#UNKNOWN_POSITION_REFERENCE}/{@link
 * CvTailoringViolationCategory#UNKNOWN_PROJECT_REFERENCE} violation for that entry and does not also
 * validate its responsibility/achievement children - there is no owning position/project to check
 * them against, so reporting them as "not owned by X" would misleadingly imply X exists. This keeps
 * diagnostics unambiguous rather than multiplying noise for one bad reference.
 *
 * <h2>Never attempted here</h2>
 *
 * No keyword overlap, edit distance, technology extraction, or regex-based hallucination detection
 * on {@link CvResponsibilityTailoring#rewrittenText()}/{@link CvAchievementTailoring#rewrittenText()}
 * - those are fragile heuristics that belong to a later, explicitly-designed semantic/prompt
 * boundary, not this structural validator. A rewrite is considered adequately checked once its
 * owning reference passes ownership validation.
 *
 * <h2>Determinism and violation order</h2>
 *
 * Violations are produced by walking {@code CvTailoringResult}'s own already-ordered lists (skills,
 * then positions in their tailoring-result order with each position's responsibilities then
 * achievements in their own order, then projects the same way) - never by iterating a {@code Map}/
 * {@code Set} built for lookup purposes. Equivalent input therefore always yields an equal, equally
 * ordered {@link CvTailoringValidationResult}. Every lookup structure below (skill id set,
 * position-by-id, project-by-id, owned-responsibility/achievement id sets) is built fresh inside
 * {@link #validate} and discarded when it returns - no shared, cached, or persisted state.
 */
public final class CvTailoringValidator {

    private CvTailoringValidator() {
    }

    public static CvTailoringValidationResult validate(CvSourceSnapshot snapshot, CvTailoringResult tailoringResult) {
        if (snapshot == null) {
            throw new IllegalArgumentException("CV source snapshot must not be null");
        }
        if (tailoringResult == null) {
            throw new IllegalArgumentException("CV tailoring result must not be null");
        }

        List<CvTailoringViolation> violations = new ArrayList<>();

        Set<UUID> candidateSkillIds = new HashSet<>();
        for (var skill : snapshot.candidateProfile().skills()) {
            if (skill.candidateSkillId() != null) {
                candidateSkillIds.add(skill.candidateSkillId());
            }
        }
        for (UUID skillId : tailoringResult.orderedSkillIds()) {
            if (!candidateSkillIds.contains(skillId)) {
                violations.add(new CvTailoringViolation(CvTailoringViolationCategory.UNKNOWN_SKILL_REFERENCE, skillId, null));
            }
        }

        Map<UUID, CvSourcePosition> positionsById = new HashMap<>();
        Map<UUID, CvSourceProject> projectsById = new HashMap<>();
        for (CvSourceCompany company : snapshot.companies()) {
            for (CvSourcePosition position : company.positions()) {
                positionsById.put(position.careerPositionId(), position);
                for (CvSourceProject project : position.projects()) {
                    projectsById.put(project.careerProjectId(), project);
                }
            }
        }

        for (CvPositionTailoring positionTailoring : tailoringResult.positionTailoring()) {
            CvSourcePosition position = positionsById.get(positionTailoring.careerPositionId());
            if (position == null) {
                violations.add(new CvTailoringViolation(
                        CvTailoringViolationCategory.UNKNOWN_POSITION_REFERENCE, positionTailoring.careerPositionId(), null));
                continue;
            }
            validateOwnership(
                    positionTailoring.responsibilities(), ids(position.responsibilities(), CvSourceResponsibility::careerResponsibilityId),
                    CvTailoringViolationCategory.POSITION_RESPONSIBILITY_NOT_OWNED, positionTailoring.careerPositionId(), violations);
            validateAchievementOwnership(
                    positionTailoring.achievements(), ids(position.achievements(), CvSourceAchievement::careerAchievementId),
                    CvTailoringViolationCategory.POSITION_ACHIEVEMENT_NOT_OWNED, positionTailoring.careerPositionId(), violations);
        }

        for (CvProjectTailoring projectTailoring : tailoringResult.projectTailoring()) {
            CvSourceProject project = projectsById.get(projectTailoring.careerProjectId());
            if (project == null) {
                violations.add(new CvTailoringViolation(
                        CvTailoringViolationCategory.UNKNOWN_PROJECT_REFERENCE, projectTailoring.careerProjectId(), null));
                continue;
            }
            validateOwnership(
                    projectTailoring.responsibilities(), ids(project.responsibilities(), CvSourceResponsibility::careerResponsibilityId),
                    CvTailoringViolationCategory.PROJECT_RESPONSIBILITY_NOT_OWNED, projectTailoring.careerProjectId(), violations);
            validateAchievementOwnership(
                    projectTailoring.achievements(), ids(project.achievements(), CvSourceAchievement::careerAchievementId),
                    CvTailoringViolationCategory.PROJECT_ACHIEVEMENT_NOT_OWNED, projectTailoring.careerProjectId(), violations);
            validateIdOwnership(
                    projectTailoring.orderedTechnologyIds(), ids(project.technologies(), CvSourceTechnology::careerTechnologyId),
                    CvTailoringViolationCategory.PROJECT_TECHNOLOGY_NOT_OWNED, projectTailoring.careerProjectId(), violations);
        }

        Map<UUID, CvSourcePersonalProject> personalProjectsById = new HashMap<>();
        for (CvSourcePersonalProject personalProject : snapshot.personalProjects()) {
            personalProjectsById.put(personalProject.personalProjectId(), personalProject);
        }

        for (CvPersonalProjectTailoring personalProjectTailoring : tailoringResult.personalProjectTailoring()) {
            CvSourcePersonalProject personalProject = personalProjectsById.get(personalProjectTailoring.personalProjectId());
            if (personalProject == null) {
                violations.add(new CvTailoringViolation(
                        CvTailoringViolationCategory.UNKNOWN_PERSONAL_PROJECT_REFERENCE, personalProjectTailoring.personalProjectId(), null));
                continue;
            }
            validateIdOwnership(
                    personalProjectTailoring.orderedHighlightIds(),
                    ids(personalProject.highlights(), CvSourcePersonalProjectHighlight::personalProjectHighlightId),
                    CvTailoringViolationCategory.PERSONAL_PROJECT_HIGHLIGHT_NOT_OWNED, personalProjectTailoring.personalProjectId(), violations);
            validateIdOwnership(
                    personalProjectTailoring.orderedTechnologyIds(),
                    ids(personalProject.technologies(), CvSourcePersonalProjectTechnology::personalProjectTechnologyId),
                    CvTailoringViolationCategory.PERSONAL_PROJECT_TECHNOLOGY_NOT_OWNED, personalProjectTailoring.personalProjectId(), violations);
        }

        return new CvTailoringValidationResult(violations);
    }

    private static void validateOwnership(
            List<CvResponsibilityTailoring> referenced, Set<UUID> ownedIds, CvTailoringViolationCategory category,
            UUID parentId, List<CvTailoringViolation> violations) {
        for (CvResponsibilityTailoring responsibility : referenced) {
            if (!ownedIds.contains(responsibility.careerResponsibilityId())) {
                violations.add(new CvTailoringViolation(category, responsibility.careerResponsibilityId(), parentId));
            }
        }
    }

    /** Ownership check for a plain {@code List<UUID>} selection (technologies, Personal Project highlights/technologies). */
    private static void validateIdOwnership(
            List<UUID> referenced, Set<UUID> ownedIds, CvTailoringViolationCategory category,
            UUID parentId, List<CvTailoringViolation> violations) {
        for (UUID id : referenced) {
            if (!ownedIds.contains(id)) {
                violations.add(new CvTailoringViolation(category, id, parentId));
            }
        }
    }

    private static void validateAchievementOwnership(
            List<CvAchievementTailoring> referenced, Set<UUID> ownedIds, CvTailoringViolationCategory category,
            UUID parentId, List<CvTailoringViolation> violations) {
        for (CvAchievementTailoring achievement : referenced) {
            if (!ownedIds.contains(achievement.careerAchievementId())) {
                violations.add(new CvTailoringViolation(category, achievement.careerAchievementId(), parentId));
            }
        }
    }

    private static <T> Set<UUID> ids(List<T> items, java.util.function.Function<T, UUID> idExtractor) {
        Set<UUID> result = new HashSet<>();
        for (T item : items) {
            result.add(idExtractor.apply(item));
        }
        return result;
    }
}
