package com.darya.jobassistant.candidatecontext.cv.tailoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CvTailoringResultTest {

    // ==================== 1. Professional summary ====================

    @Test
    void professionalSummary_validContent_isPreserved() {
        CvTailoringResult result = new CvTailoringResult("Tailored summary for this vacancy", List.of(), List.of(), List.of());

        assertThat(result.professionalSummary()).isEqualTo("Tailored summary for this vacancy");
    }

    @Test
    void professionalSummary_null_isAccepted_meansNoTailoredSummary() {
        CvTailoringResult result = new CvTailoringResult(null, List.of(), List.of(), List.of());

        assertThat(result.professionalSummary()).isNull();
    }

    @Test
    void professionalSummary_blank_isRejected() {
        assertThatThrownBy(() -> new CvTailoringResult("   ", List.of(), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== 2/3. Ordered skill selection ====================

    @Test
    void orderedSkillIds_preservesSelectionAndOrder() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();

        CvTailoringResult result = new CvTailoringResult(null, List.of(second, third, first), List.of(), List.of());

        assertThat(result.orderedSkillIds()).containsExactly(second, third, first);
    }

    @Test
    void orderedSkillIds_duplicateSkillReference_isRejected() {
        UUID skillId = UUID.randomUUID();

        assertThatThrownBy(() -> new CvTailoringResult(null, List.of(skillId, skillId), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void orderedSkillIds_nullElement_isRejected() {
        UUID skillId = UUID.randomUUID();
        List<UUID> withNull = new java.util.ArrayList<>();
        withNull.add(skillId);
        withNull.add(null);

        assertThatThrownBy(() -> new CvTailoringResult(null, withNull, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void orderedSkillIds_nullList_becomesEmpty() {
        CvTailoringResult result = new CvTailoringResult(null, null, List.of(), List.of());

        assertThat(result.orderedSkillIds()).isEmpty();
    }

    // ==================== 4. Project tailoring identity ====================

    @Test
    void projectTailoring_preservesCareerProjectId() {
        UUID projectId = UUID.randomUUID();
        CvProjectTailoring tailoring = new CvProjectTailoring(projectId, List.of(), List.of());

        CvTailoringResult result = new CvTailoringResult(null, List.of(), List.of(), List.of(tailoring));

        assertThat(result.projectTailoring()).hasSize(1);
        assertThat(result.projectTailoring().get(0).careerProjectId()).isEqualTo(projectId);
    }

    @Test
    void projectTailoring_nullCareerProjectId_isRejected() {
        assertThatThrownBy(() -> new CvProjectTailoring(null, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== 5/6/7. Project responsibility selection, order, rewrite, duplicates ====================

    @Test
    void projectResponsibilities_preserveSelectionAndOrder() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        CvProjectTailoring tailoring = new CvProjectTailoring(UUID.randomUUID(),
                List.of(new CvResponsibilityTailoring(second, null), new CvResponsibilityTailoring(first, null)), List.of());

        assertThat(tailoring.responsibilities())
                .extracting(CvResponsibilityTailoring::careerResponsibilityId)
                .containsExactly(second, first);
    }

    @Test
    void responsibilityRewrite_remainsBoundToItsSourceResponsibilityId() {
        UUID responsibilityId = UUID.randomUUID();

        CvResponsibilityTailoring rewritten = new CvResponsibilityTailoring(responsibilityId, "Led a cross-team Kafka migration");

        assertThat(rewritten.careerResponsibilityId()).isEqualTo(responsibilityId);
        assertThat(rewritten.rewrittenText()).isEqualTo("Led a cross-team Kafka migration");
    }

    @Test
    void responsibility_nullCareerResponsibilityId_isRejected() {
        assertThatThrownBy(() -> new CvResponsibilityTailoring(null, "text"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void responsibility_blankRewrite_isRejected() {
        assertThatThrownBy(() -> new CvResponsibilityTailoring(UUID.randomUUID(), "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void responsibility_nullRewrite_isAccepted_meansShowOriginalText() {
        CvResponsibilityTailoring notRewritten = new CvResponsibilityTailoring(UUID.randomUUID(), null);

        assertThat(notRewritten.rewrittenText()).isNull();
    }

    @Test
    void projectResponsibilities_duplicateReference_isRejected() {
        UUID responsibilityId = UUID.randomUUID();

        assertThatThrownBy(() -> new CvProjectTailoring(UUID.randomUUID(),
                List.of(new CvResponsibilityTailoring(responsibilityId, null), new CvResponsibilityTailoring(responsibilityId, "rewrite")),
                List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== 8/9/10. Project achievement selection, order, rewrite, duplicates ====================

    @Test
    void projectAchievements_preserveSelectionAndOrder() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        CvProjectTailoring tailoring = new CvProjectTailoring(UUID.randomUUID(), List.of(),
                List.of(new CvAchievementTailoring(second, null), new CvAchievementTailoring(first, null)));

        assertThat(tailoring.achievements())
                .extracting(CvAchievementTailoring::careerAchievementId)
                .containsExactly(second, first);
    }

    @Test
    void achievementRewrite_remainsBoundToItsSourceAchievementId() {
        UUID achievementId = UUID.randomUUID();

        CvAchievementTailoring rewritten = new CvAchievementTailoring(achievementId, "Cut infrastructure costs by 30%");

        assertThat(rewritten.careerAchievementId()).isEqualTo(achievementId);
        assertThat(rewritten.rewrittenText()).isEqualTo("Cut infrastructure costs by 30%");
    }

    @Test
    void achievement_nullCareerAchievementId_isRejected() {
        assertThatThrownBy(() -> new CvAchievementTailoring(null, "text"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void achievement_blankRewrite_isRejected() {
        assertThatThrownBy(() -> new CvAchievementTailoring(UUID.randomUUID(), "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void projectAchievements_duplicateReference_isRejected() {
        UUID achievementId = UUID.randomUUID();

        assertThatThrownBy(() -> new CvProjectTailoring(UUID.randomUUID(), List.of(),
                List.of(new CvAchievementTailoring(achievementId, null), new CvAchievementTailoring(achievementId, "rewrite"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== 11. Duplicate project tailoring entries ====================

    @Test
    void projectTailoring_duplicateProjectId_isRejected() {
        UUID projectId = UUID.randomUUID();

        assertThatThrownBy(() -> new CvTailoringResult(null, List.of(), List.of(), List.of(
                new CvProjectTailoring(projectId, List.of(), List.of()),
                new CvProjectTailoring(projectId, List.of(), List.of()))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== Position tailoring: identity, order, rewrite, duplicates (Step 2 correction) ====================

    @Test
    void positionTailoring_preservesCareerPositionId() {
        UUID positionId = UUID.randomUUID();
        CvPositionTailoring tailoring = new CvPositionTailoring(positionId, List.of(), List.of());

        CvTailoringResult result = new CvTailoringResult(null, List.of(), List.of(tailoring), List.of());

        assertThat(result.positionTailoring()).hasSize(1);
        assertThat(result.positionTailoring().get(0).careerPositionId()).isEqualTo(positionId);
    }

    @Test
    void positionTailoring_nullCareerPositionId_isRejected() {
        assertThatThrownBy(() -> new CvPositionTailoring(null, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void positionResponsibilities_preserveSelectionAndOrder() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        CvPositionTailoring tailoring = new CvPositionTailoring(UUID.randomUUID(),
                List.of(new CvResponsibilityTailoring(second, null), new CvResponsibilityTailoring(first, null)), List.of());

        assertThat(tailoring.responsibilities())
                .extracting(CvResponsibilityTailoring::careerResponsibilityId)
                .containsExactly(second, first);
    }

    @Test
    void positionResponsibilityRewrite_remainsBoundToItsSourceResponsibilityId() {
        UUID responsibilityId = UUID.randomUUID();
        CvPositionTailoring tailoring = new CvPositionTailoring(UUID.randomUUID(),
                List.of(new CvResponsibilityTailoring(responsibilityId, "Owned the on-call rotation")), List.of());

        assertThat(tailoring.responsibilities().get(0).careerResponsibilityId()).isEqualTo(responsibilityId);
        assertThat(tailoring.responsibilities().get(0).rewrittenText()).isEqualTo("Owned the on-call rotation");
    }

    @Test
    void positionResponsibilities_duplicateReference_isRejected() {
        UUID responsibilityId = UUID.randomUUID();

        assertThatThrownBy(() -> new CvPositionTailoring(UUID.randomUUID(),
                List.of(new CvResponsibilityTailoring(responsibilityId, null), new CvResponsibilityTailoring(responsibilityId, "rewrite")),
                List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void positionAchievements_preserveSelectionAndOrder() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        CvPositionTailoring tailoring = new CvPositionTailoring(UUID.randomUUID(), List.of(),
                List.of(new CvAchievementTailoring(second, null), new CvAchievementTailoring(first, null)));

        assertThat(tailoring.achievements())
                .extracting(CvAchievementTailoring::careerAchievementId)
                .containsExactly(second, first);
    }

    @Test
    void positionAchievementRewrite_remainsBoundToItsSourceAchievementId() {
        UUID achievementId = UUID.randomUUID();
        CvPositionTailoring tailoring = new CvPositionTailoring(UUID.randomUUID(), List.of(),
                List.of(new CvAchievementTailoring(achievementId, "Promoted twice in two years")));

        assertThat(tailoring.achievements().get(0).careerAchievementId()).isEqualTo(achievementId);
        assertThat(tailoring.achievements().get(0).rewrittenText()).isEqualTo("Promoted twice in two years");
    }

    @Test
    void positionAchievements_duplicateReference_isRejected() {
        UUID achievementId = UUID.randomUUID();

        assertThatThrownBy(() -> new CvPositionTailoring(UUID.randomUUID(), List.of(),
                List.of(new CvAchievementTailoring(achievementId, null), new CvAchievementTailoring(achievementId, "rewrite"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void positionTailoring_duplicateCareerPositionId_isRejected() {
        UUID positionId = UUID.randomUUID();

        assertThatThrownBy(() -> new CvTailoringResult(null, List.of(), List.of(
                new CvPositionTailoring(positionId, List.of(), List.of()),
                new CvPositionTailoring(positionId, List.of(), List.of())), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void positionTailoring_nullChildLists_becomeEmpty() {
        CvPositionTailoring tailoring = new CvPositionTailoring(UUID.randomUUID(), null, null);

        assertThat(tailoring.responsibilities()).isEmpty();
        assertThat(tailoring.achievements()).isEmpty();
    }

    @Test
    void tailoringResult_nullPositionTailoringList_becomesEmpty() {
        CvTailoringResult result = new CvTailoringResult(null, List.of(), null, List.of());

        assertThat(result.positionTailoring()).isEmpty();
    }

    /** Position and project tailoring reference disjoint id spaces - the same id may legitimately be a coincidence, never rejected here. */
    @Test
    void positionAndProjectTailoring_areIndependent_bothMayBePresentTogether() {
        UUID positionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        CvTailoringResult result = new CvTailoringResult(null, List.of(),
                List.of(new CvPositionTailoring(positionId, List.of(), List.of())),
                List.of(new CvProjectTailoring(projectId, List.of(), List.of())));

        assertThat(result.positionTailoring()).hasSize(1);
        assertThat(result.projectTailoring()).hasSize(1);
    }

    // ==================== 12. Other null/blank rejections ====================

    @Test
    void projectTailoring_nullChildLists_becomeEmpty() {
        CvProjectTailoring tailoring = new CvProjectTailoring(UUID.randomUUID(), null, null);

        assertThat(tailoring.responsibilities()).isEmpty();
        assertThat(tailoring.achievements()).isEmpty();
    }

    @Test
    void tailoringResult_nullProjectTailoringList_becomesEmpty() {
        CvTailoringResult result = new CvTailoringResult(null, List.of(), List.of(), null);

        assertThat(result.projectTailoring()).isEmpty();
    }

    // ==================== 15/16. Project technology tailoring (Step 6) ====================

    @Test
    void projectOrderedTechnologyIds_preservesSelectionAndOrder() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        CvProjectTailoring tailoring = new CvProjectTailoring(UUID.randomUUID(), List.of(), List.of(), List.of(second, first));

        assertThat(tailoring.orderedTechnologyIds()).containsExactly(second, first);
    }

    @Test
    void projectOrderedTechnologyIds_duplicateReference_isRejected() {
        UUID technologyId = UUID.randomUUID();

        assertThatThrownBy(() -> new CvProjectTailoring(UUID.randomUUID(), List.of(), List.of(), List.of(technologyId, technologyId)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void projectOrderedTechnologyIds_nullElement_isRejected() {
        List<UUID> withNull = new java.util.ArrayList<>();
        withNull.add(UUID.randomUUID());
        withNull.add(null);

        assertThatThrownBy(() -> new CvProjectTailoring(UUID.randomUUID(), List.of(), List.of(), withNull))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void projectOrderedTechnologyIds_nullList_becomesEmpty() {
        CvProjectTailoring tailoring = new CvProjectTailoring(UUID.randomUUID(), List.of(), List.of(), null);

        assertThat(tailoring.orderedTechnologyIds()).isEmpty();
    }

    @Test
    void projectTailoring_oldArityConstructor_defaultsOrderedTechnologyIdsToEmpty() {
        CvProjectTailoring tailoring = new CvProjectTailoring(UUID.randomUUID(), List.of(), List.of());

        assertThat(tailoring.orderedTechnologyIds()).isEmpty();
    }

    // ==================== 17-20. Personal project tailoring (Step 6) ====================

    @Test
    void personalProjectTailoring_preservesPersonalProjectId() {
        UUID personalProjectId = UUID.randomUUID();
        CvPersonalProjectTailoring tailoring = new CvPersonalProjectTailoring(personalProjectId, List.of(), List.of());

        CvTailoringResult result = new CvTailoringResult(null, List.of(), List.of(), List.of(), List.of(tailoring));

        assertThat(result.personalProjectTailoring()).hasSize(1);
        assertThat(result.personalProjectTailoring().get(0).personalProjectId()).isEqualTo(personalProjectId);
    }

    @Test
    void personalProjectTailoring_nullPersonalProjectId_isRejected() {
        assertThatThrownBy(() -> new CvPersonalProjectTailoring(null, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void personalProjectHighlightIds_preserveSelectionAndOrder() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        CvPersonalProjectTailoring tailoring = new CvPersonalProjectTailoring(UUID.randomUUID(), List.of(second, first), List.of());

        assertThat(tailoring.orderedHighlightIds()).containsExactly(second, first);
    }

    @Test
    void personalProjectHighlightIds_duplicateReference_isRejected() {
        UUID highlightId = UUID.randomUUID();

        assertThatThrownBy(() -> new CvPersonalProjectTailoring(UUID.randomUUID(), List.of(highlightId, highlightId), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void personalProjectTechnologyIds_preserveSelectionAndOrder() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        CvPersonalProjectTailoring tailoring = new CvPersonalProjectTailoring(UUID.randomUUID(), List.of(), List.of(second, first));

        assertThat(tailoring.orderedTechnologyIds()).containsExactly(second, first);
    }

    @Test
    void personalProjectTechnologyIds_duplicateReference_isRejected() {
        UUID technologyId = UUID.randomUUID();

        assertThatThrownBy(() -> new CvPersonalProjectTailoring(UUID.randomUUID(), List.of(), List.of(technologyId, technologyId)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void personalProjectTailoring_nullChildLists_becomeEmpty() {
        CvPersonalProjectTailoring tailoring = new CvPersonalProjectTailoring(UUID.randomUUID(), null, null);

        assertThat(tailoring.orderedHighlightIds()).isEmpty();
        assertThat(tailoring.orderedTechnologyIds()).isEmpty();
    }

    @Test
    void personalProjectTailoring_duplicatePersonalProjectId_isRejected() {
        UUID personalProjectId = UUID.randomUUID();

        assertThatThrownBy(() -> new CvTailoringResult(null, List.of(), List.of(), List.of(), List.of(
                new CvPersonalProjectTailoring(personalProjectId, List.of(), List.of()),
                new CvPersonalProjectTailoring(personalProjectId, List.of(), List.of()))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tailoringResult_nullPersonalProjectTailoringList_becomesEmpty() {
        CvTailoringResult result = new CvTailoringResult(null, List.of(), List.of(), List.of(), null);

        assertThat(result.personalProjectTailoring()).isEmpty();
    }

    @Test
    void tailoringResult_oldArityConstructor_defaultsPersonalProjectTailoringToEmpty() {
        CvTailoringResult result = new CvTailoringResult(null, List.of(), List.of(), List.of());

        assertThat(result.personalProjectTailoring()).isEmpty();
    }

    // ==================== 13. Immutability ====================

    @Test
    void collections_areUnmodifiable() {
        CvProjectTailoring projectTailoring = new CvProjectTailoring(UUID.randomUUID(),
                List.of(new CvResponsibilityTailoring(UUID.randomUUID(), null)),
                List.of(new CvAchievementTailoring(UUID.randomUUID(), null)),
                List.of(UUID.randomUUID()));
        CvPositionTailoring positionTailoring = new CvPositionTailoring(UUID.randomUUID(),
                List.of(new CvResponsibilityTailoring(UUID.randomUUID(), null)),
                List.of(new CvAchievementTailoring(UUID.randomUUID(), null)));
        CvPersonalProjectTailoring personalProjectTailoring = new CvPersonalProjectTailoring(
                UUID.randomUUID(), List.of(UUID.randomUUID()), List.of(UUID.randomUUID()));
        CvTailoringResult result = new CvTailoringResult(
                null, List.of(UUID.randomUUID()), List.of(positionTailoring), List.of(projectTailoring), List.of(personalProjectTailoring));

        assertThatThrownBy(() -> result.orderedSkillIds().add(UUID.randomUUID()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.positionTailoring().add(positionTailoring))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.projectTailoring().add(projectTailoring))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.personalProjectTailoring().add(personalProjectTailoring))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> projectTailoring.responsibilities().add(projectTailoring.responsibilities().get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> projectTailoring.achievements().add(projectTailoring.achievements().get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> projectTailoring.orderedTechnologyIds().add(UUID.randomUUID()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> positionTailoring.responsibilities().add(positionTailoring.responsibilities().get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> positionTailoring.achievements().add(positionTailoring.achievements().get(0)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> personalProjectTailoring.orderedHighlightIds().add(UUID.randomUUID()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> personalProjectTailoring.orderedTechnologyIds().add(UUID.randomUUID()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ==================== 14. Deterministic value equality ====================

    @Test
    void equivalentValues_areEqual() {
        UUID skillId = UUID.randomUUID();
        UUID positionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID responsibilityId = UUID.randomUUID();
        UUID achievementId = UUID.randomUUID();
        UUID technologyId = UUID.randomUUID();
        UUID personalProjectId = UUID.randomUUID();
        UUID highlightId = UUID.randomUUID();

        CvTailoringResult resultA = new CvTailoringResult("Summary", List.of(skillId),
                List.of(new CvPositionTailoring(positionId, List.of(new CvResponsibilityTailoring(responsibilityId, "rewrite")),
                        List.of(new CvAchievementTailoring(achievementId, null)))),
                List.of(new CvProjectTailoring(projectId, List.of(new CvResponsibilityTailoring(responsibilityId, "rewrite")),
                        List.of(new CvAchievementTailoring(achievementId, null)), List.of(technologyId))),
                List.of(new CvPersonalProjectTailoring(personalProjectId, List.of(highlightId), List.of(technologyId))));
        CvTailoringResult resultB = new CvTailoringResult("Summary", List.of(skillId),
                List.of(new CvPositionTailoring(positionId, List.of(new CvResponsibilityTailoring(responsibilityId, "rewrite")),
                        List.of(new CvAchievementTailoring(achievementId, null)))),
                List.of(new CvProjectTailoring(projectId, List.of(new CvResponsibilityTailoring(responsibilityId, "rewrite")),
                        List.of(new CvAchievementTailoring(achievementId, null)), List.of(technologyId))),
                List.of(new CvPersonalProjectTailoring(personalProjectId, List.of(highlightId), List.of(technologyId))));

        assertThat(resultA).isEqualTo(resultB);
        assertThat(resultA.hashCode()).isEqualTo(resultB.hashCode());
    }
}
