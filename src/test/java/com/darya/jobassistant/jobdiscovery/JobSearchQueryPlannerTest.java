package com.darya.jobassistant.jobdiscovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.CandidateSkill;
import com.darya.jobassistant.candidates.PreferenceImportance;
import com.darya.jobassistant.candidates.SkillProficiency;
import com.darya.jobassistant.integrations.jobsearch.JobSearchRequest;
import com.darya.jobassistant.jobdiscovery.config.JobSearchQueryPlanningProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * No Spring context, HTTP client, Firecrawl adapter, AI component, repository, or Telegram
 * component is instantiated anywhere in this class - {@link JobSearchQueryPlanner} is exercised
 * as a plain object against the real {@link CandidateProfile} model.
 */
class JobSearchQueryPlannerTest {

    private static final JobSearchQueryPlanningProperties DEFAULT_PROPERTIES =
            new JobSearchQueryPlanningProperties(5, 10, 3);

    @Test
    void fullProfile_producesOrderedDeterministicPlan() {
        CandidateProfile profile = fullProfile();
        JobSearchQueryPlanner planner = new JobSearchQueryPlanner(DEFAULT_PROPERTIES);

        List<JobSearchRequest> plan = planner.plan(profile);

        assertThat(plan).extracting(JobSearchRequest::query).containsExactly(
                "Senior Java Backend Developer Remote",
                "Senior Backend Engineer PostgreSQL Java Kafka Remote",
                "Senior Software Engineer PostgreSQL Java Kafka Remote",
                "PostgreSQL Java Kafka backend Remote",
                "Senior Java Backend Developer B2B Remote");
    }

    @Test
    void primaryQuery_containsTargetRole() {
        CandidateProfile profile = fullProfile();
        JobSearchQueryPlanner planner = new JobSearchQueryPlanner(DEFAULT_PROPERTIES);

        List<JobSearchRequest> plan = planner.plan(profile);

        assertThat(plan.get(0).query()).contains(profile.targetRole());
    }

    @Test
    void remoteTerm_includedOnlyWhenExplicitlyPreferred() {
        CandidateProfile remoteProfile = fullProfile();
        CandidateProfile nonRemoteProfile = withPreferences(fullProfile(),
                preferences(null, "Hybrid", List.of("Poland"), List.of("B2B")));

        JobSearchQueryPlanner planner = new JobSearchQueryPlanner(DEFAULT_PROPERTIES);

        assertThat(planner.plan(remoteProfile).get(0).query()).contains("Remote");
        assertThat(planner.plan(nonRemoteProfile).get(0).query()).doesNotContain("Remote").doesNotContain("Hybrid");
    }

    @Test
    void allowedWorkCountries_notIncludedInAnyQuery() {
        CandidateProfile profile = fullProfile();
        JobSearchQueryPlanner planner = new JobSearchQueryPlanner(DEFAULT_PROPERTIES);

        List<String> queries = planner.plan(profile).stream().map(JobSearchRequest::query).toList();

        assertThat(queries).noneMatch(query -> query.contains("Poland"));
    }

    @Test
    void multipleAllowedWorkCountries_doNotAffectOutput_regardlessOfOrder() {
        JobSearchQueryPlanner planner = new JobSearchQueryPlanner(DEFAULT_PROPERTIES);
        CandidateProfile noCountries = withPreferences(fullProfile(),
                preferences(null, "Remote", List.of(), List.of("B2B")));
        CandidateProfile onePolandFirst = withPreferences(fullProfile(),
                preferences(null, "Remote", List.of("Poland", "Germany"), List.of("B2B")));
        CandidateProfile oneGermanyFirst = withPreferences(fullProfile(),
                preferences(null, "Remote", List.of("Germany", "Poland"), List.of("B2B")));

        List<String> noCountriesQueries = planner.plan(noCountries).stream().map(JobSearchRequest::query).toList();
        List<String> polandFirstQueries = planner.plan(onePolandFirst).stream().map(JobSearchRequest::query).toList();
        List<String> germanyFirstQueries = planner.plan(oneGermanyFirst).stream().map(JobSearchRequest::query).toList();

        assertThat(polandFirstQueries).isEqualTo(noCountriesQueries);
        assertThat(germanyFirstQueries).isEqualTo(noCountriesQueries);
    }

    @Test
    void strongestUsableSkills_selectedFirst() {
        CandidateProfile profile = fullProfile();
        JobSearchQueryPlanner planner = new JobSearchQueryPlanner(DEFAULT_PROPERTIES);

        String skillFocusedQuery = planner.plan(profile).get(3).query();

        assertThat(skillFocusedQuery).startsWith("PostgreSQL Java Kafka backend");
    }

    @Test
    void noMoreThanMaxSkillsPerQuery_included() {
        JobSearchQueryPlanningProperties properties = new JobSearchQueryPlanningProperties(5, 10, 1);
        CandidateProfile profile = fullProfile();
        JobSearchQueryPlanner planner = new JobSearchQueryPlanner(properties);

        String skillFocusedQuery = planner.plan(profile).stream()
                .map(JobSearchRequest::query)
                .filter(query -> query.contains(" backend "))
                .findFirst()
                .orElseThrow();

        assertThat(skillFocusedQuery).isEqualTo("PostgreSQL backend Remote");
    }

    @Test
    void basicLevelSkills_excludedWhenStrongerSkillsExist() {
        CandidateProfile profile = new CandidateProfile(
                "Java Backend Developer", "Senior",
                List.of(new CandidateSkill("Java", SkillProficiency.STRONG, null),
                        new CandidateSkill("Docker", SkillProficiency.BASIC, null)),
                List.of("English"), 6,
                preferences(null, null, List.of(), List.of()));
        JobSearchQueryPlanner planner = new JobSearchQueryPlanner(DEFAULT_PROPERTIES);

        String skillFocusedQuery = planner.plan(profile).stream()
                .map(JobSearchRequest::query)
                .filter(query -> query.contains("backend"))
                .findFirst()
                .orElseThrow();

        assertThat(skillFocusedQuery).doesNotContain("Docker").contains("Java");
    }

    @Test
    void basicLevelSkills_usedWhenNoStrongerSkillExists() {
        CandidateProfile profile = new CandidateProfile(
                "Java Backend Developer", "Senior",
                List.of(new CandidateSkill("Docker", SkillProficiency.BASIC, null)),
                List.of("English"), 6,
                preferences(null, null, List.of(), List.of()));
        JobSearchQueryPlanner planner = new JobSearchQueryPlanner(DEFAULT_PROPERTIES);

        String skillFocusedQuery = planner.plan(profile).stream()
                .map(JobSearchRequest::query)
                .filter(query -> query.contains("backend"))
                .findFirst()
                .orElseThrow();

        assertThat(skillFocusedQuery).contains("Docker");
    }

    @Test
    void duplicateSkillNames_removedCaseInsensitively() {
        CandidateProfile profile = new CandidateProfile(
                "Java Backend Developer", "Senior",
                List.of(new CandidateSkill("Java", SkillProficiency.EXPERT, null),
                        new CandidateSkill("java", SkillProficiency.STRONG, null),
                        new CandidateSkill("Kafka", SkillProficiency.STRONG, null)),
                List.of("English"), 6,
                preferences(null, null, List.of(), List.of()));
        JobSearchQueryPlanner planner = new JobSearchQueryPlanner(DEFAULT_PROPERTIES);

        String skillFocusedQuery = planner.plan(profile).stream()
                .map(JobSearchRequest::query)
                .filter(query -> query.contains("backend"))
                .findFirst()
                .orElseThrow();

        assertThat(skillFocusedQuery).isEqualTo("Java Kafka backend");
    }

    @Test
    void titleVariants_preserveCandidateSeniority_evenWhenTargetRoleAlreadyContainsIt() {
        CandidateProfile profile = fullProfile();
        JobSearchQueryPlanner planner = new JobSearchQueryPlanner(DEFAULT_PROPERTIES);

        List<String> queries = planner.plan(profile).stream().map(JobSearchRequest::query).toList();

        assertThat(queries).anyMatch(query -> query.startsWith("Senior Backend Engineer"));
        assertThat(queries).anyMatch(query -> query.startsWith("Senior Software Engineer"));
    }

    @Test
    void seniority_notRepeatedWhenComposedQueryAlreadyContainsIt() {
        // targetRole intentionally does NOT contain "Backend" - proves the check runs against
        // each variant's own composed text, not against targetRole.
        CandidateProfile profile = new CandidateProfile(
                "Java Developer", "Backend",
                List.of(), List.of("English"), 6,
                preferences(null, null, List.of(), List.of()));
        JobSearchQueryPlanner planner = new JobSearchQueryPlanner(DEFAULT_PROPERTIES);

        List<String> queries = planner.plan(profile).stream().map(JobSearchRequest::query).toList();

        assertThat(queries).contains("Backend Engineer");
        assertThat(queries).contains("Backend Software Engineer");
    }

    @Test
    void primaryQuery_doesNotDuplicateRemote_whenTargetRoleAlreadyContainsIt() {
        CandidateProfile profile = new CandidateProfile(
                "Remote Java Backend Developer", "Senior",
                List.of(), List.of("English"), 6,
                preferences(null, "Remote", List.of(), List.of()));
        JobSearchQueryPlanner planner = new JobSearchQueryPlanner(DEFAULT_PROPERTIES);

        String primaryQuery = planner.plan(profile).get(0).query();

        assertThat(primaryQuery).isEqualTo("Remote Java Backend Developer");
    }

    @Test
    void titleVariants_notGeneratedWhenTheyDuplicateAnExistingQuery() {
        // Contrived but valid: targetRole equals the generic title, and targetSeniority is a
        // real word already contained in that generic title, so the Backend Engineer variant
        // collapses to exactly the primary query and is dropped by whole-query dedup.
        CandidateProfile profile = new CandidateProfile(
                "Backend Engineer", "Backend",
                List.of(), List.of("English"), 6,
                preferences(null, null, List.of(), List.of()));
        JobSearchQueryPlanner planner = new JobSearchQueryPlanner(DEFAULT_PROPERTIES);

        List<String> queries = planner.plan(profile).stream().map(JobSearchRequest::query).toList();

        assertThat(queries).containsExactly("Backend Engineer", "Backend Software Engineer");
    }

    @Test
    void contractPreference_producesSeparateQuery_notAddedToEveryQuery() {
        CandidateProfile profile = fullProfile();
        JobSearchQueryPlanner planner = new JobSearchQueryPlanner(DEFAULT_PROPERTIES);

        List<String> queries = planner.plan(profile).stream().map(JobSearchRequest::query).toList();

        assertThat(queries).filteredOn(query -> query.contains("B2B")).hasSize(1);
        assertThat(queries.get(4)).contains("B2B");
    }

    @Test
    void meaninglessContractValues_doNotProduceContractQuery() {
        CandidateProfile profile = withPreferences(fullProfile(),
                preferences(null, "Remote", List.of("Poland"), List.of("any")));
        JobSearchQueryPlanner planner = new JobSearchQueryPlanner(DEFAULT_PROPERTIES);

        List<String> queries = planner.plan(profile).stream().map(JobSearchRequest::query).toList();

        assertThat(queries).noneMatch(query -> query.toLowerCase().contains("any"));
    }

    @Test
    void missingOptionalPreferences_doNotCauseFailure() {
        CandidateProfile profile = new CandidateProfile(
                "Java Backend Developer", "Senior",
                List.of(new CandidateSkill("Java", SkillProficiency.STRONG, null)),
                List.of("English"), 6,
                new CandidatePreferences(null, null, null, List.of(), false, List.of(), null, null, null, null));
        JobSearchQueryPlanner planner = new JobSearchQueryPlanner(DEFAULT_PROPERTIES);

        List<JobSearchRequest> plan = planner.plan(profile);

        assertThat(plan).isNotEmpty();
    }

    @Test
    void missingSkills_stillAllowsRoleBasedQuery() {
        CandidateProfile profile = new CandidateProfile(
                "Java Backend Developer", "Senior",
                List.of(), List.of("English"), 6,
                preferences(null, null, List.of(), List.of()));
        JobSearchQueryPlanner planner = new JobSearchQueryPlanner(DEFAULT_PROPERTIES);

        List<String> queries = planner.plan(profile).stream().map(JobSearchRequest::query).toList();

        assertThat(queries).contains("Java Backend Developer");
        assertThat(queries).noneMatch(query -> query.contains("backend") && !query.startsWith("Java"));
    }

    @Test
    void queries_normalizedForWhitespace() {
        CandidateProfile profile = new CandidateProfile(
                "  Senior  Java   Backend Developer  ", "Senior",
                List.of(), List.of("English"), 6,
                preferences(null, null, List.of(), List.of()));
        JobSearchQueryPlanner planner = new JobSearchQueryPlanner(DEFAULT_PROPERTIES);

        String primaryQuery = planner.plan(profile).get(0).query();

        assertThat(primaryQuery).isEqualTo("Senior Java Backend Developer");
    }

    @Test
    void normalization_preservesLegitimateRepeatedWords() {
        // "Java Java" is a contrived-but-legal repeated word coming straight from targetRole;
        // normalization must only fix whitespace, never delete a repeated word from real input.
        CandidateProfile profile = new CandidateProfile(
                "Java  Java   Developer", "Senior",
                List.of(), List.of("English"), 6,
                preferences(null, null, List.of(), List.of()));
        JobSearchQueryPlanner planner = new JobSearchQueryPlanner(DEFAULT_PROPERTIES);

        String primaryQuery = planner.plan(profile).get(0).query();

        assertThat(primaryQuery).isEqualTo("Java Java Developer");
    }

    @Test
    void duplicateQueries_removedCaseInsensitively_preservingFirstOccurrenceAndOrder() {
        CandidateProfile profile = new CandidateProfile(
                "Backend Engineer", "Backend",
                List.of(), List.of("English"), 6,
                preferences(null, null, List.of(), List.of()));
        JobSearchQueryPlanner planner = new JobSearchQueryPlanner(DEFAULT_PROPERTIES);

        List<String> queries = planner.plan(profile).stream().map(JobSearchRequest::query).toList();

        assertThat(queries).containsExactly("Backend Engineer", "Backend Software Engineer");
    }

    @Test
    void maxQueries_appliedAfterDeduplication() {
        JobSearchQueryPlanningProperties properties = new JobSearchQueryPlanningProperties(2, 10, 3);
        CandidateProfile profile = new CandidateProfile(
                "Backend Engineer", "Backend",
                List.of(), List.of("English"), 6,
                preferences(null, null, List.of(), List.of()));
        JobSearchQueryPlanner planner = new JobSearchQueryPlanner(properties);

        List<String> queries = planner.plan(profile).stream().map(JobSearchRequest::query).toList();

        assertThat(queries).containsExactly("Backend Engineer", "Backend Software Engineer");
    }

    @Test
    void everyRequest_usesConfiguredResultsPerQuery() {
        JobSearchQueryPlanningProperties properties = new JobSearchQueryPlanningProperties(5, 42, 3);
        JobSearchQueryPlanner planner = new JobSearchQueryPlanner(properties);

        List<JobSearchRequest> plan = planner.plan(fullProfile());

        assertThat(plan).allSatisfy(request -> assertThat(request.maxResults()).isEqualTo(42));
    }

    @Test
    void returnedList_isImmutable() {
        JobSearchQueryPlanner planner = new JobSearchQueryPlanner(DEFAULT_PROPERTIES);

        List<JobSearchRequest> plan = planner.plan(fullProfile());

        assertThatThrownBy(() -> plan.add(null)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void repeatedInvocations_withSameProfile_returnEqualResults() {
        JobSearchQueryPlanner planner = new JobSearchQueryPlanner(DEFAULT_PROPERTIES);
        CandidateProfile profile = fullProfile();

        List<JobSearchRequest> first = planner.plan(profile);
        List<JobSearchRequest> second = planner.plan(profile);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void nullProfile_throwsJobSearchPlanningException() {
        JobSearchQueryPlanner planner = new JobSearchQueryPlanner(DEFAULT_PROPERTIES);

        assertThatThrownBy(() -> planner.plan(null)).isInstanceOf(JobSearchPlanningException.class);
    }

    private CandidateProfile fullProfile() {
        return new CandidateProfile(
                "Senior Java Backend Developer",
                "Senior",
                List.of(
                        new CandidateSkill("Java", SkillProficiency.STRONG, null),
                        new CandidateSkill("Spring Boot", SkillProficiency.WORKING, null),
                        new CandidateSkill("Docker", SkillProficiency.BASIC, null),
                        new CandidateSkill("PostgreSQL", SkillProficiency.EXPERT, null),
                        new CandidateSkill("Kafka", SkillProficiency.STRONG, null)),
                List.of("English", "Polish"),
                6,
                preferences(null, "Remote", List.of("Poland"), List.of("B2B")));
    }

    private CandidateProfile withPreferences(CandidateProfile profile, CandidatePreferences preferences) {
        return new CandidateProfile(
                profile.targetRole(), profile.targetSeniority(), profile.skills(),
                profile.languages(), profile.experienceYears(), preferences);
    }

    private CandidatePreferences preferences(String currentCountry, String preferredWorkArrangement,
                                              List<String> allowedWorkCountries, List<String> preferredContractTypes) {
        return new CandidatePreferences(
                currentCountry, preferredWorkArrangement, PreferenceImportance.STRONG,
                allowedWorkCountries, false, preferredContractTypes, PreferenceImportance.PREFERRED,
                null, null, null);
    }
}
