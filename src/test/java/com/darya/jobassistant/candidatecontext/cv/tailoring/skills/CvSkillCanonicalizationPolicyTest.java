package com.darya.jobassistant.candidatecontext.cv.tailoring.skills;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.candidatecontext.CareerHistoryAvailability;
import com.darya.jobassistant.candidatecontext.cv.model.CvSourceSnapshot;
import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfileFacts;
import com.darya.jobassistant.candidates.CandidateSkillFacts;
import com.darya.jobassistant.candidates.SkillProficiency;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Sprint 11 Final CV Policy (Part 26 - CANONICALIZATION): deterministic tests for {@link
 * CvSkillCanonicalizationPolicy} - the application-owned presentation policy that collapses narrow
 * factual skill variants into their canonical recruiter-facing family, dedupes, and caps.
 */
class CvSkillCanonicalizationPolicyTest {

    @Test
    void canonicalize_springEcosystemVariants_collapseToSpringBoot() {
        UUID springSecurityId = UUID.randomUUID();
        UUID springBootId = UUID.randomUUID();
        CvSourceSnapshot snapshot = snapshotWithSkills(
                skill(springSecurityId, "Spring Security"), skill(springBootId, "Spring Boot"));

        List<UUID> result = CvSkillCanonicalizationPolicy.canonicalize(List.of(springSecurityId), snapshot);

        assertThat(result).containsExactly(springBootId);
    }

    @Test
    void canonicalize_kafkaConceptVariants_collapseToApacheKafka() {
        UUID kafkaConsumerGroupsId = UUID.randomUUID();
        UUID apacheKafkaId = UUID.randomUUID();
        CvSourceSnapshot snapshot = snapshotWithSkills(
                skill(kafkaConsumerGroupsId, "Kafka Consumer Groups"), skill(apacheKafkaId, "Apache Kafka"));

        List<UUID> result = CvSkillCanonicalizationPolicy.canonicalize(List.of(kafkaConsumerGroupsId), snapshot);

        assertThat(result).containsExactly(apacheKafkaId);
    }

    @Test
    void canonicalize_kubernetesResourceVariants_collapseToKubernetes() {
        UUID podsId = UUID.randomUUID();
        UUID kubernetesId = UUID.randomUUID();
        CvSourceSnapshot snapshot = snapshotWithSkills(skill(podsId, "Kubernetes Pods"), skill(kubernetesId, "Kubernetes"));

        List<UUID> result = CvSkillCanonicalizationPolicy.canonicalize(List.of(podsId), snapshot);

        assertThat(result).containsExactly(kubernetesId);
    }

    @Test
    void canonicalize_dockerCompose_collapsesToDocker() {
        UUID composeId = UUID.randomUUID();
        UUID dockerId = UUID.randomUUID();
        CvSourceSnapshot snapshot = snapshotWithSkills(skill(composeId, "Docker Compose"), skill(dockerId, "Docker"));

        List<UUID> result = CvSkillCanonicalizationPolicy.canonicalize(List.of(composeId), snapshot);

        assertThat(result).containsExactly(dockerId);
    }

    @Test
    void canonicalize_awsServiceVariants_collapseToAws() {
        UUID mskId = UUID.randomUUID();
        UUID keyspacesId = UUID.randomUUID();
        UUID awsId = UUID.randomUUID();
        CvSourceSnapshot snapshot = snapshotWithSkills(skill(mskId, "AWS MSK"), skill(keyspacesId, "AWS Keyspaces"), skill(awsId, "AWS"));

        List<UUID> result = CvSkillCanonicalizationPolicy.canonicalize(List.of(mskId, keyspacesId), snapshot);

        // Both AWS MSK and AWS Keyspaces resolve to the SAME canonical AWS id, so they dedupe to one entry.
        assertThat(result).containsExactly(awsId);
    }

    @Test
    void canonicalize_restTemplate_collapsesToRestApi() {
        UUID restTemplateId = UUID.randomUUID();
        UUID restApiId = UUID.randomUUID();
        CvSourceSnapshot snapshot = snapshotWithSkills(skill(restTemplateId, "RestTemplate"), skill(restApiId, "REST API"));

        List<UUID> result = CvSkillCanonicalizationPolicy.canonicalize(List.of(restTemplateId), snapshot);

        assertThat(result).containsExactly(restApiId);
    }

    @Test
    void canonicalize_sqlTuningConcepts_collapseToSql() {
        UUID queryOptimizationId = UUID.randomUUID();
        UUID databaseIndexesId = UUID.randomUUID();
        UUID sqlId = UUID.randomUUID();
        CvSourceSnapshot snapshot = snapshotWithSkills(
                skill(queryOptimizationId, "Query Optimization"), skill(databaseIndexesId, "Database Indexes"), skill(sqlId, "SQL"));

        List<UUID> result = CvSkillCanonicalizationPolicy.canonicalize(List.of(queryOptimizationId, databaseIndexesId), snapshot);

        assertThat(result).containsExactly(sqlId);
    }

    @Test
    void canonicalize_jpa_collapsesToHibernate() {
        UUID jpaId = UUID.randomUUID();
        UUID hibernateId = UUID.randomUUID();
        CvSourceSnapshot snapshot = snapshotWithSkills(skill(jpaId, "JPA"), skill(hibernateId, "Hibernate"));

        List<UUID> result = CvSkillCanonicalizationPolicy.canonicalize(List.of(jpaId), snapshot);

        assertThat(result).containsExactly(hibernateId);
    }

    @Test
    void canonicalize_twoDifferentNarrowVariantsMappingToSameFamily_dedupeToOneEntry() {
        UUID springSecurityId = UUID.randomUUID();
        UUID springMvcId = UUID.randomUUID();
        UUID springBootId = UUID.randomUUID();
        CvSourceSnapshot snapshot = snapshotWithSkills(
                skill(springSecurityId, "Spring Security"), skill(springMvcId, "Spring MVC"), skill(springBootId, "Spring Boot"));

        List<UUID> result = CvSkillCanonicalizationPolicy.canonicalize(List.of(springSecurityId, springMvcId), snapshot);

        assertThat(result).containsExactly(springBootId);
    }

    @Test
    void canonicalize_preservesAiFirstSeenOrder_afterCollapsingAndDeduping() {
        UUID kafkaId = UUID.randomUUID();
        UUID postgresId = UUID.randomUUID();
        UUID springSecurityId = UUID.randomUUID();
        UUID springBootId = UUID.randomUUID();
        CvSourceSnapshot snapshot = snapshotWithSkills(
                skill(kafkaId, "Apache Kafka"), skill(postgresId, "PostgreSQL"),
                skill(springSecurityId, "Spring Security"), skill(springBootId, "Spring Boot"));

        // AI order: Kafka, PostgreSQL, Spring Security (-> Spring Boot).
        List<UUID> result = CvSkillCanonicalizationPolicy.canonicalize(List.of(kafkaId, postgresId, springSecurityId), snapshot);

        assertThat(result).containsExactly(kafkaId, postgresId, springBootId);
    }

    @Test
    void canonicalize_skillWithNoMapping_passesThroughUnchanged() {
        UUID javaId = UUID.randomUUID();
        CvSourceSnapshot snapshot = snapshotWithSkills(skill(javaId, "Java"));

        List<UUID> result = CvSkillCanonicalizationPolicy.canonicalize(List.of(javaId), snapshot);

        assertThat(result).containsExactly(javaId);
    }

    @Test
    void canonicalize_canonicalTargetNotInCandidateInventory_fallsBackToOriginalId_neverDropped() {
        UUID springSecurityId = UUID.randomUUID();
        // Candidate's inventory has NO "Spring Boot" fact at all.
        CvSourceSnapshot snapshot = snapshotWithSkills(skill(springSecurityId, "Spring Security"));

        List<UUID> result = CvSkillCanonicalizationPolicy.canonicalize(List.of(springSecurityId), snapshot);

        assertThat(result).containsExactly(springSecurityId);
    }

    @Test
    void canonicalize_moreThanTenDistinctSkills_returnsAllUncapped_keepingFirstSeenOrder() {
        List<CandidateSkillFacts> skills = new java.util.ArrayList<>();
        List<UUID> ids = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            skills.add(new CandidateSkillFacts(id, "Skill " + i, null, null, SkillProficiency.STRONG));
        }
        CvSourceSnapshot snapshot = snapshotWithSkills(skills.toArray(new CandidateSkillFacts[0]));

        // canonicalize() no longer caps internally - CvSkillEligibilityPolicy must see the full
        // uncapped set so a required exception can still displace a weak entry; cap() runs last.
        List<UUID> result = CvSkillCanonicalizationPolicy.canonicalize(ids, snapshot);

        assertThat(result).hasSize(12).containsExactlyElementsOf(ids);
    }

    @Test
    void cap_moreThanTenIds_isCappedToTen_keepingGivenOrder() {
        List<UUID> ids = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            ids.add(UUID.randomUUID());
        }

        List<UUID> result = CvSkillCanonicalizationPolicy.cap(ids);

        assertThat(result).hasSize(10).containsExactlyElementsOf(ids.subList(0, 10));
    }

    @Test
    void cap_tenOrFewerIds_returnsUnchanged() {
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());

        assertThat(CvSkillCanonicalizationPolicy.cap(ids)).containsExactlyElementsOf(ids);
    }

    @Test
    void cap_emptyOrNullInput_returnsEmpty() {
        assertThat(CvSkillCanonicalizationPolicy.cap(List.of())).isEmpty();
        assertThat(CvSkillCanonicalizationPolicy.cap(null)).isEmpty();
    }

    @Test
    void canonicalize_emptyInput_returnsEmpty() {
        CvSourceSnapshot snapshot = snapshotWithSkills();

        assertThat(CvSkillCanonicalizationPolicy.canonicalize(List.of(), snapshot)).isEmpty();
        assertThat(CvSkillCanonicalizationPolicy.canonicalize(null, snapshot)).isEmpty();
    }

    @Test
    void canonicalize_matchingIsCaseInsensitive() {
        UUID springSecurityId = UUID.randomUUID();
        UUID springBootId = UUID.randomUUID();
        // Deliberately lowercase, unlike the canonicalization map's own casing.
        CvSourceSnapshot snapshot = snapshotWithSkills(skill(springSecurityId, "spring security"), skill(springBootId, "Spring Boot"));

        List<UUID> result = CvSkillCanonicalizationPolicy.canonicalize(List.of(springSecurityId), snapshot);

        assertThat(result).containsExactly(springBootId);
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
}
