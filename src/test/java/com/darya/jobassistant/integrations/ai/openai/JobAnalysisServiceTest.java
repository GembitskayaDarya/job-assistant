package com.darya.jobassistant.integrations.ai.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.ai.exception.JobAnalysisException;
import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.ai.port.JobAnalysisAiPort;
import com.darya.jobassistant.candidates.CandidatePreferences;
import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.CandidateSkill;
import com.darya.jobassistant.candidates.PreferenceImportance;
import com.darya.jobassistant.candidates.SkillProficiency;
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
                "Senior",
                List.of(
                        new CandidateSkill("Java", SkillProficiency.EXPERT, null),
                        new CandidateSkill("Spring Boot", SkillProficiency.STRONG, null),
                        new CandidateSkill("Kafka", SkillProficiency.WORKING, null),
                        new CandidateSkill("AWS", SkillProficiency.BASIC, null)),
                List.of("English", "Polish"),
                6,
                new CandidatePreferences(
                        null, "Remote, Europe", PreferenceImportance.STRONG, List.of(), false,
                        List.of(), null, "Product company", null, null));
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
        // The prompt names these categories only to tell the model never to produce them.
        assertThat(systemPrompt).contains("Never produce match categories");

        String userPrompt = userPromptCaptor.getValue();
        // Candidate identity and skills, with proficiency and preserved order.
        assertThat(userPrompt).contains("Senior Java Backend Engineer");
        assertThat(userPrompt).contains("Target seniority: Senior");
        assertThat(userPrompt).contains("Total experience years: 6");
        assertThat(userPrompt).contains("Languages: English, Polish");
        assertThat(userPrompt)
                .contains("- Java: EXPERT")
                .contains("- Spring Boot: STRONG")
                .contains("- Kafka: WORKING")
                .contains("- AWS: BASIC");
        assertThat(userPrompt.indexOf("- Java: EXPERT"))
                .isLessThan(userPrompt.indexOf("- Spring Boot: STRONG"))
                .isLessThan(userPrompt.indexOf("- Kafka: WORKING"))
                .isLessThan(userPrompt.indexOf("- AWS: BASIC"));

        // Preferences, including unconfigured fields rendered as "Not configured", never as null.
        assertThat(userPrompt).contains("Preferred work arrangement: Remote, Europe");
        assertThat(userPrompt).contains("Work-arrangement importance: STRONG");
        assertThat(userPrompt).contains("Relocation allowed: false");
        assertThat(userPrompt).contains("Preferred company type: Product company");
        assertThat(userPrompt).contains("Current country: Not configured");
        assertThat(userPrompt).contains("Allowed work countries: Not configured");
        assertThat(userPrompt).contains("Preferred contract types: Not configured");
        assertThat(userPrompt).contains("Contract-type importance: Not configured");
        assertThat(userPrompt).contains("Company-type importance: Not configured");
        assertThat(userPrompt).contains("Salary expectation: Not configured");
        assertThat(userPrompt).doesNotContain("null");

        // Job description fields.
        assertThat(userPrompt).contains("Backend Engineer");
        assertThat(userPrompt).contains("Acme Corp");
        assertThat(userPrompt).contains("120k-140k");
        assertThat(userPrompt).contains("Build backend services");
    }

    @Test
    void analyze_userPrompt_neverContainsSkillProficiencyNoneOrBareSkillNamesOnly() {
        CandidateProfile profile = validProfile();
        JobAnalysis expected = new JobAnalysis(50, List.of(), List.of(), List.of(), "Summary");
        when(jobAnalysisAiPort.analyze(anyString(), anyString())).thenReturn(expected);

        jobAnalysisService.analyze(profile, validJob());

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(jobAnalysisAiPort).analyze(anyString(), userPromptCaptor.capture());
        String userPrompt = userPromptCaptor.getValue();

        assertThat(userPrompt).doesNotContain("NONE");
        // Every configured skill must appear with its level, not as a bare comma-separated name.
        assertThat(userPrompt).doesNotContain("Java, Spring Boot, Kafka");
    }

    @Test
    void analyze_throwsJobAnalysisExceptionWhenPortReturnsNull() {
        when(jobAnalysisAiPort.analyze(anyString(), anyString())).thenReturn(null);

        assertThatThrownBy(() -> jobAnalysisService.analyze(validProfile(), validJob()))
                .isInstanceOf(JobAnalysisException.class);
    }

    @Test
    void analyze_throwsJobAnalysisExceptionWhenScoreIsBelowZero() {
        JobAnalysis invalid = new JobAnalysis(-1, List.of(), List.of(), List.of(), "Some summary");
        when(jobAnalysisAiPort.analyze(anyString(), anyString())).thenReturn(invalid);

        assertThatThrownBy(() -> jobAnalysisService.analyze(validProfile(), validJob()))
                .isInstanceOf(JobAnalysisException.class);
    }

    @Test
    void analyze_throwsJobAnalysisExceptionWhenScoreIsAboveHundred() {
        JobAnalysis invalid = new JobAnalysis(101, List.of(), List.of(), List.of(), "Some summary");
        when(jobAnalysisAiPort.analyze(anyString(), anyString())).thenReturn(invalid);

        assertThatThrownBy(() -> jobAnalysisService.analyze(validProfile(), validJob()))
                .isInstanceOf(JobAnalysisException.class);
    }

    @Test
    void analyze_throwsJobAnalysisExceptionWhenSummaryIsBlank() {
        JobAnalysis invalid = new JobAnalysis(50, List.of(), List.of(), List.of(), "   ");
        when(jobAnalysisAiPort.analyze(anyString(), anyString())).thenReturn(invalid);

        assertThatThrownBy(() -> jobAnalysisService.analyze(validProfile(), validJob()))
                .isInstanceOf(JobAnalysisException.class);
    }

    @Test
    void analyze_propagatesJobAnalysisExceptionThrownByPort() {
        JobAnalysisException portFailure = new JobAnalysisException("Failed to obtain job analysis from AI provider");
        when(jobAnalysisAiPort.analyze(anyString(), anyString())).thenThrow(portFailure);

        assertThatThrownBy(() -> jobAnalysisService.analyze(validProfile(), validJob()))
                .isSameAs(portFailure);
    }

    private CandidateProfile validProfile() {
        return new CandidateProfile(
                "Senior Java Backend Engineer",
                "Senior",
                List.of(
                        new CandidateSkill("Java", SkillProficiency.EXPERT, null),
                        new CandidateSkill("Spring Boot", SkillProficiency.STRONG, null),
                        new CandidateSkill("Kafka", SkillProficiency.WORKING, null)),
                List.of("English", "Polish"),
                6,
                new CandidatePreferences(
                        null, "Remote, Europe", PreferenceImportance.STRONG, List.of(), false,
                        List.of(), null, "Product company", null, null));
    }

    private JobOffer validJob() {
        return new JobOffer(
                "job-1",
                "Backend Engineer",
                "Acme Corp",
                "Remote",
                "120k-140k",
                "Build backend services",
                "https://example.com/job-1",
                "remoteok");
    }
}
