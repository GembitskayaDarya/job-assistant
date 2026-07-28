package com.darya.jobassistant.jobdiscovery;

import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.CandidateSkill;
import com.darya.jobassistant.candidates.SkillProficiency;
import com.darya.jobassistant.integrations.jobsearch.JobSearchRequest;
import com.darya.jobassistant.jobdiscovery.config.JobSearchQueryPlanningProperties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Deterministically turns the existing {@link CandidateProfile} into a small, broad, ordered set
 * of {@link JobSearchRequest}s. Pure application policy: no network, AI, persistence, or
 * Telegram access, and no dependency on any specific search provider (Firecrawl or otherwise) -
 * see {@code JobSearchPort}/{@code integrations.jobsearch.firecrawl} for where these requests
 * eventually get executed, which is out of scope here.
 *
 * <p>There is deliberately only one implementation and no separate interface: the project's
 * convention (see {@code CLAUDE.md}) is that a port/interface is introduced only at the
 * integrations boundary or once a second real implementation exists, neither of which applies to
 * this single, deterministic rule-based planner.
 *
 * <p>Every token that ends up in a generated query is either one of the two generic title
 * templates this strategy allows ("Backend Engineer", "Software Engineer", and the literal
 * "backend" suffix) or copied verbatim from a {@link CandidateProfile} field - nothing is
 * invented, translated, or geocoded. {@code CandidatePreferences.allowedWorkCountries} is
 * deliberately not used as a search location: it describes countries the candidate is able to
 * work from/for, not an explicit preferred search region. Explicit discovery geography can be
 * modeled as its own concept in a future change.
 */
@Component
@RequiredArgsConstructor
public class JobSearchQueryPlanner {

    private static final String BACKEND_ENGINEER_TITLE = "Backend Engineer";
    private static final String SOFTWARE_ENGINEER_TITLE = "Software Engineer";
    private static final String SKILL_FOCUSED_SUFFIX = "backend";
    private static final String REMOTE_KEYWORD = "remote";
    private static final Set<String> MEANINGLESS_CONTRACT_VALUES =
            Set.of("any", "unknown", "n/a", "na", "none", "unspecified");

    private final JobSearchQueryPlanningProperties properties;

    public List<JobSearchRequest> plan(CandidateProfile candidateProfile) {
        if (candidateProfile == null) {
            throw new JobSearchPlanningException("Candidate profile must not be null");
        }

        List<CandidateSkill> selectedSkills = selectSkills(candidateProfile.skills());
        CandidatePreferences preferences = candidateProfile.preferences();
        String remoteTerm = resolveRemoteTerm(preferences);
        String contractTerm = resolveContractTerm(preferences);

        List<String> candidateQueries = new ArrayList<>();
        candidateQueries.add(buildPrimaryRoleQuery(candidateProfile.targetRole(), remoteTerm));
        candidateQueries.add(buildTitleVariantQuery(
                BACKEND_ENGINEER_TITLE, candidateProfile, selectedSkills, remoteTerm));
        candidateQueries.add(buildTitleVariantQuery(
                SOFTWARE_ENGINEER_TITLE, candidateProfile, selectedSkills, remoteTerm));

        String skillFocusedQuery = buildSkillFocusedQuery(selectedSkills, remoteTerm);
        if (skillFocusedQuery != null) {
            candidateQueries.add(skillFocusedQuery);
        }

        String contractQuery = buildContractQuery(candidateProfile.targetRole(), contractTerm, remoteTerm);
        if (contractQuery != null) {
            candidateQueries.add(contractQuery);
        }

        List<String> distinctQueries = deduplicateCaseInsensitive(candidateQueries);
        List<String> boundedQueries = distinctQueries.size() > properties.maxQueries()
                ? distinctQueries.subList(0, properties.maxQueries())
                : distinctQueries;

        if (boundedQueries.isEmpty()) {
            throw new JobSearchPlanningException("Unable to generate any job search query from the candidate profile");
        }

        return boundedQueries.stream()
                .map(query -> new JobSearchRequest(query, properties.resultsPerQuery()))
                .toList();
    }

    private String buildPrimaryRoleQuery(String targetRole, String remoteTerm) {
        List<String> parts = new ArrayList<>();
        parts.add(targetRole);
        appendIfNewPhrase(parts, remoteTerm);
        return normalize(String.join(" ", parts));
    }

    /**
     * Seniority is composed from this query's own parts (generic title + selected skills +
     * remote), never by inspecting {@code targetRole} - {@code targetRole} is not part of this
     * query at all, so checking it would answer the wrong question. Seniority is prepended
     * unless the rest of this same query already contains it as a standalone, case-insensitive
     * word/phrase.
     */
    private String buildTitleVariantQuery(String genericTitle, CandidateProfile candidateProfile,
                                           List<CandidateSkill> selectedSkills, String remoteTerm) {
        List<String> parts = new ArrayList<>();
        parts.add(genericTitle);
        selectedSkills.forEach(skill -> parts.add(skill.name()));
        appendIfNewPhrase(parts, remoteTerm);

        String seniority = candidateProfile.targetSeniority();
        if (seniority != null && !seniority.isBlank()
                && !containsWordIgnoreCase(String.join(" ", parts), seniority)) {
            parts.add(0, seniority);
        }

        return normalize(String.join(" ", parts));
    }

    private String buildSkillFocusedQuery(List<CandidateSkill> selectedSkills, String remoteTerm) {
        if (selectedSkills.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        selectedSkills.forEach(skill -> parts.add(skill.name()));
        parts.add(SKILL_FOCUSED_SUFFIX);
        appendIfNewPhrase(parts, remoteTerm);
        return normalize(String.join(" ", parts));
    }

    private String buildContractQuery(String targetRole, String contractTerm, String remoteTerm) {
        if (contractTerm == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        parts.add(targetRole);
        appendIfNewPhrase(parts, contractTerm);
        appendIfNewPhrase(parts, remoteTerm);
        return normalize(String.join(" ", parts));
    }

    /**
     * Selects up to {@code maxSkillsPerQuery} skills, strongest first. {@code BASIC}-level skills
     * are excluded unless the candidate has no skill at {@code WORKING}/{@code STRONG}/{@code
     * EXPERT} at all - there is no {@code NONE} level in {@link SkillProficiency}, so an absent
     * skill is already excluded by construction. The current {@link CandidateSkill} model has no
     * category metadata, so ordering is proficiency (descending) then the profile's own original
     * order for ties - never a hardcoded technology preference.
     */
    private List<CandidateSkill> selectSkills(List<CandidateSkill> skills) {
        List<CandidateSkill> usable = skills.stream()
                .filter(skill -> skill.proficiency() != SkillProficiency.BASIC)
                .toList();
        List<CandidateSkill> pool = usable.isEmpty() ? skills : usable;

        List<CandidateSkill> sorted = pool.stream()
                .sorted(Comparator.comparingInt((CandidateSkill skill) -> skill.proficiency().ordinal()).reversed())
                .toList();

        List<CandidateSkill> deduplicated = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();
        for (CandidateSkill skill : sorted) {
            if (seenNames.add(skill.name().toLowerCase(Locale.ROOT))) {
                deduplicated.add(skill);
            }
        }

        int limit = Math.min(properties.maxSkillsPerQuery(), deduplicated.size());
        return deduplicated.subList(0, limit);
    }

    /**
     * "Remote" is only ever added when {@code preferredWorkArrangement} explicitly, exactly means
     * remote work - the candidate's own text is reused verbatim (not a hardcoded literal), so
     * whatever capitalization they configured is what appears in the query.
     */
    private String resolveRemoteTerm(CandidatePreferences preferences) {
        String arrangement = preferences.preferredWorkArrangement();
        if (arrangement == null) {
            return null;
        }
        String trimmed = arrangement.trim();
        return trimmed.equalsIgnoreCase(REMOTE_KEYWORD) ? trimmed : null;
    }

    private String resolveContractTerm(CandidatePreferences preferences) {
        for (String contractType : preferences.preferredContractTypes()) {
            if (contractType == null) {
                continue;
            }
            String trimmed = contractType.trim();
            if (trimmed.isEmpty() || MEANINGLESS_CONTRACT_VALUES.contains(trimmed.toLowerCase(Locale.ROOT))) {
                continue;
            }
            return trimmed;
        }
        return null;
    }

    /**
     * Appends an optional planner-added phrase (remote, seniority, contract) only if it is not
     * already present, as a standalone case-insensitive phrase, in the parts accumulated so far
     * for this specific query. This is how duplication of planner-added terms is prevented -
     * proactively, at composition time - rather than by deleting tokens from an already-composed
     * query afterwards.
     */
    private void appendIfNewPhrase(List<String> parts, String phrase) {
        if (phrase == null || phrase.isBlank()) {
            return;
        }
        if (containsWordIgnoreCase(String.join(" ", parts), phrase)) {
            return;
        }
        parts.add(phrase);
    }

    private boolean containsWordIgnoreCase(String haystack, String word) {
        if (haystack == null || word == null || word.isBlank()) {
            return false;
        }
        return Pattern.compile("(?i)\\b" + Pattern.quote(word.trim()) + "\\b").matcher(haystack).find();
    }

    /** Trims leading/trailing whitespace and collapses repeated internal whitespace - nothing else. */
    private String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().replaceAll("\\s+", " ");
    }

    /** Case-insensitive whole-query dedup preserving first occurrence and priority order. */
    private List<String> deduplicateCaseInsensitive(List<String> queries) {
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String query : queries) {
            if (query == null || query.isBlank()) {
                continue;
            }
            if (seen.add(query.toLowerCase(Locale.ROOT))) {
                result.add(query);
            }
        }
        return result;
    }
}
