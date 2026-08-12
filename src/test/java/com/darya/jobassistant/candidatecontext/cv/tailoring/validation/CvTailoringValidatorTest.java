package com.darya.jobassistant.candidatecontext.cv.tailoring.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.candidatecontext.CareerHistoryAvailability;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceAchievement;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceCompany;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourcePosition;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceProject;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceResponsibility;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceSnapshot;
import com.darya.jobassistant.candidatecontext.cv.tailoring.CvAchievementTailoring;
import com.darya.jobassistant.candidatecontext.cv.tailoring.CvPositionTailoring;
import com.darya.jobassistant.candidatecontext.cv.tailoring.CvProjectTailoring;
import com.darya.jobassistant.candidatecontext.cv.tailoring.CvResponsibilityTailoring;
import com.darya.jobassistant.candidatecontext.cv.tailoring.CvTailoringResult;
import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfileFacts;
import com.darya.jobassistant.candidates.CandidateSkillFacts;
import com.darya.jobassistant.candidates.SkillProficiency;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Uses one shared, realistic {@link #snapshot()} fixture throughout - two positions (each with its
 * own position-level responsibility/achievement) and two nested projects, one per position (each
 * with its own project-level responsibility/achievement) - so ownership-mismatch tests exercise a
 * real cross-position/cross-project/cross-level mixup, not a trivial single-node graph that could
 * accidentally pass.
 */
class CvTailoringValidatorTest {

    private static final UUID SKILL_JAVA = UUID.randomUUID();
    private static final UUID SKILL_KAFKA = UUID.randomUUID();

    private static final UUID POSITION_A = UUID.randomUUID();
    private static final UUID POSITION_A_RESPONSIBILITY = UUID.randomUUID();
    private static final UUID POSITION_A_ACHIEVEMENT = UUID.randomUUID();
    private static final UUID PROJECT_P1 = UUID.randomUUID();
    private static final UUID PROJECT_P1_RESPONSIBILITY = UUID.randomUUID();
    private static final UUID PROJECT_P1_ACHIEVEMENT = UUID.randomUUID();

    private static final UUID POSITION_B = UUID.randomUUID();
    private static final UUID POSITION_B_RESPONSIBILITY = UUID.randomUUID();
    private static final UUID POSITION_B_ACHIEVEMENT = UUID.randomUUID();
    private static final UUID PROJECT_P2 = UUID.randomUUID();
    private static final UUID PROJECT_P2_RESPONSIBILITY = UUID.randomUUID();
    private static final UUID PROJECT_P2_ACHIEVEMENT = UUID.randomUUID();

    // ==================== 1. Minimal/empty result ====================

    @Test
    void validate_minimalTailoringResult_isValid() {
        CvTailoringValidationResult result = CvTailoringValidator.validate(snapshot(), new CvTailoringResult(null, List.of(), List.of(), List.of()));

        assertThat(result.valid()).isTrue();
        assertThat(result.violations()).isEmpty();
    }

    // ==================== 2/3. Skills ====================

    @Test
    void validate_knownSkillReferences_pass() {
        CvTailoringResult tailoring = new CvTailoringResult(null, List.of(SKILL_JAVA, SKILL_KAFKA), List.of(), List.of());

        CvTailoringValidationResult result = CvTailoringValidator.validate(snapshot(), tailoring);

        assertThat(result.valid()).isTrue();
    }

    @Test
    void validate_unknownSkillReference_producesViolation() {
        UUID unknownSkill = UUID.randomUUID();
        CvTailoringResult tailoring = new CvTailoringResult(null, List.of(unknownSkill), List.of(), List.of());

        CvTailoringValidationResult result = CvTailoringValidator.validate(snapshot(), tailoring);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).containsExactly(
                new CvTailoringViolation(CvTailoringViolationCategory.UNKNOWN_SKILL_REFERENCE, unknownSkill, null));
    }

    @Test
    void validate_unknownSkillReference_neverFallsBackToNameMatching() {
        // "Java" is a real skill name in the fixture, but this id is not the real Java skill's id -
        // must still be reported, proving there is no name-based fallback.
        UUID skillIdThatIsNotJavasRealId = UUID.randomUUID();
        CvTailoringResult tailoring = new CvTailoringResult(null, List.of(skillIdThatIsNotJavasRealId), List.of(), List.of());

        CvTailoringValidationResult result = CvTailoringValidator.validate(snapshot(), tailoring);

        assertThat(result.valid()).isFalse();
    }

    // ==================== 4/5/6. Position responsibility ownership ====================

    @Test
    void validate_positionResponsibility_ownedByThatPosition_passes() {
        CvTailoringResult tailoring = positionTailoringResult(POSITION_A, List.of(POSITION_A_RESPONSIBILITY), List.of());

        assertThat(CvTailoringValidator.validate(snapshot(), tailoring).valid()).isTrue();
    }

    @Test
    void validate_positionResponsibility_belongingToAnotherPosition_fails() {
        CvTailoringResult tailoring = positionTailoringResult(POSITION_A, List.of(POSITION_B_RESPONSIBILITY), List.of());

        CvTailoringValidationResult result = CvTailoringValidator.validate(snapshot(), tailoring);

        assertThat(result.violations()).containsExactly(new CvTailoringViolation(
                CvTailoringViolationCategory.POSITION_RESPONSIBILITY_NOT_OWNED, POSITION_B_RESPONSIBILITY, POSITION_A));
    }

    @Test
    void validate_projectLevelResponsibility_referencedAsPositionLevel_fails() {
        CvTailoringResult tailoring = positionTailoringResult(POSITION_A, List.of(PROJECT_P1_RESPONSIBILITY), List.of());

        CvTailoringValidationResult result = CvTailoringValidator.validate(snapshot(), tailoring);

        assertThat(result.violations()).containsExactly(new CvTailoringViolation(
                CvTailoringViolationCategory.POSITION_RESPONSIBILITY_NOT_OWNED, PROJECT_P1_RESPONSIBILITY, POSITION_A));
    }

    // ==================== 7/8/9. Position achievement ownership ====================

    @Test
    void validate_positionAchievement_ownedByThatPosition_passes() {
        CvTailoringResult tailoring = positionTailoringResult(POSITION_A, List.of(), List.of(POSITION_A_ACHIEVEMENT));

        assertThat(CvTailoringValidator.validate(snapshot(), tailoring).valid()).isTrue();
    }

    @Test
    void validate_positionAchievement_wrongPosition_fails() {
        CvTailoringResult tailoring = positionTailoringResult(POSITION_A, List.of(), List.of(POSITION_B_ACHIEVEMENT));

        CvTailoringValidationResult result = CvTailoringValidator.validate(snapshot(), tailoring);

        assertThat(result.violations()).containsExactly(new CvTailoringViolation(
                CvTailoringViolationCategory.POSITION_ACHIEVEMENT_NOT_OWNED, POSITION_B_ACHIEVEMENT, POSITION_A));
    }

    @Test
    void validate_projectLevelAchievement_referencedAsPositionLevel_fails() {
        CvTailoringResult tailoring = positionTailoringResult(POSITION_A, List.of(), List.of(PROJECT_P1_ACHIEVEMENT));

        CvTailoringValidationResult result = CvTailoringValidator.validate(snapshot(), tailoring);

        assertThat(result.violations()).containsExactly(new CvTailoringViolation(
                CvTailoringViolationCategory.POSITION_ACHIEVEMENT_NOT_OWNED, PROJECT_P1_ACHIEVEMENT, POSITION_A));
    }

    // ==================== 10/11/12. Project responsibility ownership ====================

    @Test
    void validate_projectResponsibility_ownedByThatProject_passes() {
        CvTailoringResult tailoring = projectTailoringResult(PROJECT_P1, List.of(PROJECT_P1_RESPONSIBILITY), List.of());

        assertThat(CvTailoringValidator.validate(snapshot(), tailoring).valid()).isTrue();
    }

    @Test
    void validate_projectResponsibility_belongingToAnotherProject_fails() {
        CvTailoringResult tailoring = projectTailoringResult(PROJECT_P1, List.of(PROJECT_P2_RESPONSIBILITY), List.of());

        CvTailoringValidationResult result = CvTailoringValidator.validate(snapshot(), tailoring);

        assertThat(result.violations()).containsExactly(new CvTailoringViolation(
                CvTailoringViolationCategory.PROJECT_RESPONSIBILITY_NOT_OWNED, PROJECT_P2_RESPONSIBILITY, PROJECT_P1));
    }

    @Test
    void validate_positionLevelResponsibility_referencedAsProjectLevel_fails() {
        CvTailoringResult tailoring = projectTailoringResult(PROJECT_P1, List.of(POSITION_A_RESPONSIBILITY), List.of());

        CvTailoringValidationResult result = CvTailoringValidator.validate(snapshot(), tailoring);

        assertThat(result.violations()).containsExactly(new CvTailoringViolation(
                CvTailoringViolationCategory.PROJECT_RESPONSIBILITY_NOT_OWNED, POSITION_A_RESPONSIBILITY, PROJECT_P1));
    }

    // ==================== 13/14/15. Project achievement ownership ====================

    @Test
    void validate_projectAchievement_ownedByThatProject_passes() {
        CvTailoringResult tailoring = projectTailoringResult(PROJECT_P1, List.of(), List.of(PROJECT_P1_ACHIEVEMENT));

        assertThat(CvTailoringValidator.validate(snapshot(), tailoring).valid()).isTrue();
    }

    @Test
    void validate_projectAchievement_wrongProject_fails() {
        CvTailoringResult tailoring = projectTailoringResult(PROJECT_P1, List.of(), List.of(PROJECT_P2_ACHIEVEMENT));

        CvTailoringValidationResult result = CvTailoringValidator.validate(snapshot(), tailoring);

        assertThat(result.violations()).containsExactly(new CvTailoringViolation(
                CvTailoringViolationCategory.PROJECT_ACHIEVEMENT_NOT_OWNED, PROJECT_P2_ACHIEVEMENT, PROJECT_P1));
    }

    @Test
    void validate_positionLevelAchievement_referencedAsProjectLevel_fails() {
        CvTailoringResult tailoring = projectTailoringResult(PROJECT_P1, List.of(), List.of(POSITION_A_ACHIEVEMENT));

        CvTailoringValidationResult result = CvTailoringValidator.validate(snapshot(), tailoring);

        assertThat(result.violations()).containsExactly(new CvTailoringViolation(
                CvTailoringViolationCategory.PROJECT_ACHIEVEMENT_NOT_OWNED, POSITION_A_ACHIEVEMENT, PROJECT_P1));
    }

    // ==================== 16/17. Unknown position/project + unknown-parent policy ====================

    @Test
    void validate_unknownPositionReference_isReported() {
        UUID unknownPosition = UUID.randomUUID();
        CvTailoringResult tailoring = positionTailoringResult(unknownPosition, List.of(), List.of());

        CvTailoringValidationResult result = CvTailoringValidator.validate(snapshot(), tailoring);

        assertThat(result.violations()).containsExactly(
                new CvTailoringViolation(CvTailoringViolationCategory.UNKNOWN_POSITION_REFERENCE, unknownPosition, null));
    }

    @Test
    void validate_unknownProjectReference_isReported() {
        UUID unknownProject = UUID.randomUUID();
        CvTailoringResult tailoring = projectTailoringResult(unknownProject, List.of(), List.of());

        CvTailoringValidationResult result = CvTailoringValidator.validate(snapshot(), tailoring);

        assertThat(result.violations()).containsExactly(
                new CvTailoringViolation(CvTailoringViolationCategory.UNKNOWN_PROJECT_REFERENCE, unknownProject, null));
    }

    /** Unknown-parent policy: exactly one violation for the parent, no misleading ownership errors for its would-be children. */
    @Test
    void validate_unknownPositionWithChildren_reportsOnlyTheParentViolation() {
        UUID unknownPosition = UUID.randomUUID();
        CvTailoringResult tailoring = positionTailoringResult(
                unknownPosition, List.of(POSITION_A_RESPONSIBILITY), List.of(POSITION_A_ACHIEVEMENT));

        CvTailoringValidationResult result = CvTailoringValidator.validate(snapshot(), tailoring);

        assertThat(result.violations()).containsExactly(
                new CvTailoringViolation(CvTailoringViolationCategory.UNKNOWN_POSITION_REFERENCE, unknownPosition, null));
    }

    @Test
    void validate_unknownProjectWithChildren_reportsOnlyTheParentViolation() {
        UUID unknownProject = UUID.randomUUID();
        CvTailoringResult tailoring = projectTailoringResult(
                unknownProject, List.of(PROJECT_P1_RESPONSIBILITY), List.of(PROJECT_P1_ACHIEVEMENT));

        CvTailoringValidationResult result = CvTailoringValidator.validate(snapshot(), tailoring);

        assertThat(result.violations()).containsExactly(
                new CvTailoringViolation(CvTailoringViolationCategory.UNKNOWN_PROJECT_REFERENCE, unknownProject, null));
    }

    // ==================== 18/19. Accumulation and deterministic order ====================

    @Test
    void validate_multipleIndependentProblems_areAllAccumulated_notFailFast() {
        UUID unknownSkill = UUID.randomUUID();
        CvTailoringResult tailoring = new CvTailoringResult(
                null,
                List.of(unknownSkill),
                List.of(new CvPositionTailoring(POSITION_A,
                        List.of(new CvResponsibilityTailoring(POSITION_B_RESPONSIBILITY, null)),
                        List.of(new CvAchievementTailoring(POSITION_B_ACHIEVEMENT, null)))),
                List.of(new CvProjectTailoring(PROJECT_P1,
                        List.of(new CvResponsibilityTailoring(PROJECT_P2_RESPONSIBILITY, null)),
                        List.of(new CvAchievementTailoring(PROJECT_P2_ACHIEVEMENT, null)))));

        CvTailoringValidationResult result = CvTailoringValidator.validate(snapshot(), tailoring);

        assertThat(result.violations()).hasSize(5);
        assertThat(result.violations()).extracting(CvTailoringViolation::category).containsExactlyInAnyOrder(
                CvTailoringViolationCategory.UNKNOWN_SKILL_REFERENCE,
                CvTailoringViolationCategory.POSITION_RESPONSIBILITY_NOT_OWNED,
                CvTailoringViolationCategory.POSITION_ACHIEVEMENT_NOT_OWNED,
                CvTailoringViolationCategory.PROJECT_RESPONSIBILITY_NOT_OWNED,
                CvTailoringViolationCategory.PROJECT_ACHIEVEMENT_NOT_OWNED);
    }

    @Test
    void validate_violationOrder_followsTailoringResultStructure_skillsThenPositionsThenProjects() {
        UUID unknownSkill = UUID.randomUUID();
        CvTailoringResult tailoring = new CvTailoringResult(
                null,
                List.of(unknownSkill),
                List.of(new CvPositionTailoring(POSITION_A, List.of(new CvResponsibilityTailoring(POSITION_B_RESPONSIBILITY, null)), List.of())),
                List.of(new CvProjectTailoring(PROJECT_P1, List.of(new CvResponsibilityTailoring(PROJECT_P2_RESPONSIBILITY, null)), List.of())));

        CvTailoringValidationResult result = CvTailoringValidator.validate(snapshot(), tailoring);

        assertThat(result.violations()).extracting(CvTailoringViolation::category).containsExactly(
                CvTailoringViolationCategory.UNKNOWN_SKILL_REFERENCE,
                CvTailoringViolationCategory.POSITION_RESPONSIBILITY_NOT_OWNED,
                CvTailoringViolationCategory.PROJECT_RESPONSIBILITY_NOT_OWNED);
    }

    // ==================== 20/21. No mutation ====================

    @Test
    void validate_doesNotMutateSourceSnapshotOrTailoringResult() {
        CvSourceSnapshot snapshot = snapshot();
        CvTailoringResult tailoring = positionTailoringResult(POSITION_A, List.of(POSITION_A_RESPONSIBILITY), List.of());
        CvSourceSnapshot snapshotBefore = snapshot;
        CvTailoringResult tailoringBefore = tailoring;

        CvTailoringValidator.validate(snapshot, tailoring);

        // Records are already immutable, so identity + repeated equal validation runs are the
        // practical proof: nothing about either input can have changed as a side effect of validating.
        assertThat(snapshot).isSameAs(snapshotBefore).isEqualTo(snapshotBefore);
        assertThat(tailoring).isSameAs(tailoringBefore).isEqualTo(tailoringBefore);
        assertThat(CvTailoringValidator.validate(snapshot, tailoring))
                .isEqualTo(CvTailoringValidator.validate(snapshot, tailoring));
    }

    // ==================== 22. Equivalent input -> equivalent result ====================

    @Test
    void validate_equivalentInput_yieldsEquivalentResult() {
        CvTailoringResult tailoringA = positionTailoringResult(POSITION_A, List.of(POSITION_B_RESPONSIBILITY), List.of());
        CvTailoringResult tailoringB = positionTailoringResult(POSITION_A, List.of(POSITION_B_RESPONSIBILITY), List.of());

        CvTailoringValidationResult resultA = CvTailoringValidator.validate(snapshot(), tailoringA);
        CvTailoringValidationResult resultB = CvTailoringValidator.validate(snapshot(), tailoringB);

        assertThat(resultA).isEqualTo(resultB);
    }

    // ==================== Null-argument guards ====================

    @Test
    void validate_nullSnapshot_isRejected() {
        assertThatThrownBy(() -> CvTailoringValidator.validate(null, new CvTailoringResult(null, List.of(), List.of(), List.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validate_nullTailoringResult_isRejected() {
        assertThatThrownBy(() -> CvTailoringValidator.validate(snapshot(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== Fixtures ====================

    private CvTailoringResult positionTailoringResult(
            UUID positionId, List<UUID> responsibilityIds, List<UUID> achievementIds) {
        return new CvTailoringResult(null, List.of(), List.of(new CvPositionTailoring(
                positionId,
                responsibilityIds.stream().map(id -> new CvResponsibilityTailoring(id, null)).toList(),
                achievementIds.stream().map(id -> new CvAchievementTailoring(id, null)).toList())),
                List.of());
    }

    private CvTailoringResult projectTailoringResult(
            UUID projectId, List<UUID> responsibilityIds, List<UUID> achievementIds) {
        return new CvTailoringResult(null, List.of(), List.of(), List.of(new CvProjectTailoring(
                projectId,
                responsibilityIds.stream().map(id -> new CvResponsibilityTailoring(id, null)).toList(),
                achievementIds.stream().map(id -> new CvAchievementTailoring(id, null)).toList())));
    }

    private CvSourceSnapshot snapshot() {
        CvSourceProject project1 = new CvSourceProject(PROJECT_P1, "Billing Platform", "Payments system",
                LocalDate.of(2021, 1, 1), LocalDate.of(2022, 1, 1),
                List.of(new CvSourceResponsibility(PROJECT_P1_RESPONSIBILITY, "Built the billing pipeline")),
                List.of(new CvSourceAchievement(PROJECT_P1_ACHIEVEMENT, "Cut billing errors by 30%")),
                List.of());
        CvSourcePosition positionA = new CvSourcePosition(POSITION_A, "Senior Backend Engineer", "Full-time", "Remote", "Remote",
                LocalDate.of(2021, 1, 1), null, true, "Owned backend services",
                List.of(new CvSourceResponsibility(POSITION_A_RESPONSIBILITY, "Led the backend team")),
                List.of(new CvSourceAchievement(POSITION_A_ACHIEVEMENT, "Promoted to senior within a year")),
                List.of(project1));

        CvSourceProject project2 = new CvSourceProject(PROJECT_P2, "Search Revamp", "Internal search overhaul",
                LocalDate.of(2018, 1, 1), LocalDate.of(2019, 1, 1),
                List.of(new CvSourceResponsibility(PROJECT_P2_RESPONSIBILITY, "Rebuilt the search index pipeline")),
                List.of(new CvSourceAchievement(PROJECT_P2_ACHIEVEMENT, "Reduced search latency by 40%")),
                List.of());
        CvSourcePosition positionB = new CvSourcePosition(POSITION_B, "Backend Engineer", "Full-time", "Berlin", "Hybrid",
                LocalDate.of(2018, 1, 1), LocalDate.of(2020, 12, 31), false, "Built internal tools",
                List.of(new CvSourceResponsibility(POSITION_B_RESPONSIBILITY, "Maintained internal tooling")),
                List.of(new CvSourceAchievement(POSITION_B_ACHIEVEMENT, "Shipped the internal search overhaul")),
                List.of(project2));

        CvSourceCompany company = new CvSourceCompany(UUID.randomUUID(), "Acme Corp", "https://acme.test", "Fintech",
                "Remote", "A fintech company", List.of(positionA, positionB));

        CandidateProfileFacts profile = new CandidateProfileFacts(
                "Senior Backend Engineer", "Senior",
                List.of(new CandidateSkillFacts(SKILL_JAVA, "Java", "Language", null, SkillProficiency.EXPERT),
                        new CandidateSkillFacts(SKILL_KAFKA, "Kafka", "Messaging", null, SkillProficiency.STRONG)),
                List.of(), 6,
                new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null));

        return new CvSourceSnapshot(profile, CareerHistoryAvailability.AVAILABLE, List.of(company));
    }
}
