package com.darya.jobassistant.candidatecontext.cv.tailoring.skills;

import com.darya.jobassistant.candidatecontext.cv.model.CvSourceSnapshot;
import com.darya.jobassistant.candidates.CandidateSkillFacts;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Sprint 11 Final Technical Skills Eligibility Polish: the deterministic, application-owned
 * presentation policy applied to the already-canonicalized, deduplicated skill list, before the
 * final cap - "the top Technical Skills section should contain strong technologies / architecture
 * capabilities, not generic engineering practices or activities."
 *
 * <h2>Pipeline position</h2>
 *
 * <pre>{@code
 * AI selected factual skills
 *         -> CvSkillCanonicalizationPolicy.canonicalize (collapse + dedupe, uncapped)
 *         -> CvSkillEligibilityPolicy.apply (this class: denylist filter + explicit-requirement exceptions)
 *         -> CvSkillCanonicalizationPolicy.cap (final maximum)
 *         -> CvAssembler.assembleTailored
 * }</pre>
 *
 * <h2>Two kinds of ineligibility</h2>
 *
 * <ul>
 * <li>{@link #NORMALLY_INELIGIBLE} - generic engineering practices (SOLID, Clean Code, Code Review,
 * Product Thinking), activities/experience descriptions (Java Version Migration, Query Optimization,
 * Performance Optimization, Technical Debt, Architecture Decisions), and process/methodology (Scrum,
 * Kanban, Agile). Always excluded from the top Technical Skills section - no override.
 * <li>{@link #CONDITIONALLY_ELIGIBLE} - Git, CI/CD, Jenkins, GitLab. Excluded by default (very basic
 * developer tooling / standalone tools that should not normally displace core backend skills), but
 * re-included when {@link VacancyRequirementClassifier#isExplicitlyRequired} says the vacancy
 * explicitly demands the exact term <strong>and</strong> the candidate factually has a matching
 * skill - never invented, never added on AI judgment alone.
 * </ul>
 *
 * <p>None of this deletes or renames anything in the candidate's factual skill inventory
 * ({@link CandidateSkillFacts}) - presentation eligibility only, exactly like {@link
 * CvSkillCanonicalizationPolicy}.
 *
 * <h2>Why exceptions are prepended, not appended</h2>
 *
 * An explicit-requirement exception is inserted at the front of the returned list, ahead of the
 * AI's own eligible picks. This is deliberate: if the AI's raw selection already produced 10 (or
 * more) eligible entries, appending an exception at the end would have it silently dropped by the
 * final cap - exactly what "Git is mandatory but final skill slots are constrained -> Git
 * participates as a required ATS signal" (Sprint 11 Final Technical Skills Eligibility Polish, test
 * Case F) requires NOT to happen. A deterministic, vacancy-matched required signal outranks the
 * AI's own generic relevance ordering for its own single slot; it never reorders anything else.
 */
public final class CvSkillEligibilityPolicy {

    private static final Set<String> NORMALLY_INELIGIBLE = lowercaseSet(
            "SOLID", "Clean Code", "Code Review", "Product Thinking",
            "Java Version Migration", "Query Optimization", "Performance Optimization",
            "Technical Debt", "Architecture Decisions",
            "Scrum", "Kanban", "Agile");

    private static final Set<String> CONDITIONALLY_ELIGIBLE = lowercaseSet("Git", "CI/CD", "Jenkins", "GitLab");

    private CvSkillEligibilityPolicy() {
    }

    public static List<UUID> apply(List<UUID> canonicalDedupedIds, CvSourceSnapshot snapshot, JobOffer vacancy) {
        if (canonicalDedupedIds == null || canonicalDedupedIds.isEmpty()) {
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

        List<UUID> eligible = new ArrayList<>();
        for (UUID id : canonicalDedupedIds) {
            String name = nameById.get(id);
            String lowercaseName = name == null ? null : name.toLowerCase(Locale.ROOT);
            if (lowercaseName != null && (NORMALLY_INELIGIBLE.contains(lowercaseName) || CONDITIONALLY_ELIGIBLE.contains(lowercaseName))) {
                continue;
            }
            eligible.add(id);
        }

        List<UUID> exceptions = new ArrayList<>();
        for (String term : List.of("Git", "CI/CD", "Jenkins", "GitLab")) {
            UUID factualId = idByLowercaseName.get(term.toLowerCase(Locale.ROOT));
            if (factualId == null || eligible.contains(factualId) || exceptions.contains(factualId)) {
                continue;
            }
            if (VacancyRequirementClassifier.isExplicitlyRequired(vacancy, term)) {
                exceptions.add(factualId);
            }
        }

        if (exceptions.isEmpty()) {
            return List.copyOf(eligible);
        }
        List<UUID> result = new ArrayList<>(exceptions);
        result.addAll(eligible);
        return List.copyOf(result);
    }

    private static Set<String> lowercaseSet(String... names) {
        Set<String> set = new java.util.HashSet<>();
        for (String name : names) {
            set.add(name.toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(set);
    }
}
