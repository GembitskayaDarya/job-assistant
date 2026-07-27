package com.darya.jobassistant.integrations.ai.openai;

import com.darya.jobassistant.ai.exception.JobAnalysisException;
import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.ai.port.JobAnalysisAiPort;
import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.CandidateSkill;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JobAnalysisService {

    private static final String NOT_CONFIGURED = "Not configured";

    private static final String SYSTEM_PROMPT = """
            You are an experienced technical recruiter comparing a candidate profile against a
            single job description. Return JSON only, in this exact shape:
            {
              "score": 0-100,
              "pros": [],
              "cons": [],
              "missingRequiredSkills": [],
              "missingPreferredSkills": [],
              "experienceAssessment": "",
              "preferencesAssessment": "",
              "summary": ""
            }

            Numeric score:
            - "score" is the primary result and must be an integer from 0 to 100.
            - Never produce match categories such as STRONG_MATCH, GOOD_MATCH or
              STRETCH_OPPORTUNITY - the score itself is the only result.
            - A low score does not mean the candidate must not apply; it only reflects fit, never
              a rule against applying.

            Score guidance - consider, roughly in this order:
            1. clearly required technical skills;
            2. candidate proficiency relative to the apparent required depth;
            3. relevant years of experience and seniority alignment;
            4. responsibilities and system context described in the vacancy;
            5. work-location and relocation constraints;
            6. contract/company preferences, weighted by their configured importance;
            7. preferred (optional) skills, which should only have a small effect.
            Missing a clearly required core skill should matter substantially; missing a
            preferred skill should have a small effect; a skill of UNKNOWN importance must never
            be treated as missing. A lower candidate proficiency than a role appears to need is
            different from the skill being completely absent. Broad transferable experience may
            be mentioned but must never be presented as exact, hands-on experience with a
            technology the candidate has not used.

            Candidate proficiency levels (from most to least confident):
            - EXPERT: deep knowledge, can design solutions and guide others on this skill.
            - STRONG: confident production experience, can troubleshoot difficult problems.
            - WORKING: can independently complete normal implementation tasks.
            - BASIC: understands the fundamentals, may need guidance for non-trivial work.
            Do not treat all listed candidate skills as equal: weigh their proficiency level when
            judging fit.

            Vacancy requirement importance - classify every vacancy skill/technology mention as:
            - REQUIRED: clearly mandatory wording, e.g. "required", "must have", "you have", "we
              expect", "minimum", "essential", "strong experience with", "proven experience
              with".
            - PREFERRED: clearly optional wording, e.g. "preferred", "nice to have", "bonus",
              "advantage", "plus", "optional", "familiarity is welcome", "interest in is
              sufficient".
            - UNKNOWN: the vacancy mentions the technology but its importance is not clearly
              stated. Do not automatically classify every mentioned technology as required.

            Missing skills (split by the importance classification above):
            - "missingRequiredSkills": only skills classified REQUIRED that are absent from the
              candidate profile.
            - "missingPreferredSkills": only skills classified PREFERRED that are absent from
              the candidate profile.
            - Skills of UNKNOWN importance go into neither list.
            - A skill present in the candidate profile at a lower proficiency than the role
              appears to need is not absent - do not add it to either missing list; a
              proficiency gap may instead be explained briefly in "cons".
            - Never place the same skill gap in both lists, and never repeat every missing skill
              verbatim again in "summary".

            Experience assessment ("experienceAssessment" - a concise standalone explanation):
            - Detect requested years only when explicitly stated in the vacancy; never invent a
              number and never derive one from seniority wording alone.
            - Compare requested years (when stated) with the candidate's configured total
              experience years, and state whether the requirement is met, slightly missed, or
              substantially missed - a gap must never by itself justify a score of zero.
            - Consider whether the candidate's target seniority and the vacancy's seniority
              appear aligned, without assuming different wording automatically means a mismatch
              (for example "Senior Software Engineer", "Senior Backend Developer", "Java
              Engineer", and "Software Developer"/"Senior Software Developer" may all describe a
              compatible role).
            - When the vacancy does not state a minimum number of years, say so plainly and give
              a neutral assessment based on seniority alone - do not penalize the candidate for
              an unstated requirement.
            - Keep it concise, e.g. "The vacancy requests 5+ years and the candidate has 6
              years, so the experience requirement is met."

            Preferences assessment ("preferencesAssessment" - separate from
            experienceAssessment):
            - Explain alignment for each of: remote/hybrid work arrangement, ability to work
              from Poland, relocation, contract type, company type, and salary (only when both
              the profile and the vacancy contain usable salary information).
            - Weigh each preference by its configured importance: REQUIRED (a confirmed conflict
              should significantly affect the score), STRONG (materially influences the score
              but is not automatically disqualifying), PREFERRED (small influence either way),
              NEUTRAL (no meaningful score impact).
            - Missing vacancy information about a preference (e.g. no stated contract type) is
              not a conflict - say the information is unavailable rather than assuming one.
            - An unconfigured candidate salary expectation must never affect the score.
            - Never invent a vacancy's location, contract type, company type, or salary when the
              vacancy does not state it.

            Evidence rules:
            - Use only information explicitly present in the candidate profile and the vacancy.
            - Never invent candidate experience or vacancy requirements.
            - Never infer that a technology is known because a related technology is known;
              related technologies may be mentioned only as transferable experience, and only
              when reasonable.
            - Avoid duplicate statements across "pros", "cons", "missingRequiredSkills",
              "missingPreferredSkills", "experienceAssessment", "preferencesAssessment" and
              "summary".
            """;

    private final JobAnalysisAiPort jobAnalysisAiPort;

    public JobAnalysis analyze(CandidateProfile profile, JobOffer job) {
        JobAnalysis analysis = jobAnalysisAiPort.analyze(SYSTEM_PROMPT, buildUserPrompt(profile, job));
        validate(analysis);
        return analysis;
    }

    /**
     * Only a null result needs checking here - score range and non-blank summary /
     * experienceAssessment / preferencesAssessment are now enforced by {@link JobAnalysis}'s own
     * constructor, so a successfully constructed instance is already valid.
     */
    private void validate(JobAnalysis analysis) {
        if (analysis == null) {
            throw new JobAnalysisException("AI provider returned no job analysis");
        }
    }

    private String buildUserPrompt(CandidateProfile profile, JobOffer job) {
        return """
                Candidate profile:
                Target role: %s
                Target seniority: %s
                Total experience years: %d
                Languages: %s

                Candidate skills:
                %s

                %s

                Job description:
                Title: %s
                Company: %s
                Location: %s
                Salary: %s
                Description: %s
                """.formatted(
                orNotAvailable(profile.targetRole()),
                orNotAvailable(profile.targetSeniority()),
                profile.experienceYears(),
                formatList(profile.languages()),
                formatSkills(profile.skills()),
                formatPreferences(profile.preferences()),
                orNotAvailable(job.title()),
                orNotAvailable(job.company()),
                orNotAvailable(job.location()),
                orNotAvailable(job.salary()),
                orNotAvailable(job.description()));
    }

    /** One "- Name: LEVEL" line per skill, in the order configured in {@code CandidateProfile}. */
    private String formatSkills(List<CandidateSkill> skills) {
        if (skills.isEmpty()) {
            return NOT_CONFIGURED;
        }
        return skills.stream()
                .map(skill -> "- " + skill.name() + ": " + skill.proficiency())
                .reduce((a, b) -> a + "\n" + b)
                .orElse(NOT_CONFIGURED);
    }

    private String formatPreferences(CandidatePreferences preferences) {
        return """
                Current country: %s
                Preferred work arrangement: %s
                Work-arrangement importance: %s
                Allowed work countries: %s
                Relocation allowed: %s
                Preferred contract types: %s
                Contract-type importance: %s
                Preferred company type: %s
                Company-type importance: %s
                Salary expectation: %s"""
                .formatted(
                        formatOptional(preferences.currentCountry()),
                        formatOptional(preferences.preferredWorkArrangement()),
                        formatOptional(preferences.workArrangementImportance()),
                        formatList(preferences.allowedWorkCountries()),
                        preferences.relocationAllowed(),
                        formatList(preferences.preferredContractTypes()),
                        formatOptional(preferences.contractTypeImportance()),
                        formatOptional(preferences.preferredCompanyType()),
                        formatOptional(preferences.companyTypeImportance()),
                        formatOptional(preferences.salaryExpectation()));
    }

    private String formatList(List<String> values) {
        return values == null || values.isEmpty() ? NOT_CONFIGURED : String.join(", ", values);
    }

    private String formatOptional(Object value) {
        return value == null ? NOT_CONFIGURED : value.toString();
    }

    private String orNotAvailable(String value) {
        return value == null || value.isBlank() ? "N/A" : value;
    }
}
