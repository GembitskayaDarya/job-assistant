package com.darya.jobassistant.candidatecontext.cv.tailoring.skills;

import com.darya.jobassistant.candidatecontext.cv.model.CvSourceSnapshot;
import com.darya.jobassistant.candidatecontext.cv.tailoring.CvSkillTailoringResult;
import com.darya.jobassistant.candidatecontext.cv.tailoring.CvTailoringResult;
import com.darya.jobassistant.candidates.CandidateSkillFacts;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Sprint 11 Final CV Policy: the deterministic, application-owned presentation policy applied to
 * the AI's raw {@link CvSkillTailoringResult} before it becomes {@link
 * CvTailoringResult#orderedSkillIds()} - "Technical Skills must contain canonical recruiter-facing
 * technology families, not small framework modules, infrastructure primitives, or implementation
 * details." A small, explicit, hand-maintained mapping - never a generic ontology/rules engine -
 * from a narrow, granular factual skill name to the broader canonical family it presents as on a
 * CV. This is presentation canonicalization only: nothing here deletes, renames, or otherwise
 * touches the candidate's actual factual skill inventory ({@code CandidateSkillFacts}) - a
 * canonicalized-away skill remains exactly as present in the source data as before, simply not
 * separately displayed on the CV's own Technical Skills line when its broader family is already
 * shown there.
 *
 * <p>Deliberately placed in its own {@code candidatecontext.cv.tailoring.skills} sub-package rather
 * than directly in {@code candidatecontext.cv.tailoring}: that package's own boundary test
 * intentionally forbids referencing {@code candidatecontext.cv.model} at all, so a policy whose
 * signature needs {@link CvSourceSnapshot} cannot live there - this sub-package mirrors {@code
 * candidatecontext.cv.tailoring.validation}'s intentionally more permissive guard instead.
 *
 * <h2>Pipeline position (Sprint 11 Final Technical Skills Eligibility Polish)</h2>
 *
 * <pre>{@code
 * AI raw orderedSkillIds
 *         -> map each id's factual name through the canonical family table
 *         -> resolve each canonical family name back to ITS OWN candidate skill id
 *            (falls back to the original, uncanonicalized id if the candidate has no fact for
 *            the canonical family name - never crashes, never invents a skill)
 *         -> dedupe by resolved id, keeping first-seen order (the AI's own priority order)   [canonicalize]
 *         -> apply top-CV eligibility policy                                    [CvSkillEligibilityPolicy]
 *         -> apply explicit requirement exceptions (e.g. Git)                   [CvSkillEligibilityPolicy]
 *         -> cap to MAX_SKILLS                                                              [cap]
 * }</pre>
 *
 * <p>{@link #canonicalize} deliberately no longer caps internally (a prior version of this class
 * did) - the eligibility policy and its explicit-requirement exceptions must run on the full,
 * uncapped canonical set so a required-but-normally-excluded skill (Git) can still displace a
 * weaker entry even when the AI's raw selection already reached the cap. {@link #cap} is the
 * final pipeline step, applied by {@code CvTailoringUseCase} after eligibility.
 *
 * <h2>Why id, not name</h2>
 *
 * {@link CvTailoringResult#orderedSkillIds()} - and {@code CvAssembler#resolveSkills} downstream -
 * is id-based, matching every other selection surface in this pipeline. Canonicalizing by name and
 * then immediately re-resolving to the canonical name's own real candidate skill id keeps that
 * contract intact rather than introducing a second, name-based skill representation alongside it.
 */
public final class CvSkillCanonicalizationPolicy {

    /** Part 13's hard maximum - target is 8 to 10, this is the absolute backstop applied here (before {@code CvAssembler}'s own, slightly looser, general-purpose cap runs as a second line of defense). */
    private static final int MAX_SKILLS = 10;

    /**
     * Narrow factual skill name (matched case-insensitively) -> canonical recruiter-facing family
     * display name. Every target name below is a real, existing skill in this candidate's factual
     * inventory - see {@code config/candidate-profile.yml}. A name with no entry here is already
     * considered canonical and passes through unchanged.
     */
    private static final Map<String, String> CANONICAL_FAMILY_BY_LOWERCASE_NAME = buildCanonicalFamilyMap();

    private CvSkillCanonicalizationPolicy() {
    }

    public static List<UUID> canonicalize(List<UUID> rawOrderedSkillIds, CvSourceSnapshot snapshot) {
        if (rawOrderedSkillIds == null || rawOrderedSkillIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, String> nameById = new LinkedHashMap<>();
        Map<String, UUID> idByLowercaseName = new LinkedHashMap<>();
        for (CandidateSkillFacts skill : snapshot.candidateProfile().skills()) {
            if (skill.candidateSkillId() != null) {
                nameById.put(skill.candidateSkillId(), skill.name());
                idByLowercaseName.putIfAbsent(skill.name().toLowerCase(Locale.ROOT), skill.candidateSkillId());
            }
        }

        Map<UUID, Boolean> seen = new LinkedHashMap<>();
        for (UUID rawId : rawOrderedSkillIds) {
            String rawName = nameById.get(rawId);
            UUID canonicalId = rawId;
            if (rawName != null) {
                String canonicalName = CANONICAL_FAMILY_BY_LOWERCASE_NAME.get(rawName.toLowerCase(Locale.ROOT));
                if (canonicalName != null) {
                    UUID resolved = idByLowercaseName.get(canonicalName.toLowerCase(Locale.ROOT));
                    // Fall back to the original, uncanonicalized id if the candidate's current skill
                    // inventory has no fact for the canonical family name - never crash, never invent.
                    canonicalId = resolved != null ? resolved : rawId;
                }
            }
            seen.putIfAbsent(canonicalId, Boolean.TRUE);
        }

        return List.copyOf(seen.keySet());
    }

    /**
     * Final pipeline step - caps to {@value #MAX_SKILLS}, keeping whatever order the caller's list
     * already has (this method never reorders). Applied by {@code CvTailoringUseCase} only after
     * {@code CvSkillEligibilityPolicy} has run, so a required-but-normally-ineligible skill (Git)
     * that policy re-included can still survive the cap even when the AI's own raw selection had
     * already reached {@value #MAX_SKILLS} eligible entries.
     */
    public static List<UUID> cap(List<UUID> orderedSkillIds) {
        if (orderedSkillIds == null || orderedSkillIds.isEmpty()) {
            return List.of();
        }
        return orderedSkillIds.size() <= MAX_SKILLS ? List.copyOf(orderedSkillIds) : List.copyOf(orderedSkillIds.subList(0, MAX_SKILLS));
    }

    private static Map<String, String> buildCanonicalFamilyMap() {
        Map<String, String> map = new LinkedHashMap<>();
        // Spring ecosystem modules -> Spring Boot
        putCanonical(map, "Spring Security", "Spring Boot");
        putCanonical(map, "Spring MVC", "Spring Boot");
        putCanonical(map, "Spring Data JPA", "Spring Boot");
        putCanonical(map, "Spring Data JDBC", "Spring Boot");
        putCanonical(map, "Spring Framework", "Spring Boot");
        putCanonical(map, "Spring Actuator", "Spring Boot");

        // Kafka internals/concepts -> Apache Kafka
        putCanonical(map, "Kafka Producers", "Apache Kafka");
        putCanonical(map, "Kafka Consumers", "Apache Kafka");
        putCanonical(map, "Kafka Consumer Groups", "Apache Kafka");
        putCanonical(map, "Kafka Partitions", "Apache Kafka");
        putCanonical(map, "Kafka Offsets", "Apache Kafka");
        putCanonical(map, "Kafka Rebalancing", "Apache Kafka");
        putCanonical(map, "Kafka Streams", "Apache Kafka");
        putCanonical(map, "Kafka Delivery Semantics", "Apache Kafka");
        putCanonical(map, "Retry Topics", "Apache Kafka");

        // Kubernetes resources -> Kubernetes
        putCanonical(map, "Kubernetes Pods", "Kubernetes");
        putCanonical(map, "Kubernetes Deployments", "Kubernetes");
        putCanonical(map, "Kubernetes ConfigMaps", "Kubernetes");

        // Docker components -> Docker
        putCanonical(map, "Docker Compose", "Docker");

        // AWS service variants -> AWS
        putCanonical(map, "AWS CloudFormation", "AWS");
        putCanonical(map, "AWS MSK", "AWS");
        putCanonical(map, "AWS Keyspaces", "AWS");

        // REST implementation details -> REST API
        putCanonical(map, "RestTemplate", "REST API");

        // SQL tuning/indexing concepts -> SQL
        putCanonical(map, "Query Optimization", "SQL");
        putCanonical(map, "Database Indexes", "SQL");

        // ORM concept -> its dominant real-world implementation
        putCanonical(map, "JPA", "Hibernate");

        return map;
    }

    private static void putCanonical(Map<String, String> map, String narrowName, String canonicalName) {
        map.put(narrowName.toLowerCase(Locale.ROOT), canonicalName);
    }
}
