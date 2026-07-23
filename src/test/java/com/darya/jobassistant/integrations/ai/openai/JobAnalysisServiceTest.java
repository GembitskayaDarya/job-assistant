package com.darya.jobassistant.integrations.ai.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.ai.port.JobAnalysisAiPort;
import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobAnalysisServiceTest {

    @Mock
    private JobAnalysisAiPort jobAnalysisAiPort;

    private JobAnalysisService jobAnalysisService;

    @BeforeEach
    void setUp() {
        jobAnalysisService = new JobAnalysisService(jobAnalysisAiPort);
    }

    @Test
    void analyze_sendsExpectedSystemPromptAndPromptContentAndReturnsPortResultUnchanged() {
        CandidateProfile profile = new CandidateProfile(
                "Senior Java Backend Engineer",
                List.of("Java", "Spring Boot", "Kafka"),
                List.of("English", "Polish"),
                6,
                "Product company",
                "Remote, Europe");
        JobOffer job = new JobOffer(
                "job-1",
                "Backend Engineer",
                "Acme Corp",
                "Remote",
                "120k-140k",
                "Build backend services",
                "https://example.com/job-1",
                "remoteok");
        JobAnalysis expected = new JobAnalysis(
                85, List.of("Strong Java match"), List.of("No Kafka mentioned"), List.of("Kafka"), "Good match");
        when(jobAnalysisAiPort.analyze(anyString(), anyString())).thenReturn(expected);

        JobAnalysis result = jobAnalysisService.analyze(profile, job);

        assertThat(result).isSameAs(expected);

        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(jobAnalysisAiPort).analyze(systemPromptCaptor.capture(), userPromptCaptor.capture());

        String systemPrompt = systemPromptCaptor.getValue();
        assertThat(systemPrompt).contains("experienced technical recruiter");
        assertThat(systemPrompt).contains("\"score\": 0-100");

        String userPrompt = userPromptCaptor.getValue();
        assertThat(userPrompt).contains("Senior Java Backend Engineer");
        assertThat(userPrompt).contains("Java, Spring Boot, Kafka");
        assertThat(userPrompt).contains("English, Polish");
        assertThat(userPrompt).contains("6 years");
        assertThat(userPrompt).contains("Product company");
        assertThat(userPrompt).contains("Remote, Europe");
        assertThat(userPrompt).contains("Backend Engineer");
        assertThat(userPrompt).contains("Acme Corp");
        assertThat(userPrompt).contains("120k-140k");
        assertThat(userPrompt).contains("Build backend services");
    }
}
