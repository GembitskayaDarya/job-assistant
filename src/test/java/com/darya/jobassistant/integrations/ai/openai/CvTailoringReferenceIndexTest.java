package com.darya.jobassistant.integrations.ai.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.candidatecontext.CareerHistoryAvailability;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceCompany;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourcePersonalProject;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourcePersonalProjectHighlight;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourcePersonalProjectTechnology;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourcePosition;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceProject;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceResponsibility;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceSnapshot;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceTechnology;
import com.darya.jobassistant.candidates.CandidateEducationFacts;
import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfileFacts;
import com.darya.jobassistant.candidates.CandidateSkillFacts;
import com.darya.jobassistant.candidates.SkillProficiency;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Focused, deterministic tests for {@link CvTailoringReferenceIndex} - the prompt-local
 * typed-reference layer that replaced asking the AI to copy raw UUIDs. These tests reproduce the
 * exact real-production shapes that survived the earlier (UUID-copying) prompt fix: a technology
 * name shared between a candidate skill and a project technology, and two career projects sharing
 * the identical display name "Core Service" under two different positions.
 */
class CvTailoringReferenceIndexTest {

    // ==================== Same visible name -> different reference tokens ====================

    @Test
    void sameVisibleName_skillAndProjectTechnology_receiveDifferentRefsAndDifferentNamespaces() {
        UUID kafkaSkillId = UUID.randomUUID();
        UUID kafkaTechId = UUID.randomUUID();
        CvSourceSnapshot snapshot = snapshotWithSkillAndOneProjectTechnology("Kafka", kafkaSkillId, "Kafka", kafkaTechId);

        CvTailoringReferenceIndex index = CvTailoringReferenceIndex.build(snapshot);

        String skillRef = index.skillRefOf(kafkaSkillId);
        String techRef = index.projectTechnologyRefOf(kafkaTechId);
        assertThat(skillRef).isNotNull();
        assertThat(techRef).isNotNull();
        assertThat(skillRef).isNotEqualTo(techRef);
        assertThat(skillRef).startsWith("SKILL_");
        assertThat(techRef).startsWith("PROJECT_TECH_");
    }

    @Test
    void sameProjectName_underDifferentPositions_receiveDifferentProjectRefs() {
        UUID firstProjectId = UUID.randomUUID();
        UUID secondProjectId = UUID.randomUUID();
        CvSourceSnapshot snapshot = snapshotWithTwoSameNamedProjects("Core Service", firstProjectId, secondProjectId);

        CvTailoringReferenceIndex index = CvTailoringReferenceIndex.build(snapshot);

        String firstRef = index.projectRefOf(firstProjectId);
        String secondRef = index.projectRefOf(secondProjectId);
        assertThat(firstRef).isNotNull();
        assertThat(secondRef).isNotNull();
        assertThat(firstRef).isNotEqualTo(secondRef);
    }

    @Test
    void sameTechnologyName_underSiblingProjects_receiveDifferentTechnologyRefs() {
        UUID firstProjectId = UUID.randomUUID();
        UUID secondProjectId = UUID.randomUUID();
        UUID firstTechId = UUID.randomUUID();
        UUID secondTechId = UUID.randomUUID();
        CvSourceSnapshot snapshot = snapshotWithTwoSameNamedProjectsEachWithATechnology(
                "Core Service", firstProjectId, firstTechId, secondProjectId, secondTechId, "Kafka");

        CvTailoringReferenceIndex index = CvTailoringReferenceIndex.build(snapshot);

        String firstTechRef = index.projectTechnologyRefOf(firstTechId);
        String secondTechRef = index.projectTechnologyRefOf(secondTechId);
        assertThat(firstTechRef).isNotNull();
        assertThat(secondTechRef).isNotNull();
        assertThat(firstTechRef).isNotEqualTo(secondTechRef);
    }

    // ==================== Refs resolve to the exact original UUID ====================

    @Test
    void everyAssignedRef_resolvesToTheExactOriginalUuidAcrossEveryNamespace() {
        UUID skillId = UUID.randomUUID();
        UUID positionId = UUID.randomUUID();
        UUID positionRespId = UUID.randomUUID();
        UUID positionAchId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID projectRespId = UUID.randomUUID();
        UUID projectAchId = UUID.randomUUID();
        UUID techId = UUID.randomUUID();
        UUID personalProjectId = UUID.randomUUID();
        UUID highlightId = UUID.randomUUID();
        UUID personalTechId = UUID.randomUUID();

        CvSourceSnapshot snapshot = fullSnapshot(
                skillId, positionId, positionRespId, positionAchId, projectId, projectRespId, projectAchId, techId,
                personalProjectId, highlightId, personalTechId);
        CvTailoringReferenceIndex index = CvTailoringReferenceIndex.build(snapshot);

        assertThat(index.resolveSkillRef(index.skillRefOf(skillId))).isEqualTo(skillId);
        assertThat(index.resolvePositionRef(index.positionRefOf(positionId))).isEqualTo(positionId);
        assertThat(index.resolvePositionResponsibilityRef(index.positionRefOf(positionId), index.positionResponsibilityRefOf(positionRespId)))
                .isEqualTo(positionRespId);
        assertThat(index.resolvePositionAchievementRef(index.positionRefOf(positionId), index.positionAchievementRefOf(positionAchId)))
                .isEqualTo(positionAchId);
        assertThat(index.resolveProjectRef(index.projectRefOf(projectId))).isEqualTo(projectId);
        assertThat(index.resolveProjectResponsibilityRef(index.projectRefOf(projectId), index.projectResponsibilityRefOf(projectRespId)))
                .isEqualTo(projectRespId);
        assertThat(index.resolveProjectAchievementRef(index.projectRefOf(projectId), index.projectAchievementRefOf(projectAchId)))
                .isEqualTo(projectAchId);
        assertThat(index.resolveProjectTechnologyRef(index.projectRefOf(projectId), index.projectTechnologyRefOf(techId)))
                .isEqualTo(techId);
        assertThat(index.resolvePersonalProjectRef(index.personalProjectRefOf(personalProjectId))).isEqualTo(personalProjectId);
        assertThat(index.resolvePersonalProjectHighlightRef(
                index.personalProjectRefOf(personalProjectId), index.personalProjectHighlightRefOf(highlightId)))
                .isEqualTo(highlightId);
        assertThat(index.resolvePersonalProjectTechnologyRef(
                index.personalProjectRefOf(personalProjectId), index.personalProjectTechnologyRefOf(personalTechId)))
                .isEqualTo(personalTechId);
    }

    // ==================== Strict rejection: wrong namespace / wrong owner / unknown ====================

    @Test
    void resolveSkillRef_givenAProjectTechnologyRef_isRejected_wrongNamespace() {
        UUID skillId = UUID.randomUUID();
        UUID techId = UUID.randomUUID();
        CvSourceSnapshot snapshot = snapshotWithSkillAndOneProjectTechnology("Kafka", skillId, "Kafka", techId);
        CvTailoringReferenceIndex index = CvTailoringReferenceIndex.build(snapshot);
        String techRef = index.projectTechnologyRefOf(techId);

        assertThatThrownBy(() -> index.resolveSkillRef(techRef))
                .isInstanceOf(CvTailoringReferenceResolutionException.class);
    }

    @Test
    void resolveProjectTechnologyRef_givenASiblingProjectsTechnologyRef_isRejected_crossOwner() {
        UUID firstProjectId = UUID.randomUUID();
        UUID secondProjectId = UUID.randomUUID();
        UUID firstTechId = UUID.randomUUID();
        UUID secondTechId = UUID.randomUUID();
        CvSourceSnapshot snapshot = snapshotWithTwoSameNamedProjectsEachWithATechnology(
                "Core Service", firstProjectId, firstTechId, secondProjectId, secondTechId, "Kafka");
        CvTailoringReferenceIndex index = CvTailoringReferenceIndex.build(snapshot);
        String secondProjectRef = index.projectRefOf(secondProjectId);
        String firstTechRef = index.projectTechnologyRefOf(firstTechId);

        assertThatThrownBy(() -> index.resolveProjectTechnologyRef(secondProjectRef, firstTechRef))
                .isInstanceOf(CvTailoringReferenceResolutionException.class);
    }

    @Test
    void resolveSkillRef_unknownHallucinatedRef_isRejected() {
        CvSourceSnapshot snapshot = snapshotWithSkillAndOneProjectTechnology("Kafka", UUID.randomUUID(), "Kafka", UUID.randomUUID());
        CvTailoringReferenceIndex index = CvTailoringReferenceIndex.build(snapshot);

        assertThatThrownBy(() -> index.resolveSkillRef("SKILL_999"))
                .isInstanceOf(CvTailoringReferenceResolutionException.class);
    }

    @Test
    void resolveSkillRef_blankOrNullRef_isRejected() {
        CvSourceSnapshot snapshot = snapshotWithSkillAndOneProjectTechnology("Kafka", UUID.randomUUID(), "Kafka", UUID.randomUUID());
        CvTailoringReferenceIndex index = CvTailoringReferenceIndex.build(snapshot);

        assertThatThrownBy(() -> index.resolveSkillRef("")).isInstanceOf(CvTailoringReferenceResolutionException.class);
        assertThatThrownBy(() -> index.resolveSkillRef(null)).isInstanceOf(CvTailoringReferenceResolutionException.class);
    }

    @Test
    void resolutionException_carriesOnlyRefNamespaceAndOwnerRef_neverCandidateText() {
        UUID firstProjectId = UUID.randomUUID();
        UUID secondProjectId = UUID.randomUUID();
        UUID firstTechId = UUID.randomUUID();
        UUID secondTechId = UUID.randomUUID();
        CvSourceSnapshot snapshot = snapshotWithTwoSameNamedProjectsEachWithATechnology(
                "Core Service", firstProjectId, firstTechId, secondProjectId, secondTechId, "Kafka");
        CvTailoringReferenceIndex index = CvTailoringReferenceIndex.build(snapshot);
        String secondProjectRef = index.projectRefOf(secondProjectId);
        String firstTechRef = index.projectTechnologyRefOf(firstTechId);

        CvTailoringReferenceResolutionException exception = org.junit.jupiter.api.Assertions.assertThrows(
                CvTailoringReferenceResolutionException.class,
                () -> index.resolveProjectTechnologyRef(secondProjectRef, firstTechRef));

        assertThat(exception.ref()).isEqualTo(firstTechRef);
        assertThat(exception.expectedNamespace()).isEqualTo("PROJECT_TECHNOLOGY");
        assertThat(exception.ownerRef()).isEqualTo(secondProjectRef);
        assertThat(exception.getMessage()).doesNotContain("Kafka");
        assertThat(exception.getMessage()).doesNotContain("Core Service");
    }

    // ==================== Fixtures ====================

    private CvSourceSnapshot snapshotWithSkillAndOneProjectTechnology(
            String skillName, UUID skillId, String techName, UUID techId) {
        CvSourceProject project = new CvSourceProject(UUID.randomUUID(), "Core Service", null,
                LocalDate.of(2021, 1, 1), LocalDate.of(2022, 1, 1), List.of(), List.of(),
                List.of(new CvSourceTechnology(techId, techName, null)));
        CvSourcePosition position = new CvSourcePosition(UUID.randomUUID(), "Backend Engineer", null, null, null,
                LocalDate.of(2020, 1, 1), null, true, null, List.of(), List.of(), List.of(project));
        CvSourceCompany company = new CvSourceCompany(UUID.randomUUID(), "Acme", null, null, null, null, List.of(position));

        CandidateProfileFacts profile = candidateProfile(List.of(new CandidateSkillFacts(skillId, skillName, null, null, SkillProficiency.STRONG)));
        return new CvSourceSnapshot(profile, CareerHistoryAvailability.AVAILABLE, List.of(company), List.of());
    }

    private CvSourceSnapshot snapshotWithTwoSameNamedProjects(String sharedProjectName, UUID firstProjectId, UUID secondProjectId) {
        CvSourceProject firstProject = new CvSourceProject(firstProjectId, sharedProjectName, null,
                LocalDate.of(2023, 1, 1), LocalDate.of(2026, 1, 1), List.of(), List.of(), List.of());
        CvSourceProject secondProject = new CvSourceProject(secondProjectId, sharedProjectName, null,
                LocalDate.of(2026, 2, 1), null, List.of(), List.of(), List.of());
        CvSourcePosition firstPosition = new CvSourcePosition(UUID.randomUUID(), "Senior Backend Engineer", null, null, null,
                LocalDate.of(2023, 1, 1), LocalDate.of(2026, 1, 1), false, null, List.of(), List.of(), List.of(firstProject));
        CvSourcePosition secondPosition = new CvSourcePosition(UUID.randomUUID(), "Component Lead", null, null, null,
                LocalDate.of(2026, 2, 1), null, true, null, List.of(), List.of(), List.of(secondProject));
        CvSourceCompany company = new CvSourceCompany(UUID.randomUUID(), "Spribe", null, null, null, null,
                List.of(firstPosition, secondPosition));

        CandidateProfileFacts profile = candidateProfile(List.of());
        return new CvSourceSnapshot(profile, CareerHistoryAvailability.AVAILABLE, List.of(company), List.of());
    }

    private CvSourceSnapshot snapshotWithTwoSameNamedProjectsEachWithATechnology(
            String sharedProjectName, UUID firstProjectId, UUID firstTechId, UUID secondProjectId, UUID secondTechId, String sharedTechName) {
        CvSourceProject firstProject = new CvSourceProject(firstProjectId, sharedProjectName, null,
                LocalDate.of(2023, 1, 1), LocalDate.of(2026, 1, 1), List.of(), List.of(),
                List.of(new CvSourceTechnology(firstTechId, sharedTechName, null)));
        CvSourceProject secondProject = new CvSourceProject(secondProjectId, sharedProjectName, null,
                LocalDate.of(2026, 2, 1), null, List.of(), List.of(),
                List.of(new CvSourceTechnology(secondTechId, sharedTechName, null)));
        CvSourcePosition firstPosition = new CvSourcePosition(UUID.randomUUID(), "Senior Backend Engineer", null, null, null,
                LocalDate.of(2023, 1, 1), LocalDate.of(2026, 1, 1), false, null, List.of(), List.of(), List.of(firstProject));
        CvSourcePosition secondPosition = new CvSourcePosition(UUID.randomUUID(), "Component Lead", null, null, null,
                LocalDate.of(2026, 2, 1), null, true, null, List.of(), List.of(), List.of(secondProject));
        CvSourceCompany company = new CvSourceCompany(UUID.randomUUID(), "Spribe", null, null, null, null,
                List.of(firstPosition, secondPosition));

        CandidateProfileFacts profile = candidateProfile(List.of());
        return new CvSourceSnapshot(profile, CareerHistoryAvailability.AVAILABLE, List.of(company), List.of());
    }

    private CvSourceSnapshot fullSnapshot(
            UUID skillId, UUID positionId, UUID positionRespId, UUID positionAchId, UUID projectId, UUID projectRespId,
            UUID projectAchId, UUID techId, UUID personalProjectId, UUID highlightId, UUID personalTechId) {
        CvSourceProject project = new CvSourceProject(projectId, "Core Service", null,
                LocalDate.of(2021, 1, 1), LocalDate.of(2022, 1, 1),
                List.of(new CvSourceResponsibility(projectRespId, "Built the service")),
                List.of(new com.darya.jobassistant.candidatecontext.cv.model.CvSourceAchievement(projectAchId, "Shipped it")),
                List.of(new CvSourceTechnology(techId, "Kafka", null)));
        CvSourcePosition position = new CvSourcePosition(positionId, "Backend Engineer", null, null, null,
                LocalDate.of(2020, 1, 1), null, true, null,
                List.of(new CvSourceResponsibility(positionRespId, "Led backend work")),
                List.of(new com.darya.jobassistant.candidatecontext.cv.model.CvSourceAchievement(positionAchId, "Delivered the platform")),
                List.of(project));
        CvSourceCompany company = new CvSourceCompany(UUID.randomUUID(), "Acme", null, null, null, null, List.of(position));
        CvSourcePersonalProject personalProject = new CvSourcePersonalProject(personalProjectId, "Home Lab", null, null,
                LocalDate.of(2022, 1, 1), null,
                List.of(new CvSourcePersonalProjectHighlight(highlightId, "Built a dashboard")),
                List.of(new CvSourcePersonalProjectTechnology(personalTechId, "Grafana", null)));

        CandidateProfileFacts profile = candidateProfile(List.of(new CandidateSkillFacts(skillId, "Java", null, null, SkillProficiency.STRONG)));
        return new CvSourceSnapshot(profile, CareerHistoryAvailability.AVAILABLE, List.of(company), List.of(personalProject));
    }

    private CandidateProfileFacts candidateProfile(List<CandidateSkillFacts> skills) {
        return new CandidateProfileFacts(
                "Senior Java Backend Engineer", "Senior", skills, List.of(), 6,
                new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null),
                "Jane Candidate", "jane@example.test", "+1 555 0100", "https://linkedin.test/in/jane", "Remote", "Senior Backend Engineer",
                List.of(new CandidateEducationFacts(null, "State University", "BSc", "Computer Science", null, null, null, null, 0)));
    }
}
