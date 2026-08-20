package com.darya.jobassistant.candidatecontext.cv.baseline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

import com.darya.jobassistant.candidatecontext.CandidateContextProvider;
import com.darya.jobassistant.candidatecontext.CandidateContextSnapshot;
import com.darya.jobassistant.candidatecontext.cv.baseline.BaselineCvSelectionProperties.PersonalProjectSelection;
import com.darya.jobassistant.candidatecontext.cv.baseline.BaselineCvSelectionProperties.PositionSelection;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvDocument;
import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfileFacts;
import com.darya.jobassistant.candidates.CandidateSkillFacts;
import com.darya.jobassistant.candidates.SkillProficiency;
import com.darya.jobassistant.careerhistory.aggregate.CareerCompany;
import com.darya.jobassistant.careerhistory.aggregate.CareerHistoryAggregate;
import com.darya.jobassistant.careerhistory.aggregate.CareerPosition;
import com.darya.jobassistant.careerhistory.aggregate.CareerResponsibility;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProject;
import com.darya.jobassistant.personalprojects.aggregate.PersonalProjectHighlight;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Sprint 11 Big Block 7 (Part 15): fixtures use clearly generic/fake company/skill data ("Acme
 * Platform Inc") rather than the real candidate's, since this test is about the use case's wiring
 * and guarantees - not about the content of the real private baseline config.
 */
@ExtendWith(MockitoExtension.class)
class GenerateBaselineCvUseCaseTest {

    @Mock
    private CandidateContextProvider candidateContextProvider;

    private final UUID skillJavaId = UUID.randomUUID();
    private final UUID skillKafkaId = UUID.randomUUID();
    private final UUID skillDockerId = UUID.randomUUID();
    private final UUID positionId = UUID.randomUUID();
    private final UUID responsibilityId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(candidateContextProvider.loadCurrentContext()).thenReturn(context());
    }

    // ==================== No AI / no vacancy dependency ====================

    @Test
    void generate_useCaseHasNoAiOrVacancyDependency() {
        // Part 14: the deterministic baseline path must never call an AI port or take a vacancy -
        // proven structurally by inspecting this use case's own constructor, not just by behavior.
        for (var constructor : GenerateBaselineCvUseCase.class.getDeclaredConstructors()) {
            for (Class<?> parameterType : constructor.getParameterTypes()) {
                String packageName = parameterType.getPackageName();
                assertThat(packageName).doesNotContain("openai");
                assertThat(parameterType.getSimpleName()).doesNotContain("JobOffer").doesNotContain("Vacancy");
            }
        }
    }

    // ==================== Skill selection is a strict subset ====================

    @Test
    void generate_selectedSkills_areAStrictSubsetOfAllCandidateSkills_notEverything() {
        BaselineCvSelectionProperties properties = new BaselineCvSelectionProperties(
                null, List.of("Java"), List.of(), List.of());
        GenerateBaselineCvUseCase useCase = new GenerateBaselineCvUseCase(candidateContextProvider, properties);

        TailoredCvDocument document = useCase.generate();

        assertThat(document.skills()).containsExactly("Java");
        assertThat(document.skills()).hasSizeLessThan(3); // profile has 3 skills (Java, Kafka, Docker); only 1 selected
    }

    @Test
    void generate_selectAllSentinel_selectsEverySkill() {
        BaselineCvSelectionProperties properties = new BaselineCvSelectionProperties(
                null, List.of("*"), List.of(), List.of());
        GenerateBaselineCvUseCase useCase = new GenerateBaselineCvUseCase(candidateContextProvider, properties);

        TailoredCvDocument document = useCase.generate();

        assertThat(document.skills()).containsExactly("Java", "Kafka", "Docker");
    }

    // ==================== Fixed facts preserved exactly ====================

    @Test
    void generate_preservesCompanyAndPositionAndBulletTextExactly() {
        PositionSelection selection = new PositionSelection(
                "Acme Platform Inc", "Backend Engineer", List.of("Rebuilt the payments pipeline"), List.of(), List.of());
        BaselineCvSelectionProperties properties = new BaselineCvSelectionProperties(
                "A concise professional summary.", List.of(), List.of(selection), List.of());
        GenerateBaselineCvUseCase useCase = new GenerateBaselineCvUseCase(candidateContextProvider, properties);

        TailoredCvDocument document = useCase.generate();

        assertThat(document.professionalSummary()).isEqualTo("A concise professional summary.");
        assertThat(document.experience()).hasSize(1);
        assertThat(document.experience().get(0).name()).isEqualTo("Acme Platform Inc");
        assertThat(document.experience().get(0).positions().get(0).title()).isEqualTo("Backend Engineer");
        assertThat(document.experience().get(0).positions().get(0).responsibilities())
                .containsExactly("Rebuilt the payments pipeline");
    }

    // ==================== Unmatched natural-value reference fails loudly ====================

    @Test
    void generate_configReferencingUnknownCompany_throwsBaselineCvSelectionResolutionException() {
        PositionSelection selection = new PositionSelection("Nonexistent Corp", "Backend Engineer", List.of("*"), List.of(), List.of());
        BaselineCvSelectionProperties properties = new BaselineCvSelectionProperties(null, List.of(), List.of(selection), List.of());
        GenerateBaselineCvUseCase useCase = new GenerateBaselineCvUseCase(candidateContextProvider, properties);

        assertThatThrownBy(useCase::generate)
                .isInstanceOf(BaselineCvSelectionResolutionException.class)
                .hasMessageContaining("Nonexistent Corp");
    }

    @Test
    void generate_configReferencingUnknownSkill_throwsBaselineCvSelectionResolutionException() {
        BaselineCvSelectionProperties properties = new BaselineCvSelectionProperties(null, List.of("Rust"), List.of(), List.of());
        GenerateBaselineCvUseCase useCase = new GenerateBaselineCvUseCase(candidateContextProvider, properties);

        assertThatThrownBy(useCase::generate)
                .isInstanceOf(BaselineCvSelectionResolutionException.class)
                .hasMessageContaining("Rust");
    }

    @Test
    void generate_configReferencingUnknownPersonalProject_throwsBaselineCvSelectionResolutionException() {
        PersonalProjectSelection selection = new PersonalProjectSelection("Nonexistent Hobby Project", List.of(), List.of());
        BaselineCvSelectionProperties properties = new BaselineCvSelectionProperties(null, List.of(), List.of(), List.of(selection));
        GenerateBaselineCvUseCase useCase = new GenerateBaselineCvUseCase(candidateContextProvider, properties);

        assertThatThrownBy(useCase::generate)
                .isInstanceOf(BaselineCvSelectionResolutionException.class)
                .hasMessageContaining("Nonexistent Hobby Project");
    }

    // ==================== Empty config is a valid, deliberate empty CV ====================

    @Test
    void generate_emptyConfig_producesAnEmptySelectionButPreservesTheFullHierarchy() {
        // CvAssembler.assembleTailored never omits a company/position node itself (see its class
        // javadoc) - an empty selection means empty bullets/skills/personal-projects, not fewer nodes.
        BaselineCvSelectionProperties properties = new BaselineCvSelectionProperties(null, List.of(), List.of(), List.of());
        GenerateBaselineCvUseCase useCase = new GenerateBaselineCvUseCase(candidateContextProvider, properties);

        TailoredCvDocument document = useCase.generate();

        assertThat(document.skills()).isEmpty();
        assertThat(document.experience()).hasSize(1);
        assertThat(document.experience().get(0).positions().get(0).responsibilities()).isEmpty();
        assertThat(document.experience().get(0).positions().get(0).achievements()).isEmpty();
        assertThat(document.personalProjects()).hasSize(1);
        assertThat(document.personalProjects().get(0).highlights()).isEmpty();
    }

    // ==================== Fixtures ====================

    private CandidateContextSnapshot context() {
        CareerPosition position = new CareerPosition(positionId, "Backend Engineer", "Full-time", "Remote", "Remote",
                LocalDate.of(2020, 1, 1), null, true, "Owned backend services", 0,
                List.of(new CareerResponsibility(responsibilityId, "Rebuilt the payments pipeline", 0)), List.of(), List.of());
        CareerCompany company = new CareerCompany(UUID.randomUUID(), "Acme Platform Inc", null, null, null, null, 0, List.of(position));
        CareerHistoryAggregate careerHistory = new CareerHistoryAggregate(UUID.randomUUID(), UUID.randomUUID(), List.of(company), 0L);

        CandidateProfileFacts profile = new CandidateProfileFacts(
                "Senior Backend Engineer", "Senior",
                List.of(new CandidateSkillFacts(skillJavaId, "Java", "Language", null, SkillProficiency.EXPERT),
                        new CandidateSkillFacts(skillKafkaId, "Kafka", "Messaging", null, SkillProficiency.STRONG),
                        new CandidateSkillFacts(skillDockerId, "Docker", "DevOps", null, SkillProficiency.STRONG)),
                List.of(), 6,
                new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null));

        PersonalProject personalProject = new PersonalProject(UUID.randomUUID(), UUID.randomUUID(), "Home Lab Monitoring",
                "Self-hosted metrics stack", "https://github.test/example/homelab", LocalDate.of(2022, 1, 1), null, 0,
                List.of(new PersonalProjectHighlight(UUID.randomUUID(), "Built a Grafana dashboard", 0)), List.of(), 0L);

        return new CandidateContextSnapshot(
                UUID.randomUUID(), "primary", 0L, profile, Optional.of(careerHistory), List.of(personalProject));
    }
}
