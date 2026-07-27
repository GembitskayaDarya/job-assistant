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
                85, List.of("Strong Java match"), List.of("No Kafka mentioned"),
                List.of("Kafka"), List.of("Terraform"),
                "The vacancy requests 5+ years and the candidate has 6, so the requirement is met.",
                "Remote and Poland-based work both match the candidate's preferences.",
                "Good match");
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

        // Job description fields.
        assertThat(userPrompt).contains("Backend Engineer");
        assertThat(userPrompt).contains("Acme Corp");
        assertThat(userPrompt).contains("120k-140k");
        assertThat(userPrompt).contains("Build backend services");
    }

    @Test
    void systemPrompt_declaresTheNewJsonResponseShape() {
        stubAnalysis(validAnalysis());

        jobAnalysisService.analyze(validProfile(), validJob());

        String systemPrompt = captureSystemPrompt();
        assertThat(systemPrompt)
                .contains("\"missingRequiredSkills\": []")
                .contains("\"missingPreferredSkills\": []")
                .contains("\"experienceAssessment\": \"\"")
                .contains("\"preferencesAssessment\": \"\"");
        // The old flat "missingSkills" field is no longer part of the active provider contract.
        assertThat(systemPrompt).doesNotContain("\"missingSkills\"");
    }

    @Test
    void systemPrompt_explainsRequiredPreferredAndUnknownVacancyImportance() {
        stubAnalysis(validAnalysis());

        jobAnalysisService.analyze(validProfile(), validJob());

        String systemPrompt = captureSystemPrompt();
        assertThat(systemPrompt).contains("REQUIRED").contains("PREFERRED").contains("UNKNOWN");
        assertThat(systemPrompt).contains("Do not automatically classify every mentioned technology as required");
    }

    @Test
    void systemPrompt_restrictsMissingListsToAbsentSkillsByImportance() {
        stubAnalysis(validAnalysis());

        jobAnalysisService.analyze(validProfile(), validJob());

        String systemPrompt = captureSystemPrompt();
        assertThat(systemPrompt).contains("only skills classified REQUIRED that are absent");
        assertThat(systemPrompt).contains("only skills classified PREFERRED that are absent");
        assertThat(systemPrompt).contains("Skills of UNKNOWN importance go into neither list");
        assertThat(systemPrompt).contains("is not absent");
    }

    @Test
    void systemPrompt_requiresExperienceAndPreferencesToBeAssessedSeparately() {
        stubAnalysis(validAnalysis());

        jobAnalysisService.analyze(validProfile(), validJob());

        String systemPrompt = captureSystemPrompt();
        assertThat(systemPrompt).contains("\"experienceAssessment\"");
        assertThat(systemPrompt).contains("\"preferencesAssessment\"");
        assertThat(systemPrompt).contains("never invent a").contains("never derive one from seniority wording alone");
        assertThat(systemPrompt).contains("a gap must never by itself justify a score of zero");
    }

    @Test
    void systemPrompt_keepsScoreAsTheOnlyNumericResult() {
        stubAnalysis(validAnalysis());

        jobAnalysisService.analyze(validProfile(), validJob());

        String systemPrompt = captureSystemPrompt();
        assertThat(systemPrompt).contains("\"score\": 0-100");
        assertThat(systemPrompt).contains("the score itself is the only result");
    }

    @Test
    void analyze_throwsJobAnalysisExceptionWhenPortReturnsNull() {
        when(jobAnalysisAiPort.analyze(anyString(), anyString())).thenReturn(null);

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

    private void stubAnalysis(JobAnalysis analysis) {
        when(jobAnalysisAiPort.analyze(anyString(), anyString())).thenReturn(analysis);
    }

    private String captureSystemPrompt() {
        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(jobAnalysisAiPort).analyze(systemPromptCaptor.capture(), anyString());
        return systemPromptCaptor.getValue();
    }

    private JobAnalysis validAnalysis() {
        return new JobAnalysis(
                50, List.of(), List.of(), List.of(), List.of(),
                "No years stated; seniority appears broadly aligned.",
                "No preference conflicts detected.",
                "Summary");
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
