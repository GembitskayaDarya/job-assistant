package com.darya.jobassistant.telegram.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.ai.exception.JobAnalysisException;
import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.CandidateProfileProvider;
import com.darya.jobassistant.exception.VacancyNotFoundException;
import com.darya.jobassistant.integrations.ai.openai.JobAnalysisService;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.telegram.format.JobMessageFormatter;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.mapper.VacancyJobOfferMapper;
import com.darya.jobassistant.vacancies.service.VacancyQueryService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@ExtendWith(MockitoExtension.class)
class AnalyzeCommandTest {

    @Mock
    private VacancyQueryService vacancyQueryService;

    @Mock
    private VacancyJobOfferMapper vacancyJobOfferMapper;

    @Mock
    private CandidateProfileProvider candidateProfileProvider;

    @Mock
    private JobAnalysisService jobAnalysisService;

    @Mock
    private JobMessageFormatter jobMessageFormatter;

    @Mock
    private Message message;

    private AnalyzeCommand analyzeCommand;

    @BeforeEach
    void setUp() {
        analyzeCommand = new AnalyzeCommand(
                vacancyQueryService, vacancyJobOfferMapper, candidateProfileProvider, jobAnalysisService, jobMessageFormatter);
    }

    @Test
    void execute_validUuid_returnsFormattedMarkdownV2Result() {
        UUID vacancyId = UUID.randomUUID();
        when(message.getText()).thenReturn("/analyze " + vacancyId);

        Vacancy vacancy = Vacancy.builder().build();
        JobOffer jobOffer = jobOffer();
        CandidateProfile profile = candidateProfile();
        JobAnalysis analysis = jobAnalysis();

        when(vacancyQueryService.getById(vacancyId)).thenReturn(vacancy);
        when(vacancyJobOfferMapper.toJobOffer(vacancy)).thenReturn(jobOffer);
        when(candidateProfileProvider.getProfile()).thenReturn(profile);
        when(jobAnalysisService.analyze(profile, jobOffer)).thenReturn(analysis);
        when(jobMessageFormatter.format(jobOffer, analysis)).thenReturn("formatted result");

        BotResponse response = analyzeCommand.execute(message);

        verify(vacancyQueryService, times(1)).getById(vacancyId);
        verify(vacancyJobOfferMapper).toJobOffer(vacancy);
        verify(candidateProfileProvider).getProfile();
        verify(jobAnalysisService, times(1)).analyze(profile, jobOffer);
        verify(jobMessageFormatter, times(1)).format(jobOffer, analysis);
        assertThat(response.text()).isEqualTo("formatted result");
        assertThat(response.parseMode()).isEqualTo(ParseMode.MARKDOWNV2);
    }

    @Test
    void execute_missingArgument_returnsUsageMessageWithoutDownstreamCalls() {
        when(message.getText()).thenReturn("/analyze");

        BotResponse response = analyzeCommand.execute(message);

        assertThat(response.text()).isEqualTo("Usage: /analyze <vacancy UUID>");
        verifyNoInteractions(vacancyQueryService, jobAnalysisService, jobMessageFormatter);
    }

    @Test
    void execute_invalidUuid_returnsInvalidIdMessageWithoutDownstreamCalls() {
        when(message.getText()).thenReturn("/analyze abc");

        BotResponse response = analyzeCommand.execute(message);

        assertThat(response.text()).isEqualTo("Invalid vacancy ID. Use the UUID shown in the search results.");
        verifyNoInteractions(vacancyQueryService, jobAnalysisService, jobMessageFormatter);
    }

    @Test
    void execute_extraArgument_returnsUsageMessageWithoutDownstreamCalls() {
        when(message.getText()).thenReturn("/analyze " + UUID.randomUUID() + " extra");

        BotResponse response = analyzeCommand.execute(message);

        assertThat(response.text()).isEqualTo("Usage: /analyze <vacancy UUID>");
        verifyNoInteractions(vacancyQueryService, jobAnalysisService, jobMessageFormatter);
    }

    @Test
    void execute_vacancyNotFound_returnsFriendlyMessageWithoutAiOrFormatterCalls() {
        UUID vacancyId = UUID.randomUUID();
        when(message.getText()).thenReturn("/analyze " + vacancyId);
        when(vacancyQueryService.getById(vacancyId)).thenThrow(new VacancyNotFoundException(vacancyId));

        BotResponse response = analyzeCommand.execute(message);

        assertThat(response.text()).isEqualTo("Vacancy not found. Run /search and use an ID from the results.");
        verifyNoInteractions(jobAnalysisService, jobMessageFormatter);
    }

    @Test
    void execute_jobAnalysisException_returnsFriendlyMessageAndDoesNotCallFormatter() {
        UUID vacancyId = UUID.randomUUID();
        when(message.getText()).thenReturn("/analyze " + vacancyId);

        Vacancy vacancy = Vacancy.builder().build();
        JobOffer jobOffer = jobOffer();
        CandidateProfile profile = candidateProfile();

        when(vacancyQueryService.getById(vacancyId)).thenReturn(vacancy);
        when(vacancyJobOfferMapper.toJobOffer(vacancy)).thenReturn(jobOffer);
        when(candidateProfileProvider.getProfile()).thenReturn(profile);
        RuntimeException providerFailure = new RuntimeException("HTTP 429 - insufficient_quota");
        when(jobAnalysisService.analyze(profile, jobOffer))
                .thenThrow(new JobAnalysisException("Failed to obtain job analysis from AI provider", providerFailure));

        BotResponse response = analyzeCommand.execute(message);

        assertThat(response.text()).isEqualTo("Unable to analyze this job right now. Please try again later.");
        assertThat(response.text()).doesNotContain("429", "insufficient_quota", "HTTP", "NonTransientAiException");
        verify(jobAnalysisService, times(1)).analyze(profile, jobOffer);
        verify(jobMessageFormatter, never()).format(any(), any());
    }

    private JobOffer jobOffer() {
        return new JobOffer("job-1", "Backend Engineer", "Acme Corp", null, "100000 USD", "desc", "https://example.com/job-1", "remoteok");
    }

    private CandidateProfile candidateProfile() {
        return new CandidateProfile(
                "Senior Java Backend Developer",
                List.of("Java", "Spring Boot"),
                List.of("English"),
                6,
                "Product company",
                "Remote Europe");
    }

    private JobAnalysis jobAnalysis() {
        return new JobAnalysis(85, List.of("Strong Java skills"), List.of(), List.of("Kafka"), "Good match");
    }
}
