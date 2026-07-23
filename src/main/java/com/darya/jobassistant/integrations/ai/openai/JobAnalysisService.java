package com.darya.jobassistant.integrations.ai.openai;

import com.darya.jobassistant.ai.port.JobAnalysisAiPort;
import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JobAnalysisService {

    private static final String SYSTEM_PROMPT = """
            You are an experienced technical recruiter.
            Compare the candidate profile and the job description.
            Return JSON only, in this exact shape:
            {
              "score": 0-100,
              "pros": [],
              "cons": [],
              "missingSkills": [],
              "summary": ""
            }
            Never invent missing information.
            Use only explicitly mentioned skills.
            """;

    private final JobAnalysisAiPort jobAnalysisAiPort;

    public JobAnalysis analyze(CandidateProfile profile, JobOffer job) {
        return jobAnalysisAiPort.analyze(SYSTEM_PROMPT, buildUserPrompt(profile, job));
    }

    private String buildUserPrompt(CandidateProfile profile, JobOffer job) {
        return """
                Candidate profile:
                Target role: %s
                Skills: %s
                Languages: %s
                Experience: %d years
                Preferred company type: %s
                Preferred location: %s

                Job description:
                Title: %s
                Company: %s
                Location: %s
                Salary: %s
                Description: %s
                """.formatted(
                orNotAvailable(profile.targetRole()),
                joinOrNone(profile.skills()),
                joinOrNone(profile.languages()),
                profile.experienceYears(),
                orNotAvailable(profile.preferredCompanyType()),
                orNotAvailable(profile.preferredLocation()),
                orNotAvailable(job.title()),
                orNotAvailable(job.company()),
                orNotAvailable(job.location()),
                orNotAvailable(job.salary()),
                orNotAvailable(job.description()));
    }

    private String joinOrNone(List<String> values) {
        return values == null || values.isEmpty() ? "None specified" : String.join(", ", values);
    }

    private String orNotAvailable(String value) {
        return value == null || value.isBlank() ? "N/A" : value;
    }
}
