package com.darya.jobassistant.candidatecontext.cv.tailoring;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Sprint 11 Step 2 correction: shared, framework-free "no duplicate id" checks used identically by
 * both {@link CvPositionTailoring} and {@link CvProjectTailoring} - position-level and project-level
 * bullet tailoring enforce exactly the same intrinsic invariant on the same two reference types
 * ({@link CvResponsibilityTailoring}/{@link CvAchievementTailoring}), so this is extracted once
 * rather than duplicated per owner, mirroring {@code careerhistory.aggregate.CareerHistoryValidation}'s
 * package-private-helper convention.
 */
final class CvTailoringValidation {

    private CvTailoringValidation() {
    }

    static void requireNoDuplicateResponsibilityIds(List<CvResponsibilityTailoring> responsibilities, String ownerDescription) {
        Set<UUID> seen = new HashSet<>();
        for (CvResponsibilityTailoring responsibility : responsibilities) {
            if (!seen.add(responsibility.careerResponsibilityId())) {
                throw new IllegalArgumentException(
                        "Duplicate responsibility reference in " + ownerDescription + ": " + responsibility.careerResponsibilityId());
            }
        }
    }

    static void requireNoDuplicateAchievementIds(List<CvAchievementTailoring> achievements, String ownerDescription) {
        Set<UUID> seen = new HashSet<>();
        for (CvAchievementTailoring achievement : achievements) {
            if (!seen.add(achievement.careerAchievementId())) {
                throw new IllegalArgumentException(
                        "Duplicate achievement reference in " + ownerDescription + ": " + achievement.careerAchievementId());
            }
        }
    }

    /**
     * Sprint 11 Step 6: shared "no null, no duplicate" check for a plain ordered id-selection list -
     * {@link CvProjectTailoring#orderedTechnologyIds()} and {@link
     * CvPersonalProjectTailoring}'s {@code orderedHighlightIds}/{@code orderedTechnologyIds} all use
     * exactly this shape (unlike responsibilities/achievements, a plain {@code List<UUID>} carries no
     * rewrite alongside the id), mirroring {@link CvTailoringResult#orderedSkillIds()}'s own inline
     * check.
     */
    static void requireNoNullOrDuplicateIds(List<UUID> ids, String fieldDescription) {
        Set<UUID> seen = new HashSet<>();
        for (UUID id : ids) {
            if (id == null) {
                throw new IllegalArgumentException(fieldDescription + " must not contain a null id");
            }
            if (!seen.add(id)) {
                throw new IllegalArgumentException("Duplicate reference in " + fieldDescription + ": " + id);
            }
        }
    }
}
