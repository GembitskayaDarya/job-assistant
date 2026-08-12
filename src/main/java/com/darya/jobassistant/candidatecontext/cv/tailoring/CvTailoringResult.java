package com.darya.jobassistant.candidatecontext.cv.tailoring;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Sprint 11 Step 2: the complete, immutable, framework-free set of tailoring decisions a future AI
 * is allowed to request for one vacancy - never a complete generated CV, and never the owner of any
 * factual career structure. Every reference here points back to a stable identity already present
 * in the {@code CvSourceSnapshot} the request was built from ({@code candidateSkillId}, {@code
 * careerPositionId}, {@code careerProjectId}, {@code careerResponsibilityId}, {@code
 * careerAchievementId}); nothing in this type can express a company name, position, project name,
 * date, or piece of career structure the AI invented.
 *
 * <p>These ids are references into that specific snapshot, not permanent external identifiers - a
 * future tailoring result must be validated and assembled against the same factual snapshot the AI
 * request was built from (Sprint 11 Step 3), since {@code CandidateSkillFacts#candidateSkillId()}
 * and every Career History id are only stable for the lifetime of one loaded snapshot, not durable
 * across a subsequent Candidate Profile/Career History save (both still use full replace-on-save
 * persistence - unchanged by this correction).
 *
 * <p>Conceptually the next stage after {@code CvSourceSnapshot}:
 *
 * <pre>{@code
 * CvSourceSnapshot -> future AI -> CvTailoringResult
 * }</pre>
 *
 * A later step combines this with the snapshot it was produced from
 * ({@code CvSourceSnapshot + CvTailoringResult -> validation/guardrails -> TailoredCvDocument})
 * - not implemented yet. This step deliberately does NOT check whether any id referenced here
 * actually exists in a particular snapshot, or belongs to the position/project it is claimed under;
 * only structural invariants knowable from this result alone are enforced.
 *
 * <h2>What the AI does NOT control through this contract</h2>
 *
 * Candidate name, contact information, company names, company/position ordering, position titles,
 * employment dates, project names/existence, education facts, language facts/proficiency, project
 * technologies, and skill proficiency all remain application-owned factual data - none of them has
 * a field here.
 *
 * <h2>Fields</h2>
 *
 * <p>{@link #professionalSummary} is optional free-form content only - no HTML/Markdown formatting
 * concerns belong in this domain contract (that is a future renderer's job). {@code null} means no
 * tailored summary was produced; a non-null value must not be blank.
 *
 * <p>{@link #orderedSkillIds} references {@code CandidateSkillFacts#candidateSkillId()} - list
 * position expresses both which factual skills are shown and in what order, so no separate
 * selection flag or ordering field is needed alongside it. The AI can never invent a free-form
 * skill name this way.
 *
 * <p>{@link #positionTailoring} holds at most one {@link CvPositionTailoring} per {@code
 * careerPositionId} - zero or more entries, one per position's own (non-project) bullets the AI
 * chooses to tailor. {@link #projectTailoring} holds at most one {@link CvProjectTailoring} per
 * {@code careerProjectId} - zero or more entries, one per project the AI chooses to tailor. The two
 * lists are independent id spaces (positions and projects are different source rows) and are
 * intentionally never cross-checked against each other in this structural contract.
 */
public record CvTailoringResult(
        String professionalSummary,
        List<UUID> orderedSkillIds,
        List<CvPositionTailoring> positionTailoring,
        List<CvProjectTailoring> projectTailoring
) {

    public CvTailoringResult {
        if (professionalSummary != null && professionalSummary.isBlank()) {
            throw new IllegalArgumentException("Tailoring result professionalSummary must not be blank when present");
        }
        // Validated on the raw, caller-supplied list before defensively copying: List.copyOf itself
        // throws a bare NullPointerException on a null element, which would bypass the intended
        // IllegalArgumentException contract for "invalid tailoring input" below.
        requireNoNullOrDuplicateSkillIds(orderedSkillIds == null ? List.of() : orderedSkillIds);
        orderedSkillIds = orderedSkillIds == null ? List.of() : List.copyOf(orderedSkillIds);
        positionTailoring = positionTailoring == null ? List.of() : List.copyOf(positionTailoring);
        requireNoDuplicatePositionIds(positionTailoring);
        projectTailoring = projectTailoring == null ? List.of() : List.copyOf(projectTailoring);
        requireNoDuplicateProjectIds(projectTailoring);
    }

    private static void requireNoNullOrDuplicateSkillIds(List<UUID> skillIds) {
        Set<UUID> seen = new HashSet<>();
        for (UUID skillId : skillIds) {
            if (skillId == null) {
                throw new IllegalArgumentException("Tailoring result orderedSkillIds must not contain a null id");
            }
            if (!seen.add(skillId)) {
                throw new IllegalArgumentException("Duplicate skill reference in tailoring result: " + skillId);
            }
        }
    }

    private static void requireNoDuplicatePositionIds(List<CvPositionTailoring> positionTailoring) {
        Set<UUID> seen = new HashSet<>();
        for (CvPositionTailoring tailoring : positionTailoring) {
            if (!seen.add(tailoring.careerPositionId())) {
                throw new IllegalArgumentException(
                        "Duplicate position tailoring entry for position: " + tailoring.careerPositionId());
            }
        }
    }

    private static void requireNoDuplicateProjectIds(List<CvProjectTailoring> projectTailoring) {
        Set<UUID> seen = new HashSet<>();
        for (CvProjectTailoring tailoring : projectTailoring) {
            if (!seen.add(tailoring.careerProjectId())) {
                throw new IllegalArgumentException(
                        "Duplicate project tailoring entry for project: " + tailoring.careerProjectId());
            }
        }
    }
}
