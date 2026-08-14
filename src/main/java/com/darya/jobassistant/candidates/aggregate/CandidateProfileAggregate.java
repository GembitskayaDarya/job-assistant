package com.darya.jobassistant.candidates.aggregate;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Framework-free domain model for the Candidate Profile business aggregate backed by Sprint 9
 * Step 1's PostgreSQL schema (parent {@code candidate_profile} row plus its {@code
 * candidate_profile_skill}, {@code candidate_profile_language}, and - since Step 3 -
 * {@code candidate_profile_preference} rows) - the type {@link CandidateProfileRepositoryPort}
 * loads and saves.
 *
 * <h2>Intended architectural role</h2>
 *
 * <p>This is the intended <b>future source-of-truth</b> business aggregate for Candidate Profile
 * data, not an infrastructure DTO - persistence is an implementation detail of what this type
 * represents, not its purpose. It lives in its own {@code candidates.aggregate} package,
 * deliberately separate from the flat top-level {@code candidates} package, to make that
 * distinction visible in the code layout itself:
 *
 * <pre>{@code
 * CandidateProfileAggregate
 *     = future PostgreSQL-backed source-of-truth domain aggregate (this type)
 *
 * com.darya.jobassistant.candidates.CandidateProfile
 *     = current analysis-oriented projection JobAnalysisService actually reads from today,
 *       sourced from config/candidate-profile.yml via ConfigurationCandidateProfileProvider
 *
 * Sprint 9 Step 3 built both halves of the round trip:
 *     CandidateProfileYamlImportMapper : CandidateProfile (YAML)   -> CandidateProfileAggregate
 *     CandidateProfileAnalysisAssembler: CandidateProfileAggregate -> CandidateProfile
 * }</pre>
 *
 * <h2>{@link #preferences} versus the flat {@link #preferredCompanyType}/{@link #remotePolicy}</h2>
 *
 * {@link #preferences} (added Step 3) is the lossless, importance-aware representation of {@code
 * CandidatePreferences.preferredCompanyType}/{@code preferredWorkArrangement} (via {@link
 * CandidatePreferenceType#COMPANY_TYPE}/{@link CandidatePreferenceType#WORK_ARRANGEMENT}) - the
 * flat {@link #preferredCompanyType}/{@link #remotePolicy} columns Step 1 defined cannot carry an
 * importance at all, so they are never populated by the YAML import mapper once the corresponding
 * preference exists. To guarantee these two representations can never disagree (rather than just
 * documenting that they should not), this constructor rejects an aggregate that sets both: a
 * {@code COMPANY_TYPE}/{@code WORK_ARRANGEMENT} preference and a non-null {@link
 * #preferredCompanyType}/{@link #remotePolicy} are mutually exclusive. {@link #preferredLocation}
 * and {@link #employmentModel} remain unpopulated by the migration for a simpler reason: the YAML
 * model has no single "preferred location" string, and {@code preferredContractTypes} is a list
 * where {@link #employmentModel} is singular - there is no lossless value to assign either flat
 * field from, so the migration mapper leaves both null rather than guessing.
 *
 * <p>{@link #id} is {@code null} for a profile not yet persisted; {@link #version} is the
 * optimistic-locking token a caller must supply unchanged from a prior load to update that exact
 * revision - see {@link CandidateProfileRepositoryPort#save}. Every save of an existing aggregate,
 * including one that only changes {@link #skills}, {@link #languages}, {@link #preferences}, or
 * {@link #education}, performs a version-checked write of the whole aggregate and increments
 * {@link #version} - all four are parts of this aggregate, not independently versioned
 * sub-resources. {@link #education} joins that set as of Sprint 11 Step 5 - fixed factual data,
 * flat like {@link #skills}/{@link #languages} (no nested children), so it needs no separate
 * aggregate the way Career History or Personal Projects do.
 *
 * <h2>CV header/contact facts (Sprint 11 Step 5)</h2>
 *
 * {@link #email}, {@link #phone}, {@link #linkedinUrl}, {@link #cvLocation}, and {@link
 * #cvHeadline} are fixed factual CV-presentation data - never AI-generated. {@link #cvHeadline}
 * is deliberately a separate fact from {@link #targetRole}: {@link #targetRole} is an operational
 * job-search/vacancy-matching input (fed into the AI analysis prompt and into outbound job-search
 * queries), while {@link #cvHeadline} is the fixed title printed on the CV document - the two
 * must be free to diverge and are not kept in sync.
 */
public record CandidateProfileAggregate(
        UUID id,
        String profileKey,
        String targetRole,
        String seniority,
        int experienceYears,
        String preferredCompanyType,
        String preferredLocation,
        String employmentModel,
        String remotePolicy,
        String salaryCurrency,
        BigDecimal minimumSalary,
        String currentCountry,
        boolean relocationAllowed,
        String salaryExpectationNote,
        String email,
        String phone,
        String linkedinUrl,
        String cvLocation,
        String cvHeadline,
        List<CandidateSkill> skills,
        List<CandidateLanguage> languages,
        List<CandidateProfilePreference> preferences,
        List<CandidateEducation> education,
        long version
) {
    /** Mirrors V26's {@code chk_candidate_profile_email_format}. */
    private static final Pattern EMAIL_FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /** Mirrors V26's {@code chk_candidate_profile_linkedin_url_format}. */
    private static final Pattern LINKEDIN_URL_FORMAT =
            Pattern.compile("^https://([a-z0-9-]+\\.)*linkedin\\.com/.+$", Pattern.CASE_INSENSITIVE);

    public CandidateProfileAggregate {
        profileKey = profileKey == null ? null : profileKey.trim();
        if (profileKey == null || profileKey.isEmpty()) {
            throw new IllegalArgumentException("Candidate profile key must not be null or blank");
        }
        targetRole = targetRole == null ? null : targetRole.trim();
        if (targetRole == null || targetRole.isEmpty()) {
            throw new IllegalArgumentException("Candidate target role must not be null or blank");
        }
        if (experienceYears < 0) {
            throw new IllegalArgumentException("Candidate experience years must not be negative");
        }
        if (minimumSalary != null && minimumSalary.signum() < 0) {
            throw new IllegalArgumentException("Candidate minimum salary must not be negative");
        }
        if (version < 0) {
            throw new IllegalArgumentException("Candidate profile version must not be negative");
        }
        email = trimToNull(email);
        if (email != null && !EMAIL_FORMAT.matcher(email).matches()) {
            throw new IllegalArgumentException("Candidate profile email is not a valid email address: " + email);
        }
        phone = trimToNull(phone);
        linkedinUrl = trimToNull(linkedinUrl);
        if (linkedinUrl != null && !LINKEDIN_URL_FORMAT.matcher(linkedinUrl).matches()) {
            throw new IllegalArgumentException("Candidate profile LinkedIn URL is not a valid LinkedIn URL: " + linkedinUrl);
        }
        cvLocation = trimToNull(cvLocation);
        cvHeadline = trimToNull(cvHeadline);
        skills = skills == null ? List.of() : List.copyOf(skills);
        languages = copySortedByLanguageDisplayOrder(languages);
        preferences = preferences == null ? List.of() : List.copyOf(preferences);
        education = copySortedByDisplayOrder(education);
        requireUniqueSkillNames(skills);
        requireUniqueLanguageCodes(languages);
        requireUniqueLanguageDisplayOrders(languages);
        requireValidPreferences(preferences);
        requireUniqueEducationDisplayOrders(education);
        if (preferredCompanyType != null && hasPreferenceOfType(preferences, CandidatePreferenceType.COMPANY_TYPE)) {
            throw new IllegalArgumentException(
                    "Candidate profile must not set both preferredCompanyType and a COMPANY_TYPE preference");
        }
        if (remotePolicy != null && hasPreferenceOfType(preferences, CandidatePreferenceType.WORK_ARRANGEMENT)) {
            throw new IllegalArgumentException(
                    "Candidate profile must not set both remotePolicy and a WORK_ARRANGEMENT preference");
        }
    }

    /**
     * Convenience constructor matching this aggregate's pre-Sprint-11-Step-5 shape - defaults the
     * new CV header/contact facts to {@code null} and {@link #education} to empty, so the many
     * existing call sites built before those facts existed keep compiling unchanged. Every
     * production caller that actually has header/education data uses the canonical constructor
     * above instead.
     */
    public CandidateProfileAggregate(
            UUID id, String profileKey, String targetRole, String seniority, int experienceYears,
            String preferredCompanyType, String preferredLocation, String employmentModel, String remotePolicy,
            String salaryCurrency, BigDecimal minimumSalary, String currentCountry, boolean relocationAllowed,
            String salaryExpectationNote, List<CandidateSkill> skills, List<CandidateLanguage> languages,
            List<CandidateProfilePreference> preferences, long version) {
        this(id, profileKey, targetRole, seniority, experienceYears, preferredCompanyType, preferredLocation,
                employmentModel, remotePolicy, salaryCurrency, minimumSalary, currentCountry, relocationAllowed,
                salaryExpectationNote, null, null, null, null, null, skills, languages, preferences, List.of(), version);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static List<CandidateEducation> copySortedByDisplayOrder(List<CandidateEducation> education) {
        if (education == null) {
            return List.of();
        }
        return education.stream()
                .sorted(Comparator.comparingInt(CandidateEducation::displayOrder))
                .toList();
    }

    private static void requireUniqueEducationDisplayOrders(List<CandidateEducation> education) {
        Set<Integer> seen = new HashSet<>();
        for (CandidateEducation entry : education) {
            if (!seen.add(entry.displayOrder())) {
                throw new IllegalArgumentException("Duplicate education display order in candidate profile: " + entry.displayOrder());
            }
        }
    }

    private static void requireUniqueSkillNames(List<CandidateSkill> skills) {
        Set<String> seen = new LinkedHashSet<>();
        for (CandidateSkill skill : skills) {
            if (!seen.add(skill.name())) {
                throw new IllegalArgumentException("Duplicate skill name in candidate profile: " + skill.name());
            }
        }
    }

    private static void requireUniqueLanguageCodes(List<CandidateLanguage> languages) {
        Set<String> seen = new LinkedHashSet<>();
        for (CandidateLanguage language : languages) {
            if (!seen.add(language.languageCode())) {
                throw new IllegalArgumentException("Duplicate language code in candidate profile: " + language.languageCode());
            }
        }
    }

    private static List<CandidateLanguage> copySortedByLanguageDisplayOrder(List<CandidateLanguage> languages) {
        if (languages == null) {
            return List.of();
        }
        return languages.stream()
                .sorted(Comparator.comparingInt(CandidateLanguage::displayOrder))
                .toList();
    }

    /**
     * Sprint 11 Step 5 acceptance correction: {@code displayOrder} is now a real, order-significant
     * CV presentation fact (see {@link CandidateLanguage}'s javadoc) - two languages silently
     * sharing one position in the profile is an invalid aggregate, caught here, before any
     * persistence attempt, exactly like {@link #requireUniqueEducationDisplayOrders}. The
     * database's {@code uk_candidate_profile_language_profile_id_display_order} constraint remains
     * defense-in-depth, not the primary place this is discovered.
     */
    private static void requireUniqueLanguageDisplayOrders(List<CandidateLanguage> languages) {
        Set<Integer> seen = new HashSet<>();
        for (CandidateLanguage language : languages) {
            if (!seen.add(language.displayOrder())) {
                throw new IllegalArgumentException("Duplicate language display order in candidate profile: " + language.displayOrder());
            }
        }
    }

    /**
     * Mirrors {@code uk_candidate_profile_preference_profile_type_value} (no duplicate
     * type+value pair) and additionally enforces, at the domain level, that {@link
     * CandidatePreferenceType#WORK_ARRANGEMENT} and {@link CandidatePreferenceType#COMPANY_TYPE}
     * - single-valued concepts in the source YAML model - never appear more than once each; the
     * database only constrains their value/type uniqueness, not their cardinality, since it has
     * no way to express "at most one row of this specific type" short of a partial unique index
     * this step does not need for the other two (multi-valued) types.
     *
     * <p>Additionally mirrors {@code uk_candidate_profile_preference_profile_type_priority} (V18):
     * within one order-significant {@code type}, no two rows may share the same {@code
     * priorityOrder} - two preferences cannot both claim the same position in the source list.
     */
    private static void requireValidPreferences(List<CandidateProfilePreference> preferences) {
        Set<String> seenTypeValue = new HashSet<>();
        Set<CandidatePreferenceType> singleValuedTypesSeen = new HashSet<>();
        Set<String> seenTypePriority = new HashSet<>();
        for (CandidateProfilePreference preference : preferences) {
            String key = preference.type() + " " + preference.value();
            if (!seenTypeValue.add(key)) {
                throw new IllegalArgumentException(
                        "Duplicate candidate profile preference: " + preference.type() + "=" + preference.value());
            }
            boolean singleValued = preference.type() == CandidatePreferenceType.WORK_ARRANGEMENT
                    || preference.type() == CandidatePreferenceType.COMPANY_TYPE;
            if (singleValued && !singleValuedTypesSeen.add(preference.type())) {
                throw new IllegalArgumentException(
                        "Candidate profile must not have more than one " + preference.type() + " preference");
            }
            if (preference.priorityOrder() != null) {
                String priorityKey = preference.type() + " " + preference.priorityOrder();
                if (!seenTypePriority.add(priorityKey)) {
                    throw new IllegalArgumentException(
                            "Duplicate priorityOrder " + preference.priorityOrder() + " for candidate profile preference type "
                                    + preference.type());
                }
            }
        }
    }

    private static boolean hasPreferenceOfType(List<CandidateProfilePreference> preferences, CandidatePreferenceType type) {
        return preferences.stream().anyMatch(preference -> preference.type() == type);
    }
}
