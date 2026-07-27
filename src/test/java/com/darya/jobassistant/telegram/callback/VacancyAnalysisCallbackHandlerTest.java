package com.darya.jobassistant.telegram.callback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.telegram.TelegramMessageSender;
import com.darya.jobassistant.telegram.command.BotResponse;
import com.darya.jobassistant.telegram.format.JobMessageFormatter;
import com.darya.jobassistant.vacancyimport.AnalyzeImportedVacancyUseCase;
import com.darya.jobassistant.vacancyimport.dto.AnalyzeImportedVacancyResult;
import com.darya.jobassistant.vacancyimport.model.ImportState;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@ExtendWith(MockitoExtension.class)
class VacancyAnalysisCallbackHandlerTest {

    private static final long CHAT_ID = 555L;
    private static final long USER_ID = 777L;
    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final UUID VACANCY_ID = UUID.randomUUID();

    @Mock
    private AnalyzeImportedVacancyUseCase analyzeImportedVacancyUseCase;

    @Mock
    private JobMessageFormatter jobMessageFormatter;

    @Mock
    private TelegramMessageSender telegramMessageSender;

    @Mock
    private CallbackQuery callbackQuery;

    @Mock
    private Message message;

    @Mock
    private User sender;

    private VacancyAnalysisCallbackHandler handler;

    @BeforeEach
    void setUp() {
        handler = new VacancyAnalysisCallbackHandler(analyzeImportedVacancyUseCase, jobMessageFormatter, telegramMessageSender);
        lenient().when(callbackQuery.getId()).thenReturn("callback-id");
        lenient().when(callbackQuery.getMessage()).thenReturn(message);
        lenient().when(message.getChatId()).thenReturn(CHAT_ID);
        lenient().when(callbackQuery.getFrom()).thenReturn(sender);
        lenient().when(sender.getId()).thenReturn(USER_ID);
    }

    @Test
    void handle_recognizesOnlyViaCallbacks() {
        when(callbackQuery.getData()).thenReturn("via:analyze:" + SESSION_ID);
        when(analyzeImportedVacancyUseCase.analyze(SESSION_ID, CHAT_ID, USER_ID))
                .thenReturn(new AnalyzeImportedVacancyResult.Available(SESSION_ID, VACANCY_ID, jobOffer(), jobAnalysis(), true));

        boolean consumed = handler.handle(callbackQuery);

        assertThat(consumed).isTrue();
    }

    @Test
    void handle_unrelatedCallback_isNotConsumed() {
        when(callbackQuery.getData()).thenReturn("vi:save:" + SESSION_ID);

        boolean consumed = handler.handle(callbackQuery);

        assertThat(consumed).isFalse();
        verify(analyzeImportedVacancyUseCase, never()).analyze(any(), anyLong(), anyLong());
        verify(telegramMessageSender, never()).answerCallbackQuery(any(), any());
    }

    @Test
    void handle_malformedCallback_doesNotCrashAndAcknowledgesSafely() {
        when(callbackQuery.getData()).thenReturn("via:analyze:not-a-uuid");

        boolean consumed = handler.handle(callbackQuery);

        assertThat(consumed).isTrue();
        verify(telegramMessageSender).answerCallbackQuery("callback-id", "This action is not available.");
        verify(analyzeImportedVacancyUseCase, never()).analyze(any(), anyLong(), anyLong());
    }

    @Test
    void handle_validCallback_passesSessionChatAndUserIdsToUseCase() {
        when(callbackQuery.getData()).thenReturn("via:analyze:" + SESSION_ID);
        when(analyzeImportedVacancyUseCase.analyze(SESSION_ID, CHAT_ID, USER_ID))
                .thenReturn(new AnalyzeImportedVacancyResult.Available(SESSION_ID, VACANCY_ID, jobOffer(), jobAnalysis(), true));

        handler.handle(callbackQuery);

        verify(analyzeImportedVacancyUseCase).analyze(SESSION_ID, CHAT_ID, USER_ID);
    }

    @Test
    void handle_acknowledgesBeforeCallingTheUseCase() {
        when(callbackQuery.getData()).thenReturn("via:analyze:" + SESSION_ID);
        when(analyzeImportedVacancyUseCase.analyze(SESSION_ID, CHAT_ID, USER_ID)).thenAnswer(invocation -> {
            verify(telegramMessageSender).answerCallbackQuery("callback-id", "Analyzing vacancy...");
            return new AnalyzeImportedVacancyResult.Available(SESSION_ID, VACANCY_ID, jobOffer(), jobAnalysis(), true);
        });

        handler.handle(callbackQuery);

        verify(analyzeImportedVacancyUseCase).analyze(SESSION_ID, CHAT_ID, USER_ID);
    }

    @Test
    void handle_availableResult_sendsSharedFormattedResult() {
        when(callbackQuery.getData()).thenReturn("via:analyze:" + SESSION_ID);
        JobOffer jobOffer = jobOffer();
        JobAnalysis analysis = jobAnalysis();
        when(analyzeImportedVacancyUseCase.analyze(SESSION_ID, CHAT_ID, USER_ID))
                .thenReturn(new AnalyzeImportedVacancyResult.Available(SESSION_ID, VACANCY_ID, jobOffer, analysis, true));
        when(jobMessageFormatter.format(jobOffer, analysis)).thenReturn("formatted analysis");

        handler.handle(callbackQuery);

        ArgumentCaptor<BotResponse> captor = ArgumentCaptor.forClass(BotResponse.class);
        verify(telegramMessageSender).send(eq(CHAT_ID), captor.capture());
        assertThat(captor.getValue().text()).isEqualTo("formatted analysis");
        assertThat(captor.getValue().parseMode()).isEqualTo(ParseMode.MARKDOWNV2);
    }

    @Test
    void handle_alreadyAnalyzedResult_rendersIdenticallyToFreshAnalysis() {
        when(callbackQuery.getData()).thenReturn("via:analyze:" + SESSION_ID);
        JobOffer jobOffer = jobOffer();
        JobAnalysis analysis = jobAnalysis();
        when(analyzeImportedVacancyUseCase.analyze(SESSION_ID, CHAT_ID, USER_ID))
                .thenReturn(new AnalyzeImportedVacancyResult.Available(SESSION_ID, VACANCY_ID, jobOffer, analysis, false));
        when(jobMessageFormatter.format(jobOffer, analysis)).thenReturn("formatted analysis");

        handler.handle(callbackQuery);

        ArgumentCaptor<BotResponse> captor = ArgumentCaptor.forClass(BotResponse.class);
        verify(telegramMessageSender).send(eq(CHAT_ID), captor.capture());
        assertThat(captor.getValue().text()).isEqualTo("formatted analysis");
    }

    @Test
    void handle_inProgress_sendsSafeGuidance() {
        when(callbackQuery.getData()).thenReturn("via:analyze:" + SESSION_ID);
        when(analyzeImportedVacancyUseCase.analyze(SESSION_ID, CHAT_ID, USER_ID)).thenReturn(new AnalyzeImportedVacancyResult.InProgress());

        handler.handle(callbackQuery);

        ArgumentCaptor<BotResponse> captor = ArgumentCaptor.forClass(BotResponse.class);
        verify(telegramMessageSender).send(eq(CHAT_ID), captor.capture());
        assertThat(captor.getValue().text()).contains("already being analyzed");
    }

    @Test
    void handle_notAvailable_sendsNonDisclosingGuidance() {
        when(callbackQuery.getData()).thenReturn("via:analyze:" + SESSION_ID);
        when(analyzeImportedVacancyUseCase.analyze(SESSION_ID, CHAT_ID, USER_ID)).thenReturn(new AnalyzeImportedVacancyResult.NotAvailable());

        handler.handle(callbackQuery);

        ArgumentCaptor<BotResponse> captor = ArgumentCaptor.forClass(BotResponse.class);
        verify(telegramMessageSender).send(eq(CHAT_ID), captor.capture());
        assertThat(captor.getValue().text()).isEqualTo("This action is not available.");
    }

    @Test
    void handle_invalidState_sendsNotSavedGuidance() {
        when(callbackQuery.getData()).thenReturn("via:analyze:" + SESSION_ID);
        when(analyzeImportedVacancyUseCase.analyze(SESSION_ID, CHAT_ID, USER_ID))
                .thenReturn(new AnalyzeImportedVacancyResult.InvalidState(ImportState.WAITING_FOR_CONFIRMATION));

        handler.handle(callbackQuery);

        ArgumentCaptor<BotResponse> captor = ArgumentCaptor.forClass(BotResponse.class);
        verify(telegramMessageSender).send(eq(CHAT_ID), captor.capture());
        assertThat(captor.getValue().text()).isEqualTo("This vacancy has not been saved yet.");
    }

    @Test
    void handle_missingLinkedVacancy_sendsGenericFailure() {
        when(callbackQuery.getData()).thenReturn("via:analyze:" + SESSION_ID);
        when(analyzeImportedVacancyUseCase.analyze(SESSION_ID, CHAT_ID, USER_ID))
                .thenReturn(new AnalyzeImportedVacancyResult.MissingLinkedVacancy(SESSION_ID));

        handler.handle(callbackQuery);

        ArgumentCaptor<BotResponse> captor = ArgumentCaptor.forClass(BotResponse.class);
        verify(telegramMessageSender).send(eq(CHAT_ID), captor.capture());
        assertThat(captor.getValue().text()).isEqualTo("I couldn't analyze this vacancy right now.");
    }

    @Test
    void handle_providerFailure_sendsGenericFailureWithoutProviderDetails() {
        when(callbackQuery.getData()).thenReturn("via:analyze:" + SESSION_ID);
        when(analyzeImportedVacancyUseCase.analyze(SESSION_ID, CHAT_ID, USER_ID)).thenReturn(new AnalyzeImportedVacancyResult.Failed());

        handler.handle(callbackQuery);

        ArgumentCaptor<BotResponse> captor = ArgumentCaptor.forClass(BotResponse.class);
        verify(telegramMessageSender).send(eq(CHAT_ID), captor.capture());
        assertThat(captor.getValue().text()).isEqualTo("I couldn't analyze this vacancy right now.\n\nPlease try again later.");
        assertThat(captor.getValue().text()).doesNotContain("429", "insufficient_quota", "OpenAI", "exception");
    }

    @Test
    void handle_useCaseThrowsUnexpectedException_returnsGenericFailureWithoutLeakingDetails() {
        when(callbackQuery.getData()).thenReturn("via:analyze:" + SESSION_ID);
        when(analyzeImportedVacancyUseCase.analyze(SESSION_ID, CHAT_ID, USER_ID))
                .thenThrow(new RuntimeException("HTTP 429 - insufficient_quota"));

        handler.handle(callbackQuery);

        ArgumentCaptor<BotResponse> captor = ArgumentCaptor.forClass(BotResponse.class);
        verify(telegramMessageSender).send(eq(CHAT_ID), captor.capture());
        assertThat(captor.getValue().text()).doesNotContain("429", "insufficient_quota", "RuntimeException");
        assertThat(captor.getValue().text()).contains("couldn't analyze");
    }

    private JobOffer jobOffer() {
        return new JobOffer("id", "Backend Engineer", "Acme Corp", null, null, "desc", "https://example.com/job", "linkedin.com");
    }

    private JobAnalysis jobAnalysis() {
        return new JobAnalysis(
                85, List.of("Strong Java skills"), List.of(), List.of("Kafka"), List.of(),
                "6 years vs. no stated requirement.", "Remote preference matches.", "Good match");
    }
}
