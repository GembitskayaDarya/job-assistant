package com.darya.jobassistant.candidatecontext.cv.tailoring.skills;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.candidatecontext.CareerHistoryAvailability;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceSnapshot;
import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfileFacts;
import com.darya.jobassistant.candidates.CandidateSkillFacts;
import com.darya.jobassistant.candidates.SkillProficiency;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Sprint 11 Final Technical Skills Eligibility Polish: deterministic tests for {@link
 * CvSkillEligibilityPolicy} - proving the normal denylist (generic practices/activities/process),
 * the Git special-case Cases A-F, and the conditional Jenkins/GitLab/CI-CD behavior. Never depends
 * on one specific real company/vacancy.
 */
class CvSkillEligibilityPolicyTest {

    // ==================== General eligibility (Part 11) ====================

    @Test
    void apply_genericPracticeSkills_areExcluded() {
        UUID solidId = UUID.randomUUID();
        UUID cleanCodeId = UUID.randomUUID();
        UUID codeReviewId = UUID.randomUUID();
        UUID productThinkingId = UUID.randomUUID();
        UUID javaVersionMigrationId = UUID.randomUUID();
        UUID queryOptimizationId = UUID.randomUUID();
        UUID javaId = UUID.randomUUID();
        CvSourceSnapshot snapshot = snapshotWithSkills(
                skill(solidId, "SOLID"), skill(cleanCodeId, "Clean Code"), skill(codeReviewId, "Code Review"),
                skill(productThinkingId, "Product Thinking"), skill(javaVersionMigrationId, "Java Version Migration"),
                skill(queryOptimizationId, "Query Optimization"), skill(javaId, "Java"));

        List<UUID> result = CvSkillEligibilityPolicy.apply(
                List.of(solidId, cleanCodeId, codeReviewId, productThinkingId, javaVersionMigrationId, queryOptimizationId, javaId),
                snapshot, vacancyWithDescription("Requirements:\n- Java"));

        assertThat(result).containsExactly(javaId);
    }

    @Test
    void apply_processMethodologySkills_areExcluded() {
        UUID scrumId = UUID.randomUUID();
        UUID kanbanId = UUID.randomUUID();
        UUID agileId = UUID.randomUUID();
        UUID springBootId = UUID.randomUUID();
        CvSourceSnapshot snapshot = snapshotWithSkills(
                skill(scrumId, "Scrum"), skill(kanbanId, "Kanban"), skill(agileId, "Agile"), skill(springBootId, "Spring Boot"));

        List<UUID> result = CvSkillEligibilityPolicy.apply(
                List.of(scrumId, kanbanId, agileId, springBootId), snapshot, vacancyWithDescription("Requirements:\n- Spring Boot"));

        assertThat(result).containsExactly(springBootId);
    }

    @Test
    void apply_strongCanonicalBackendSkills_remainEligible() {
        UUID javaId = UUID.randomUUID();
        UUID springBootId = UUID.randomUUID();
        UUID kafkaId = UUID.randomUUID();
        UUID postgresId = UUID.randomUUID();
        UUID redisId = UUID.randomUUID();
        UUID dockerId = UUID.randomUUID();
        UUID kubernetesId = UUID.randomUUID();
        UUID awsId = UUID.randomUUID();
        UUID restApiId = UUID.randomUUID();
        List<UUID> ids = List.of(javaId, springBootId, kafkaId, postgresId, redisId, dockerId, kubernetesId, awsId, restApiId);
        CvSourceSnapshot snapshot = snapshotWithSkills(
                skill(javaId, "Java"), skill(springBootId, "Spring Boot"), skill(kafkaId, "Apache Kafka"),
                skill(postgresId, "PostgreSQL"), skill(redisId, "Redis"), skill(dockerId, "Docker"),
                skill(kubernetesId, "Kubernetes"), skill(awsId, "AWS"), skill(restApiId, "REST API"));

        List<UUID> result = CvSkillEligibilityPolicy.apply(ids, snapshot, vacancyWithDescription("Requirements:\n- Java"));

        assertThat(result).containsExactlyElementsOf(ids);
    }

    @Test
    void apply_preservesGivenOrder_whenNoExceptionsApply() {
        UUID kafkaId = UUID.randomUUID();
        UUID postgresId = UUID.randomUUID();
        UUID javaId = UUID.randomUUID();
        CvSourceSnapshot snapshot = snapshotWithSkills(skill(kafkaId, "Apache Kafka"), skill(postgresId, "PostgreSQL"), skill(javaId, "Java"));

        List<UUID> result = CvSkillEligibilityPolicy.apply(
                List.of(kafkaId, postgresId, javaId), snapshot, vacancyWithDescription("Requirements:\n- Java"));

        assertThat(result).containsExactly(kafkaId, postgresId, javaId);
    }

    // ==================== Git Cases A-F ====================

    @Test
    void apply_caseA_gitMandatoryAndFactuallyKnown_isIncluded() {
        UUID gitId = UUID.randomUUID();
        UUID javaId = UUID.randomUUID();
        CvSourceSnapshot snapshot = snapshotWithSkills(skill(gitId, "Git"), skill(javaId, "Java"));
        JobOffer vacancy = vacancyWithDescription("""
                Requirements:
                - Strong Git knowledge
                - Java
                """);

        List<UUID> result = CvSkillEligibilityPolicy.apply(List.of(javaId), snapshot, vacancy);

        assertThat(result).contains(gitId);
    }

    @Test
    void apply_caseB_gitOnlyInGenericTechStack_isExcluded() {
        UUID gitId = UUID.randomUUID();
        UUID javaId = UUID.randomUUID();
        CvSourceSnapshot snapshot = snapshotWithSkills(skill(gitId, "Git"), skill(javaId, "Java"));
        JobOffer vacancy = vacancyWithDescription("""
                Tech Stack:
                Java, Git, PostgreSQL

                Requirements:
                - Java
                """);

        List<UUID> result = CvSkillEligibilityPolicy.apply(List.of(gitId, javaId), snapshot, vacancy);

        assertThat(result).doesNotContain(gitId).containsExactly(javaId);
    }

    @Test
    void apply_caseC_gitNiceToHave_isExcluded() {
        UUID gitId = UUID.randomUUID();
        UUID javaId = UUID.randomUUID();
        CvSourceSnapshot snapshot = snapshotWithSkills(skill(gitId, "Git"), skill(javaId, "Java"));
        JobOffer vacancy = vacancyWithDescription("""
                Requirements:
                - Java

                Nice to have:
                - Git
                """);

        List<UUID> result = CvSkillEligibilityPolicy.apply(List.of(gitId, javaId), snapshot, vacancy);

        assertThat(result).doesNotContain(gitId).containsExactly(javaId);
    }

    @Test
    void apply_caseD_gitNotMentioned_isExcluded() {
        UUID gitId = UUID.randomUUID();
        UUID javaId = UUID.randomUUID();
        CvSourceSnapshot snapshot = snapshotWithSkills(skill(gitId, "Git"), skill(javaId, "Java"));
        JobOffer vacancy = vacancyWithDescription("Requirements:\n- Java, Spring Boot\n");

        List<UUID> result = CvSkillEligibilityPolicy.apply(List.of(gitId, javaId), snapshot, vacancy);

        assertThat(result).doesNotContain(gitId).containsExactly(javaId);
    }

    @Test
    void apply_caseE_gitMandatoryButCandidateDoesNotFactuallyHaveIt_neverInvented() {
        UUID javaId = UUID.randomUUID();
        // No "Git" fact anywhere in the candidate's inventory.
        CvSourceSnapshot snapshot = snapshotWithSkills(skill(javaId, "Java"));
        JobOffer vacancy = vacancyWithDescription("Requirements:\n- Must have Git\n- Java\n");

        List<UUID> result = CvSkillEligibilityPolicy.apply(List.of(javaId), snapshot, vacancy);

        assertThat(result).containsExactly(javaId);
        assertThat(result).noneMatch(id -> "Git".equalsIgnoreCase(nameOf(snapshot, id)));
    }

    @Test
    void apply_caseF_gitMandatoryWithSlotsConstrained_stillParticipatesAndDisplacesWeakestOnCap() {
        UUID gitId = UUID.randomUUID();
        List<UUID> strongIds = new ArrayList<>();
        List<CandidateSkillFacts> facts = new ArrayList<>();
        facts.add(skill(gitId, "Git"));
        for (int i = 0; i < 10; i++) {
            UUID id = UUID.randomUUID();
            strongIds.add(id);
            facts.add(skill(id, "Skill " + i));
        }
        CvSourceSnapshot snapshot = snapshotWithSkills(facts.toArray(new CandidateSkillFacts[0]));
        JobOffer vacancy = vacancyWithDescription("Requirements:\n- Git required\n");

        List<UUID> eligible = CvSkillEligibilityPolicy.apply(strongIds, snapshot, vacancy);
        List<UUID> finalCapped = CvSkillCanonicalizationPolicy.cap(eligible);

        // Git is prepended ahead of the AI's own 10 picks, so it survives the cap - the weakest
        // (last) AI pick is the one displaced, never Git itself.
        assertThat(finalCapped).hasSize(10).startsWith(gitId).doesNotContain(strongIds.get(9));
    }

    // ==================== Jenkins / GitLab / CI-CD conditional behavior ====================

    @Test
    void apply_jenkinsExplicitlyMandatory_isIncluded() {
        UUID jenkinsId = UUID.randomUUID();
        UUID javaId = UUID.randomUUID();
        CvSourceSnapshot snapshot = snapshotWithSkills(skill(jenkinsId, "Jenkins"), skill(javaId, "Java"));
        JobOffer vacancy = vacancyWithDescription("Requirements:\n- Jenkins required\n- Java\n");

        List<UUID> result = CvSkillEligibilityPolicy.apply(List.of(javaId), snapshot, vacancy);

        assertThat(result).contains(jenkinsId);
    }

    @Test
    void apply_gitLabMentionedCasually_isExcluded() {
        UUID gitLabId = UUID.randomUUID();
        UUID javaId = UUID.randomUUID();
        CvSourceSnapshot snapshot = snapshotWithSkills(skill(gitLabId, "GitLab"), skill(javaId, "Java"));
        JobOffer vacancy = vacancyWithDescription("Tech Stack:\nJava, GitLab\n\nRequirements:\n- Java\n");

        List<UUID> result = CvSkillEligibilityPolicy.apply(List.of(gitLabId, javaId), snapshot, vacancy);

        assertThat(result).doesNotContain(gitLabId);
    }

    @Test
    void apply_ciCdGenuineRequirement_isIncluded() {
        UUID ciCdId = UUID.randomUUID();
        UUID javaId = UUID.randomUUID();
        CvSourceSnapshot snapshot = snapshotWithSkills(skill(ciCdId, "CI/CD"), skill(javaId, "Java"));
        JobOffer vacancy = vacancyWithDescription("Requirements:\n- CI/CD pipeline design and ownership\n- Java\n");

        List<UUID> result = CvSkillEligibilityPolicy.apply(List.of(javaId), snapshot, vacancy);

        assertThat(result).contains(ciCdId);
    }

    @Test
    void apply_ciCdCasualStackMention_isExcluded() {
        UUID ciCdId = UUID.randomUUID();
        UUID javaId = UUID.randomUUID();
        CvSourceSnapshot snapshot = snapshotWithSkills(skill(ciCdId, "CI/CD"), skill(javaId, "Java"));
        JobOffer vacancy = vacancyWithDescription("Tech Stack:\nGitLab CI/CD, Java\n\nRequirements:\n- Java\n");

        List<UUID> result = CvSkillEligibilityPolicy.apply(List.of(ciCdId, javaId), snapshot, vacancy);

        assertThat(result).doesNotContain(ciCdId);
    }

    // ==================== Pipeline order: canonicalize -> eligibility -> cap ====================

    @Test
    void pipeline_canonicalizeThenEligibilityThenCap_appliedInOrder() {
        UUID springSecurityId = UUID.randomUUID();
        UUID springBootId = UUID.randomUUID();
        UUID solidId = UUID.randomUUID();
        UUID gitId = UUID.randomUUID();
        UUID javaId = UUID.randomUUID();
        CvSourceSnapshot snapshot = snapshotWithSkills(
                skill(springSecurityId, "Spring Security"), skill(springBootId, "Spring Boot"),
                skill(solidId, "SOLID"), skill(gitId, "Git"), skill(javaId, "Java"));
        JobOffer vacancy = vacancyWithDescription("Requirements:\n- Strong Git knowledge\n- Java, Spring Boot\n");

        // Raw AI selection includes a narrow Spring variant, a denylisted practice, and skips Git entirely.
        List<UUID> raw = List.of(springSecurityId, solidId, javaId);

        List<UUID> canonicalized = CvSkillCanonicalizationPolicy.canonicalize(raw, snapshot);
        assertThat(canonicalized).containsExactly(springBootId, solidId, javaId);

        List<UUID> eligible = CvSkillEligibilityPolicy.apply(canonicalized, snapshot, vacancy);
        // SOLID dropped (denylist); Git injected as an explicit-requirement exception, ahead of the rest.
        assertThat(eligible).containsExactly(gitId, springBootId, javaId);

        List<UUID> finalResult = CvSkillCanonicalizationPolicy.cap(eligible);
        assertThat(finalResult).containsExactly(gitId, springBootId, javaId);
    }

    // ==================== Fixtures ====================

    private String nameOf(CvSourceSnapshot snapshot, UUID id) {
        return snapshot.candidateProfile().skills().stream()
                .filter(s -> id.equals(s.candidateSkillId()))
                .map(CandidateSkillFacts::name)
                .findFirst().orElse(null);
    }

    private CandidateSkillFacts skill(UUID id, String name) {
        return new CandidateSkillFacts(id, name, null, null, SkillProficiency.STRONG);
    }

    private CvSourceSnapshot snapshotWithSkills(CandidateSkillFacts... skills) {
        CandidateProfileFacts profile = new CandidateProfileFacts(
                "Senior Java Backend Engineer", "Senior", List.of(skills), List.of(), 6,
                new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null));
        return new CvSourceSnapshot(profile, CareerHistoryAvailability.AVAILABLE, List.of(), List.of());
    }

    private JobOffer vacancyWithDescription(String description) {
        return new JobOffer("job-1", "Backend Engineer", "Acme Corp", "Remote", null, description, "https://example.com/job-1", "test");
    }
}
