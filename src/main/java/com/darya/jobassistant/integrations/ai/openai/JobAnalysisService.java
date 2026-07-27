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
              "missingSkills": [],
              "summary": ""
            }

            Numeric score:
            - "score" is the primary result and must be an integer from 0 to 100.
            - Never produce match categories such as STRONG_MATCH, GOOD_MATCH or
              STRETCH_OPPORTUNITY - the score itself is the only result.
            - A low score does not mean the candidate must not apply; it only reflects fit.

            Candidate proficiency levels (from most to least confident):
            - EXPERT: deep knowledge, can design solutions and guide others on this skill.
            - STRONG: confident production experience, can troubleshoot difficult problems.
            - WORKING: can independently complete normal implementation tasks.
            - BASIC: understands the fundamentals, may need guidance for non-trivial work.
            A candidate proficiency lower than what the vacancy appears to require may reduce the
            score, but this is not the same as the skill being completely missing - treat a lower
            level as a partial match, not an absence. Do not treat all listed candidate skills as
            equal: weigh their proficiency level when judging fit.

            Missing skills (temporary "missingSkills" field, until a later step separates
            required vs. preferred explicitly):
            - Include only skills the vacancy clearly presents as required AND that are absent
              from the candidate profile.
            - Do not include preferred, optional, nice-to-have or bonus skills here.
            - Do not treat uncertain or ambiguous wording as a required skill.
            - Do not invent requirements that are not present in the vacancy description.
            - Preferred or optional gaps may be mentioned briefly in "cons", but their effect on
              the score must be small.

            Experience years:
            - Detect requested experience years only when explicitly stated in the vacancy.
            - Compare requested years with the candidate's total experience years.
            - A small gap should have a moderate effect on the score; a large gap may have a
              stronger effect, but an experience gap must never by itself produce a zero score.
            - If the vacancy does not specify required years, do not penalize the candidate for
              experience at all.
            - Briefly explain a meaningful years mismatch in "cons" or "summary".

            Seniority:
            - Consider the candidate's target seniority against the vacancy's stated seniority
              using the complete vacancy context, not the job title alone.
            - Do not assume that different wording automatically means a mismatch - "Senior
              Software Engineer", "Senior Backend Developer", "Java Engineer" and "Software
              Developer" or "Senior Software Developer" may all describe a compatible role.

            Candidate preferences:
            - Each preference has an importance: REQUIRED, STRONG, PREFERRED, or NEUTRAL.
            - REQUIRED: a confirmed conflict should significantly affect the score.
            - STRONG: an important factor, but not always disqualifying.
            - PREFERRED: only a small positive or negative influence.
            - NEUTRAL: no meaningful score impact.
            - Known candidate preference semantics: remote work is strongly preferred; working
              from Poland is acceptable; relocation is not desired; B2B is preferred but not
              mandatory; product companies are preferred but not mandatory.
            - An unconfigured salary expectation must never affect the score.
            - Never invent a vacancy's location, contract type, company type or salary when the
              vacancy does not state it.

            Evidence rules:
            - Use only information explicitly present in the candidate profile and the vacancy.
            - Never invent candidate experience or vacancy requirements.
            - Never infer that a technology is known because a related technology is known;
              related technologies may be mentioned only as transferable experience, and only
              when reasonable.
            - Always distinguish "missing" from "lower proficiency" in your reasoning.
            - Avoid duplicate statements across "pros", "cons", "missingSkills" and "summary".
            """;

    private final JobAnalysisAiPort jobAnalysisAiPort;

    public JobAnalysis analyze(CandidateProfile profile, JobOffer job) {
        JobAnalysis analysis = jobAnalysisAiPort.analyze(SYSTEM_PROMPT, buildUserPrompt(profile, job));
        validate(analysis);
        return analysis;
    }

    private void validate(JobAnalysis analysis) {
        if (analysis == null) {
            throw new JobAnalysisException("AI provider returned no job analysis");
        }
        if (analysis.score() < 0 || analysis.score() > 100) {
            throw new JobAnalysisException("AI provider returned an out-of-range score: " + analysis.score());
        }
        if (analysis.summary() == null || analysis.summary().isBlank()) {
            throw new JobAnalysisException("AI provider returned a blank summary");
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
