package com.darya.jobassistant.candidatecontext.cv.baseline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.candidatecontext.CareerHistoryAvailability;
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
 * Sprint 11 Big Block 7 (Part 15): all fixtures use clearly generic/fake company and skill names
 * ("Acme Platform Inc", "Legacy Systems Corp") - deliberately not the real candidate's employer
 * names - proving {@link BaselineCvSelectionResolver} works purely by natural-value matching
 * against whatever {@link CvSourceSnapshot} it is given, with nothing hardcoded.
 */
class BaselineCvSelectionResolverTest {

    private static final UUID SKILL_JAVA_ID = UUID.randomUUID();
    private static final UUID SKILL_KAFKA_ID = UUID.randomUUID();

    private static final UUID RESPONSIBILITY_A_ID = UUID.randomUUID();
    private static final UUID RESPONSIBILITY_B_ID = UUID.randomUUID();
    private static final UUID ACHIEVEMENT_A_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID TECHNOLOGY_ID = UUID.randomUUID();

    private static final UUID PERSONAL_PROJECT_ID = UUID.randomUUID();
    private static final UUID PERSONAL_HIGHLIGHT_ID = UUID.randomUUID();
    private static final UUID PERSONAL_TECHNOLOGY_ID = UUID.randomUUID();

    // ==================== Skills ====================

    @Test
    void resolve_selectAllSkills_selectsEverySkillInSourceOrder() {
        CvTailoringResult result = BaselineCvSelectionResolver.resolve(config(List.of("*"), List.of(), List.of()), snapshot());

        assertThat(result.orderedSkillIds()).containsExactly(SKILL_JAVA_ID, SKILL_KAFKA_ID);
    }

    @Test
    void resolve_explicitSkillList_selectsOnlyThoseInGivenOrder() {
        CvTailoringResult result = BaselineCvSelectionResolver.resolve(config(List.of("Kafka"), List.of(), List.of()), snapshot());

        assertThat(result.orderedSkillIds()).containsExactly(SKILL_KAFKA_ID);
    }

    @Test
    void resolve_emptySkillList_isADeliberateEmptySelection() {
        // A non-empty position keeps this resolve() call outside requireNonEmptyBaseline's "every
        // field empty at once" guard (see that method's javadoc) - orthogonal to what this test
        // actually proves: an empty skill selection is honored as a deliberate zero-skill choice.
        PositionSelection selection = new PositionSelection("Acme Platform Inc", "Backend Engineer", List.of("*"), List.of(), List.of());
        CvTailoringResult result = BaselineCvSelectionResolver.resolve(config(List.of(), List.of(selection), List.of()), snapshot());

        assertThat(result.orderedSkillIds()).isEmpty();
    }

    @Test
    void resolve_unknownSkillReference_throws() {
        assertThatThrownBy(() -> BaselineCvSelectionResolver.resolve(config(List.of("Rust"), List.of(), List.of()), snapshot()))
                .isInstanceOf(BaselineCvSelectionResolutionException.class)
                .hasMessageContaining("Rust");
    }

    // ==================== Positions: responsibilities / achievements ====================

    @Test
    void resolve_selectAllResponsibilitiesAndAchievements_selectsAllInSourceOrder() {
        PositionSelection selection = new PositionSelection("Acme Platform Inc", "Backend Engineer", List.of("*"), List.of("*"), List.of());
        CvTailoringResult result = BaselineCvSelectionResolver.resolve(config(List.of(), List.of(selection), List.of()), snapshot());

        CvPositionTailoring positionTailoring = result.positionTailoring().get(0);
        assertThat(positionTailoring.responsibilities()).extracting(CvResponsibilityTailoring::careerResponsibilityId)
                .containsExactly(RESPONSIBILITY_A_ID, RESPONSIBILITY_B_ID);
        assertThat(positionTailoring.achievements()).hasSize(1);
    }

    @Test
    void resolve_explicitResponsibilitySelection_selectsOnlyThoseByExactText() {
        PositionSelection selection = new PositionSelection(
                "Acme Platform Inc", "Backend Engineer", List.of("Rebuilt the payments pipeline"), List.of(), List.of());
        CvTailoringResult result = BaselineCvSelectionResolver.resolve(config(List.of(), List.of(selection), List.of()), snapshot());

        assertThat(result.positionTailoring().get(0).responsibilities())
                .extracting(CvResponsibilityTailoring::careerResponsibilityId).containsExactly(RESPONSIBILITY_A_ID);
    }

    @Test
    void resolve_emptyResponsibilitySelection_selectsNone() {
        PositionSelection selection = new PositionSelection("Acme Platform Inc", "Backend Engineer", List.of(), List.of(), List.of());
        CvTailoringResult result = BaselineCvSelectionResolver.resolve(config(List.of(), List.of(selection), List.of()), snapshot());

        assertThat(result.positionTailoring().get(0).responsibilities()).isEmpty();
    }

    @Test
    void resolve_unknownCompany_throwsMentioningCompanyAndPosition() {
        PositionSelection selection = new PositionSelection("Nonexistent Corp", "Backend Engineer", List.of("*"), List.of(), List.of());

        assertThatThrownBy(() -> BaselineCvSelectionResolver.resolve(config(List.of(), List.of(selection), List.of()), snapshot()))
                .isInstanceOf(BaselineCvSelectionResolutionException.class)
                .hasMessageContaining("Nonexistent Corp")
                .hasMessageContaining("Backend Engineer");
    }

    @Test
    void resolve_unknownPositionUnderKnownCompany_throws() {
        PositionSelection selection = new PositionSelection("Acme Platform Inc", "Chief Astronaut", List.of("*"), List.of(), List.of());

        assertThatThrownBy(() -> BaselineCvSelectionResolver.resolve(config(List.of(), List.of(selection), List.of()), snapshot()))
                .isInstanceOf(BaselineCvSelectionResolutionException.class)
                .hasMessageContaining("Chief Astronaut");
    }

    @Test
    void resolve_unknownResponsibilityText_throws() {
        PositionSelection selection = new PositionSelection(
                "Acme Platform Inc", "Backend Engineer", List.of("This text was never written"), List.of(), List.of());

        assertThatThrownBy(() -> BaselineCvSelectionResolver.resolve(config(List.of(), List.of(selection), List.of()), snapshot()))
                .isInstanceOf(BaselineCvSelectionResolutionException.class)
                .hasMessageContaining("This text was never written");
    }

    @Test
    void resolve_unknownAchievementText_throws() {
        PositionSelection selection = new PositionSelection(
                "Acme Platform Inc", "Backend Engineer", List.of(), List.of("Never happened"), List.of());

        assertThatThrownBy(() -> BaselineCvSelectionResolver.resolve(config(List.of(), List.of(selection), List.of()), snapshot()))
                .isInstanceOf(BaselineCvSelectionResolutionException.class)
                .hasMessageContaining("Never happened");
    }

    // ==================== Nested projects under a position ====================

    @Test
    void resolve_projectSelection_resolvesProjectAndItsSelectedTechnologies() {
        ProjectSelection projectSelection = new ProjectSelection("Payments Revamp", List.of("Kafka"));
        PositionSelection positionSelection = new PositionSelection(
                "Acme Platform Inc", "Backend Engineer", List.of(), List.of(), List.of(projectSelection));

        CvTailoringResult result = BaselineCvSelectionResolver.resolve(config(List.of(), List.of(positionSelection), List.of()), snapshot());

        CvProjectTailoring projectTailoring = result.projectTailoring().get(0);
        assertThat(projectTailoring.careerProjectId()).isEqualTo(PROJECT_ID);
        assertThat(projectTailoring.orderedTechnologyIds()).containsExactly(TECHNOLOGY_ID);
    }

    @Test
    void resolve_unknownProjectNameUnderKnownPosition_throws() {
        ProjectSelection projectSelection = new ProjectSelection("Nonexistent Project", List.of("*"));
        PositionSelection positionSelection = new PositionSelection(
                "Acme Platform Inc", "Backend Engineer", List.of(), List.of(), List.of(projectSelection));

        assertThatThrownBy(() -> BaselineCvSelectionResolver.resolve(config(List.of(), List.of(positionSelection), List.of()), snapshot()))
                .isInstanceOf(BaselineCvSelectionResolutionException.class)
                .hasMessageContaining("Nonexistent Project");
    }

    // ==================== Personal projects ====================

    @Test
    void resolve_personalProjectSelection_resolvesHighlightsAndTechnologies() {
        PersonalProjectSelection selection = new PersonalProjectSelection("Home Lab Monitoring", List.of("*"), List.of("*"));

        CvTailoringResult result = BaselineCvSelectionResolver.resolve(config(List.of(), List.of(), List.of(selection)), snapshot());

        var personalProjectTailoring = result.personalProjectTailoring().get(0);
        assertThat(personalProjectTailoring.personalProjectId()).isEqualTo(PERSONAL_PROJECT_ID);
        assertThat(personalProjectTailoring.orderedHighlightIds()).containsExactly(PERSONAL_HIGHLIGHT_ID);
        assertThat(personalProjectTailoring.orderedTechnologyIds()).containsExactly(PERSONAL_TECHNOLOGY_ID);
    }

    @Test
    void resolve_unknownPersonalProjectName_throws() {
        PersonalProjectSelection selection = new PersonalProjectSelection("Nonexistent Hobby Project", List.of(), List.of());

        assertThatThrownBy(() -> BaselineCvSelectionResolver.resolve(config(List.of(), List.of(), List.of(selection)), snapshot()))
                .isInstanceOf(BaselineCvSelectionResolutionException.class)
                .hasMessageContaining("Nonexistent Hobby Project");
    }

    // ==================== Professional summary ====================

    @Test
    void resolve_blankProfessionalSummary_isNormalizedToNull() {
        // A non-empty skill selection keeps this resolve() call outside requireNonEmptyBaseline's
        // "every field empty at once" guard - orthogonal to what this test actually proves: a
        // blank (whitespace-only) summary string normalizes to null rather than surviving as-is.
        BaselineCvSelectionProperties config = new BaselineCvSelectionProperties("   ", List.of("Java"), List.of(), List.of(), null);

        CvTailoringResult result = BaselineCvSelectionResolver.resolve(config, snapshot());

        assertThat(result.professionalSummary()).isNull();
    }

    @Test
    void resolve_nonBlankProfessionalSummary_isPreservedVerbatim() {
        BaselineCvSelectionProperties config = new BaselineCvSelectionProperties("A concise summary.", List.of(), List.of(), List.of(), null);

        CvTailoringResult result = BaselineCvSelectionResolver.resolve(config, snapshot());

        assertThat(result.professionalSummary()).isEqualTo("A concise summary.");
    }

    // ==================== Null guards ====================

    @Test
    void resolve_nullConfig_isRejected() {
        assertThatThrownBy(() -> BaselineCvSelectionResolver.resolve(null, snapshot())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resolve_nullSnapshot_isRejected() {
        assertThatThrownBy(() -> BaselineCvSelectionResolver.resolve(config(List.of(), List.of(), List.of()), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== Fixtures ====================

    private BaselineCvSelectionProperties config(List<String> skills, List<PositionSelection> positions, List<PersonalProjectSelection> personalProjects) {
        return new BaselineCvSelectionProperties(null, skills, positions, personalProjects, null);
    }

    private CvSourceSnapshot snapshot() {
        CvSourceProject project = new CvSourceProject(PROJECT_ID, "Payments Revamp", "Rebuilt the payments platform",
                LocalDate.of(2021, 1, 1), LocalDate.of(2022, 1, 1), List.of(), List.of(),
                List.of(new CvSourceTechnology(TECHNOLOGY_ID, "Kafka", "Messaging")));

        CvSourcePosition position = new CvSourcePosition(UUID.randomUUID(), "Backend Engineer", "Full-time", "Remote", "Remote",
                LocalDate.of(2020, 1, 1), null, true, "Owned backend services",
                List.of(new CvSourceResponsibility(RESPONSIBILITY_A_ID, "Rebuilt the payments pipeline"),
                        new CvSourceResponsibility(RESPONSIBILITY_B_ID, "Mentored two junior engineers")),
                List.of(new CvSourceAchievement(ACHIEVEMENT_A_ID, "Cut latency by half")),
                List.of(project));

        CvSourceCompany company = new CvSourceCompany(UUID.randomUUID(), "Acme Platform Inc", "https://acme-platform.test",
                "Software", "Remote", "A fictional test company", List.of(position));

        CvSourceCompany otherCompany = new CvSourceCompany(UUID.randomUUID(), "Legacy Systems Corp", null, null, null, null,
                List.of(new CvSourcePosition(UUID.randomUUID(), "Junior Developer", null, null, null,
                        LocalDate.of(2017, 1, 1), LocalDate.of(2019, 12, 31), false, null, List.of(), List.of(), List.of())));

        CandidateProfileFacts profile = new CandidateProfileFacts(
                "Senior Backend Engineer", "Senior",
                List.of(new CandidateSkillFacts(SKILL_JAVA_ID, "Java", "Language", null, SkillProficiency.EXPERT),
                        new CandidateSkillFacts(SKILL_KAFKA_ID, "Kafka", "Messaging", null, SkillProficiency.STRONG)),
                List.of(), 6,
                new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null));

        CvSourcePersonalProject personalProject = new CvSourcePersonalProject(PERSONAL_PROJECT_ID, "Home Lab Monitoring",
                "Self-hosted metrics stack", "https://github.test/example/homelab", LocalDate.of(2022, 1, 1), null,
                List.of(new CvSourcePersonalProjectHighlight(PERSONAL_HIGHLIGHT_ID, "Built a Grafana dashboard for home metrics")),
                List.of(new CvSourcePersonalProjectTechnology(PERSONAL_TECHNOLOGY_ID, "Grafana", "Observability")));

        return new CvSourceSnapshot(profile, CareerHistoryAvailability.AVAILABLE, List.of(company, otherCompany), List.of(personalProject));
    }
}
