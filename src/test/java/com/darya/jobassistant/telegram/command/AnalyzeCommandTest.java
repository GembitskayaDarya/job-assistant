package com.darya.jobassistant.telegram.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.ai.AnalyzeVacancyUseCase;
import com.darya.jobassistant.ai.dto.AnalyzeVacancyResult;
import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.telegram.format.JobMessageFormatter;
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
    private AnalyzeVacancyUseCase analyzeVacancyUseCase;

    @Mock
    private JobMessageFormatter jobMessageFormatter;

    @Mock
    private Message message;

    private AnalyzeCommand analyzeCommand;

    @BeforeEach
    void setUp() {
        analyzeCommand = new AnalyzeCommand(analyzeVacancyUseCase, jobMessageFormatter);
    }

    @Test
    void execute_validUuid_returnsFormattedMarkdownV2Result() {
        UUID vacancyId = UUID.randomUUID();
        when(message.getText()).thenReturn("/analyze " + vacancyId);

        JobOffer jobOffer = jobOffer();
        JobAnalysis analysis = jobAnalysis();
        when(analyzeVacancyUseCase.analyze(vacancyId)).thenReturn(new AnalyzeVacancyResult.Available(jobOffer, analysis, true));
        when(jobMessageFormatter.format(jobOffer, analysis)).thenReturn("formatted result");

        BotResponse response = analyzeCommand.execute(message);

        verify(analyzeVacancyUseCase).analyze(vacancyId);
        verify(jobMessageFormatter).format(jobOffer, analysis);
        assertThat(response.text()).isEqualTo("formatted result");
        assertThat(response.parseMode()).isEqualTo(ParseMode.MARKDOWNV2);
    }

    @Test
    void execute_missingArgument_returnsUsageMessageWithoutDownstreamCalls() {
        when(message.getText()).thenReturn("/analyze");

        BotResponse response = analyzeCommand.execute(message);

        assertThat(response.text()).isEqualTo("Usage: /analyze <vacancy UUID>");
        verifyNoInteractions(analyzeVacancyUseCase, jobMessageFormatter);
    }

    @Test
    void execute_invalidUuid_returnsInvalidIdMessageWithoutDownstreamCalls() {
        when(message.getText()).thenReturn("/analyze abc");

        BotResponse response = analyzeCommand.execute(message);

        assertThat(response.text()).isEqualTo("Invalid vacancy ID. Use the UUID shown in the search results.");
        verifyNoInteractions(analyzeVacancyUseCase, jobMessageFormatter);
    }

    @Test
    void execute_extraArgument_returnsUsageMessageWithoutDownstreamCalls() {
        when(message.getText()).thenReturn("/analyze " + UUID.randomUUID() + " extra");

        BotResponse response = analyzeCommand.execute(message);

        assertThat(response.text()).isEqualTo("Usage: /analyze <vacancy UUID>");
        verifyNoInteractions(analyzeVacancyUseCase, jobMessageFormatter);
    }

    @Test
    void execute_vacancyNotFound_returnsFriendlyMessageWithoutFormatterCalls() {
        UUID vacancyId = UUID.randomUUID();
        when(message.getText()).thenReturn("/analyze " + vacancyId);
        when(analyzeVacancyUseCase.analyze(vacancyId)).thenReturn(new AnalyzeVacancyResult.VacancyNotFound());

        BotResponse response = analyzeCommand.execute(message);

        assertThat(response.text()).isEqualTo("Vacancy not found. Run /search and use an ID from the results.");
        verifyNoInteractions(jobMessageFormatter);
    }

    @Test
    void execute_inProgress_returnsSafeGuidanceWithoutFormatterCalls() {
        UUID vacancyId = UUID.randomUUID();
        when(message.getText()).thenReturn("/analyze " + vacancyId);
        when(analyzeVacancyUseCase.analyze(vacancyId)).thenReturn(new AnalyzeVacancyResult.InProgress());

        BotResponse response = analyzeCommand.execute(message);

        assertThat(response.text()).isEqualTo("This vacancy is already being analyzed. Please try again shortly.");
        verifyNoInteractions(jobMessageFormatter);
    }

    @Test
    void execute_analysisFailed_returnsFriendlyMessageAndDoesNotCallFormatter() {
        UUID vacancyId = UUID.randomUUID();
        when(message.getText()).thenReturn("/analyze " + vacancyId);
        when(analyzeVacancyUseCase.analyze(vacancyId)).thenReturn(new AnalyzeVacancyResult.Failed());

        BotResponse response = analyzeCommand.execute(message);

        assertThat(response.text()).isEqualTo("Unable to analyze this job right now. Please try again later.");
        assertThat(response.text()).doesNotContain("429", "insufficient_quota", "HTTP", "NonTransientAiException");
        verifyNoInteractions(jobMessageFormatter);
    }

    private JobOffer jobOffer() {
        return new JobOffer("job-1", "Backend Engineer", "Acme Corp", null, "100000 USD", "desc", "https://example.com/job-1", "remoteok");
    }

    private JobAnalysis jobAnalysis() {
        return new JobAnalysis(85, List.of("Strong Java skills"), List.of(), List.of("Kafka"), "Good match");
    }
}
