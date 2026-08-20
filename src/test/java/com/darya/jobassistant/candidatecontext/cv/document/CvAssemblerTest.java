package com.darya.jobassistant.candidatecontext.cv.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.candidatecontext.CareerHistoryAvailability;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvCompany;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvDocument;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvPersonalProject;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvPosition;
import com.darya.jobassistant.candidatecontext.cv.document.model.TailoredCvProject;
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
import com.darya.jobassistant.candidates.CandidateEducationFacts;
import com.darya.jobassistant.candidates.CandidateLanguageFacts;
import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfileFacts;
import com.darya.jobassistant.candidates.CandidateSkillFacts;
import com.darya.jobassistant.candidates.SkillProficiency;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Final acceptance correction: covers both explicit {@link CvAssembler} modes separately -
 * {@link CvAssembler#assembleBaseline} (deterministic, application-owned, no tailoring result) and
 * {@link CvAssembler#assembleTailored} (a missing tailoring entry means zero content for that node,
 * never every factual item - see {@link CvAssembler}'s class javadoc for the exact semantics).
 */
class CvAssemblerTest {

    private static final UUID SKILL_JAVA = UUID.randomUUID();
    private static final UUID SKILL_KAFKA = UUID.randomUUID();

    private static final UUID POSITION_ID = UUID.randomUUID();
    private static final UUID POSITION_RESPONSIBILITY = UUID.randomUUID();
    private static final UUID POSITION_ACHIEVEMENT = UUID.randomUUID();

    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID PROJECT_RESPONSIBILITY = UUID.randomUUID();
    private static final UUID PROJECT_ACHIEVEMENT = UUID.randomUUID();
    private static final UUID PROJECT_TECHNOLOGY = UUID.randomUUID();

    private static final UUID PERSONAL_PROJECT_ID = UUID.randomUUID();
    private static final UUID PERSONAL_PROJECT_HIGHLIGHT = UUID.randomUUID();
    private static final UUID PERSONAL_PROJECT_TECHNOLOGY = UUID.randomUUID();

    // ==================== assembleBaseline: deterministic universal baseline ====================

    @Test
    void assembleBaseline_headerFieldsAndCvHeadline_arePreservedExactly() {
        TailoredCvDocument document = CvAssembler.assembleBaseline(snapshot());

        assertThat(document.header().fullName()).isEqualTo("Jane Candidate");
        assertThat(document.header().cvHeadline()).isEqualTo("Senior Backend Engineer");
        assertThat(document.header().cvLocation()).isEqualTo("Remote");
        assertThat(document.header().email()).isEqualTo("jane@example.test");
        assertThat(document.header().phone()).isEqualTo("+1 555 0100");
        assertThat(document.header().linkedinUrl()).isEqualTo("https://linkedin.test/in/jane");
    }

    @Test
    void assembleBaseline_education_isPreservedExactly() {
        TailoredCvDocument document = CvAssembler.assembleBaseline(snapshot());

        assertThat(document.education()).containsExactly(
                new CandidateEducationFacts(null, "State University", "BSc", "Computer Science", null, null, null, null, 0));
    }

    @Test
    void assembleBaseline_languagesAndProficiency_arePreservedExactly() {
        TailoredCvDocument document = CvAssembler.assembleBaseline(snapshot());

        assertThat(document.languages()).containsExactly(new CandidateLanguageFacts("English", "Native"));
    }

    @Test
    void assembleBaseline_professionalSummary_isAlwaysNull() {
        TailoredCvDocument document = CvAssembler.assembleBaseline(snapshot());

        assertThat(document.professionalSummary()).isNull();
    }

    @Test
    void assembleBaseline_skills_showsEveryFactualSkillUnchangedOrder() {
        TailoredCvDocument document = CvAssembler.assembleBaseline(snapshot());

        assertThat(document.skills()).containsExactly("Java", "Kafka");
    }

    @Test
    void assembleBaseline_companyPositionProjectHierarchy_isPreserved() {
        TailoredCvDocument document = CvAssembler.assembleBaseline(snapshot());

        assertThat(document.experience()).hasSize(1);
        TailoredCvCompany company = document.experience().get(0);
        assertThat(company.name()).isEqualTo("Acme Corp");
        assertThat(company.positions()).hasSize(1);
        TailoredCvPosition position = company.positions().get(0);
        assertThat(position.title()).isEqualTo("Senior Backend Engineer");
        assertThat(position.startDate()).isEqualTo(LocalDate.of(2021, 1, 1));
        assertThat(position.projects()).hasSize(1);
        assertThat(position.projects().get(0).name()).isEqualTo("Billing Platform");
    }

    @Test
    void assembleBaseline_positionBullets_showsEveryFactualResponsibilityAndAchievement() {
        TailoredCvDocument document = CvAssembler.assembleBaseline(snapshot());

        TailoredCvPosition position = document.experience().get(0).positions().get(0);
        assertThat(position.responsibilities()).containsExactly("Owned backend services");
        assertThat(position.achievements()).containsExactly("Promoted to senior within a year");
    }

    @Test
    void assembleBaseline_projectBulletsAndTechnologies_showsEveryFactualItemUnchanged() {
        TailoredCvDocument document = CvAssembler.assembleBaseline(snapshot());

        TailoredCvProject project = document.experience().get(0).positions().get(0).projects().get(0);
        assertThat(project.responsibilities()).containsExactly("Built the billing pipeline");
        assertThat(project.achievements()).containsExactly("Cut billing errors by 30%");
        assertThat(project.technologies()).containsExactly("Kafka");
    }

    @Test
    void assembleBaseline_personalProject_everyFactualFieldAndHighlightAndTechnology_isPreserved() {
        TailoredCvDocument document = CvAssembler.assembleBaseline(snapshot());

        assertThat(document.personalProjects()).hasSize(1);
        TailoredCvPersonalProject project = document.personalProjects().get(0);
        assertThat(project.name()).isEqualTo("Home Lab Monitoring");
        assertThat(project.description()).isEqualTo("Self-hosted metrics stack");
        assertThat(project.url()).isEqualTo("https://github.test/example/homelab");
        assertThat(project.startDate()).isEqualTo(LocalDate.of(2022, 1, 1));
        assertThat(project.highlights()).containsExactly("Built a Grafana dashboard for home metrics");
        assertThat(project.technologies()).containsExactly("Grafana");
    }

    @Test
    void assembleBaseline_nullSnapshot_isRejected() {
        assertThatThrownBy(() -> CvAssembler.assembleBaseline(null)).isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== assembleTailored: fixed factual data preserved regardless of tailoring ====================

    @Test
    void assembleTailored_headerEducationAndLanguages_arePreservedExactlyAndUnaffectedByTailoring() {
        TailoredCvDocument document = CvAssembler.assembleTailored(snapshot(), emptyTailoring());

        assertThat(document.header().fullName()).isEqualTo("Jane Candidate");
        assertThat(document.education()).containsExactly(
                new CandidateEducationFacts(null, "State University", "BSc", "Computer Science", null, null, null, null, 0));
        assertThat(document.languages()).containsExactly(new CandidateLanguageFacts("English", "Native"));
    }

    // ==================== assembleTailored: skills - explicit empty selection means zero skills ====================

    @Test
    void assembleTailored_skills_resolvedInAiSelectedOrder() {
        CvTailoringResult tailoring = new CvTailoringResult(null, List.of(SKILL_KAFKA, SKILL_JAVA), List.of(), List.of());

        TailoredCvDocument document = CvAssembler.assembleTailored(snapshot(), tailoring);

        assertThat(document.skills()).containsExactly("Kafka", "Java");
    }

    @Test
    void assembleTailored_skills_emptySelection_meansZeroSkills_neverFallsBackToAll() {
        TailoredCvDocument document = CvAssembler.assembleTailored(snapshot(), emptyTailoring());

        assertThat(document.skills()).isEmpty();
    }

    // ==================== assembleTailored: responsibility/achievement selection, ordering, rewrite ====================

    @Test
    void assembleTailored_positionResponsibilities_selectedOrderingIsPreserved() {
        UUID secondResponsibility = UUID.randomUUID();
        CvSourceSnapshot snapshot = snapshotWithTwoPositionResponsibilities(secondResponsibility);
        CvTailoringResult tailoring = new CvTailoringResult(null, List.of(), List.of(new CvPositionTailoring(POSITION_ID,
                List.of(new CvResponsibilityTailoring(secondResponsibility, null), new CvResponsibilityTailoring(POSITION_RESPONSIBILITY, null)),
                List.of())), List.of());

        TailoredCvDocument document = CvAssembler.assembleTailored(snapshot, tailoring);

        assertThat(document.experience().get(0).positions().get(0).responsibilities())
                .containsExactly("Second responsibility", "Owned backend services");
    }

    @Test
    void assembleTailored_allowedRewrite_isAppliedInsteadOfOriginalText() {
        CvTailoringResult tailoring = positionTailoring(
                List.of(new CvResponsibilityTailoring(POSITION_RESPONSIBILITY, "Rewritten responsibility text")), List.of());

        TailoredCvDocument document = CvAssembler.assembleTailored(snapshot(), tailoring);

        assertThat(document.experience().get(0).positions().get(0).responsibilities())
                .containsExactly("Rewritten responsibility text");
    }

    @Test
    void assembleTailored_noRewrite_showsOriginalSourceText() {
        CvTailoringResult tailoring = positionTailoring(List.of(new CvResponsibilityTailoring(POSITION_RESPONSIBILITY, null)), List.of());

        TailoredCvDocument document = CvAssembler.assembleTailored(snapshot(), tailoring);

        assertThat(document.experience().get(0).positions().get(0).responsibilities())
                .containsExactly("Owned backend services");
    }

    @Test
    void assembleTailored_achievements_selectedOrderingAndRewrite_arePreserved() {
        CvTailoringResult tailoring = positionTailoring(
                List.of(), List.of(new CvAchievementTailoring(POSITION_ACHIEVEMENT, "Promoted twice in two years")));

        TailoredCvDocument document = CvAssembler.assembleTailored(snapshot(), tailoring);

        assertThat(document.experience().get(0).positions().get(0).achievements())
                .containsExactly("Promoted twice in two years");
    }

    @Test
    void assembleTailored_projectTechnologyIds_resolveCorrectly() {
        CvTailoringResult tailoring = new CvTailoringResult(null, List.of(), List.of(), List.of(
                new CvProjectTailoring(PROJECT_ID, List.of(), List.of(), List.of(PROJECT_TECHNOLOGY))));

        TailoredCvDocument document = CvAssembler.assembleTailored(snapshot(), tailoring);

        assertThat(document.experience().get(0).positions().get(0).projects().get(0).technologies())
                .containsExactly("Kafka");
    }

    @Test
    void assembleTailored_personalProjectHighlightAndTechnologyIds_resolveCorrectly() {
        CvTailoringResult tailoring = new CvTailoringResult(null, List.of(), List.of(), List.of(), List.of(
                new CvPersonalProjectTailoring(PERSONAL_PROJECT_ID, List.of(PERSONAL_PROJECT_HIGHLIGHT), List.of(PERSONAL_PROJECT_TECHNOLOGY))));

        TailoredCvDocument document = CvAssembler.assembleTailored(snapshot(), tailoring);

        TailoredCvPersonalProject project = document.personalProjects().get(0);
        assertThat(project.highlights()).containsExactly("Built a Grafana dashboard for home metrics");
        assertThat(project.technologies()).containsExactly("Grafana");
    }

    // ==================== assembleTailored: missing entry means zero content, never everything ====================

    @Test
    void assembleTailored_positionWithNoTailoringEntry_showsZeroBullets_neverEveryFactualBullet() {
        TailoredCvDocument document = CvAssembler.assembleTailored(snapshot(), emptyTailoring());

        TailoredCvPosition position = document.experience().get(0).positions().get(0);
        assertThat(position.responsibilities()).isEmpty();
        assertThat(position.achievements()).isEmpty();
    }

    @Test
    void assembleTailored_projectWithNoTailoringEntry_showsZeroBulletsAndTechnologies_neverEveryFactualItem() {
        TailoredCvDocument document = CvAssembler.assembleTailored(snapshot(), emptyTailoring());

        TailoredCvProject project = document.experience().get(0).positions().get(0).projects().get(0);
        assertThat(project.responsibilities()).isEmpty();
        assertThat(project.achievements()).isEmpty();
        assertThat(project.technologies()).isEmpty();
    }

    @Test
    void assembleTailored_personalProjectWithNoTailoringEntry_showsZeroHighlightsAndTechnologies_neverEveryFactualItem() {
        TailoredCvDocument document = CvAssembler.assembleTailored(snapshot(), emptyTailoring());

        TailoredCvPersonalProject project = document.personalProjects().get(0);
        assertThat(project.highlights()).isEmpty();
        assertThat(project.technologies()).isEmpty();
    }

    @Test
    void assembleTailored_positionWithNoTailoringEntry_stillPreservesMandatoryPositionAndCompanyIdentity() {
        TailoredCvDocument document = CvAssembler.assembleTailored(snapshot(), emptyTailoring());

        assertThat(document.experience()).hasSize(1);
        assertThat(document.experience().get(0).name()).isEqualTo("Acme Corp");
        TailoredCvPosition position = document.experience().get(0).positions().get(0);
        assertThat(position.title()).isEqualTo("Senior Backend Engineer");
        assertThat(position.startDate()).isEqualTo(LocalDate.of(2021, 1, 1));
        assertThat(position.projects()).hasSize(1);
        assertThat(position.projects().get(0).name()).isEqualTo("Billing Platform");
    }

    @Test
    void assembleTailored_personalProjectWithNoTailoringEntry_stillPreservesMandatoryIdentity() {
        TailoredCvDocument document = CvAssembler.assembleTailored(snapshot(), emptyTailoring());

        assertThat(document.personalProjects()).hasSize(1);
        assertThat(document.personalProjects().get(0).name()).isEqualTo("Home Lab Monitoring");
        assertThat(document.personalProjects().get(0).url()).isEqualTo("https://github.test/example/homelab");
    }

    // ==================== assembleTailored: an explicit (even empty) entry is a deliberate AI choice ====================

    @Test
    void assembleTailored_presentTailoringEntryWithEmptySelections_showsZeroBullets_deliberateAiChoiceHonored() {
        CvTailoringResult tailoring = positionTailoring(List.of(), List.of());

        TailoredCvDocument document = CvAssembler.assembleTailored(snapshot(), tailoring);

        assertThat(document.experience().get(0).positions().get(0).responsibilities()).isEmpty();
        assertThat(document.experience().get(0).positions().get(0).achievements()).isEmpty();
    }

    // ==================== assembleTailored: no AI field can modify fixed factual data ====================

    @Test
    void assembleTailored_professionalSummary_takenFromTailoringResult_neverFromSource() {
        CvTailoringResult tailoring = new CvTailoringResult("Tailored summary", List.of(), List.of(), List.of());

        TailoredCvDocument document = CvAssembler.assembleTailored(snapshot(), tailoring);

        assertThat(document.professionalSummary()).isEqualTo("Tailored summary");
    }

    @Test
    void assembleTailored_positionTitleDatesAndCompanyIdentity_areNeverAffectedByTailoring() {
        CvTailoringResult withTailoring = positionTailoring(
                List.of(new CvResponsibilityTailoring(POSITION_RESPONSIBILITY, "Rewritten")), List.of());

        TailoredCvDocument tailored = CvAssembler.assembleTailored(snapshot(), withTailoring);
        TailoredCvDocument baseline = CvAssembler.assembleBaseline(snapshot());

        assertThat(tailored.experience().get(0).name()).isEqualTo(baseline.experience().get(0).name());
        assertThat(tailored.experience().get(0).positions().get(0).title())
                .isEqualTo(baseline.experience().get(0).positions().get(0).title());
        assertThat(tailored.experience().get(0).positions().get(0).startDate())
                .isEqualTo(baseline.experience().get(0).positions().get(0).startDate());
    }

    // ==================== assembleTailored: defensive guards ====================

    @Test
    void assembleTailored_nullSnapshot_isRejected() {
        assertThatThrownBy(() -> CvAssembler.assembleTailored(null, emptyTailoring())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void assembleTailored_nullTailoringResult_isRejected() {
        assertThatThrownBy(() -> CvAssembler.assembleTailored(snapshot(), null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void assembleTailored_unresolvedSkillId_throwsIllegalStateException_ratherThanSilentlyDropping() {
        CvTailoringResult tailoring = new CvTailoringResult(null, List.of(UUID.randomUUID()), List.of(), List.of());

        assertThatThrownBy(() -> CvAssembler.assembleTailored(snapshot(), tailoring)).isInstanceOf(IllegalStateException.class);
    }

    // ==================== Fixtures ====================

    private CvTailoringResult emptyTailoring() {
        return new CvTailoringResult(null, List.of(), List.of(), List.of());
    }

    private CvTailoringResult positionTailoring(List<CvResponsibilityTailoring> responsibilities, List<CvAchievementTailoring> achievements) {
        return new CvTailoringResult(null, List.of(), List.of(new CvPositionTailoring(POSITION_ID, responsibilities, achievements)), List.of());
    }

    private CvSourceSnapshot snapshotWithTwoPositionResponsibilities(UUID secondResponsibilityId) {
        CvSourcePosition position = new CvSourcePosition(POSITION_ID, "Senior Backend Engineer", "Full-time", "Remote", "Remote",
                LocalDate.of(2021, 1, 1), null, true, "Owned backend services",
                List.of(new CvSourceResponsibility(POSITION_RESPONSIBILITY, "Owned backend services"),
                        new CvSourceResponsibility(secondResponsibilityId, "Second responsibility")),
                List.of(new CvSourceAchievement(POSITION_ACHIEVEMENT, "Promoted to senior within a year")),
                List.of());
        CvSourceCompany company = new CvSourceCompany(UUID.randomUUID(), "Acme Corp", "https://acme.test", "Fintech",
                "Remote", "A fintech company", List.of(position));
        return new CvSourceSnapshot(profile(), CareerHistoryAvailability.AVAILABLE, List.of(company), List.of());
    }

    private CvSourceSnapshot snapshot() {
        CvSourceProject project = new CvSourceProject(PROJECT_ID, "Billing Platform", "Payments system",
                LocalDate.of(2021, 1, 1), LocalDate.of(2022, 1, 1),
                List.of(new CvSourceResponsibility(PROJECT_RESPONSIBILITY, "Built the billing pipeline")),
                List.of(new CvSourceAchievement(PROJECT_ACHIEVEMENT, "Cut billing errors by 30%")),
                List.of(new CvSourceTechnology(PROJECT_TECHNOLOGY, "Kafka", "Messaging")));
        CvSourcePosition position = new CvSourcePosition(POSITION_ID, "Senior Backend Engineer", "Full-time", "Remote", "Remote",
                LocalDate.of(2021, 1, 1), null, true, "Owned backend services",
                List.of(new CvSourceResponsibility(POSITION_RESPONSIBILITY, "Owned backend services")),
                List.of(new CvSourceAchievement(POSITION_ACHIEVEMENT, "Promoted to senior within a year")),
                List.of(project));
        CvSourceCompany company = new CvSourceCompany(UUID.randomUUID(), "Acme Corp", "https://acme.test", "Fintech",
                "Remote", "A fintech company", List.of(position));

        CvSourcePersonalProject personalProject = new CvSourcePersonalProject(PERSONAL_PROJECT_ID, "Home Lab Monitoring",
                "Self-hosted metrics stack", "https://github.test/example/homelab", LocalDate.of(2022, 1, 1), null,
                List.of(new CvSourcePersonalProjectHighlight(PERSONAL_PROJECT_HIGHLIGHT, "Built a Grafana dashboard for home metrics")),
                List.of(new CvSourcePersonalProjectTechnology(PERSONAL_PROJECT_TECHNOLOGY, "Grafana", "Observability")));

        return new CvSourceSnapshot(profile(), CareerHistoryAvailability.AVAILABLE, List.of(company), List.of(personalProject));
    }

    private CandidateProfileFacts profile() {
        return new CandidateProfileFacts(
                "Senior Backend Engineer", "Senior",
                List.of(new CandidateSkillFacts(SKILL_JAVA, "Java", "Language", null, SkillProficiency.EXPERT),
                        new CandidateSkillFacts(SKILL_KAFKA, "Kafka", "Messaging", null, SkillProficiency.STRONG)),
                List.of(new CandidateLanguageFacts("English", "Native")),
                6,
                new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null),
                "Jane Candidate", "jane@example.test", "+1 555 0100", "https://linkedin.test/in/jane", "Remote", "Senior Backend Engineer",
                List.of(new CandidateEducationFacts(null, "State University", "BSc", "Computer Science", null, null, null, null, 0)));
    }
}
