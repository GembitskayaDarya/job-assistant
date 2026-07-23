package com.darya.jobassistant.integrations.ai.openai;

import com.darya.jobassistant.integrations.jobsource.JobOffer;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JobAnalysisService {

    private static final String SYSTEM_PROMPT = """
            You are a career assistant helping a job seeker evaluate open positions.
            Assess the job offer objectively based only on the information provided.
            Do not invent facts that are not present in the job offer.
            """;

    private final ChatClient chatClient;

    public JobAnalysis analyze(JobOffer job) {
        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(buildUserPrompt(job))
                .call()
                .entity(JobAnalysis.class);
    }

    private String buildUserPrompt(JobOffer job) {
        return """
                Analyze this job offer and assess how strong a match it is for a job seeker:

                Title: %s
                Company: %s
                Location: %s
                Salary: %s
                Description: %s
                """.formatted(
                orNotAvailable(job.title()),
                orNotAvailable(job.company()),
                orNotAvailable(job.location()),
                orNotAvailable(job.salary()),
                orNotAvailable(job.description()));
    }

    private String orNotAvailable(String value) {
        return value == null || value.isBlank() ? "N/A" : value;
    }
}
