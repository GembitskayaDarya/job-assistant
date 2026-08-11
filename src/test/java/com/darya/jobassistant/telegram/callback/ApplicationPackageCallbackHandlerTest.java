package com.darya.jobassistant.telegram.callback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.applicationmaterials.preparation.PrepareApplicationPackageOutcome;
import com.darya.jobassistant.applicationmaterials.preparation.PrepareApplicationPackageUseCase;
import com.darya.jobassistant.telegram.TelegramMessageSender;
import com.darya.jobassistant.telegram.command.BotResponse;
import com.darya.jobassistant.telegram.format.ApplicationPackageTelegramFormatter;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@ExtendWith(MockitoExtension.class)
class ApplicationPackageCallbackHandlerTest {

    private static final long CHAT_ID = 555L;
    private static final UUID VACANCY_ID = UUID.randomUUID();

    @Mock
    private PrepareApplicationPackageUseCase prepareApplicationPackageUseCase;

    @Mock
    private ApplicationPackageTelegramFormatter formatter;

    @Mock
    private TelegramMessageSender telegramMessageSender;

    @Mock
    private CallbackQuery callbackQuery;

    @Mock
    private Message message;

    private ApplicationPackageCallbackHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ApplicationPackageCallbackHandler(prepareApplicationPackageUseCase, formatter, telegramMessageSender);
        lenient().when(callbackQuery.getId()).thenReturn("callback-id");
        lenient().when(callbackQuery.getMessage()).thenReturn(message);
        lenient().when(message.getChatId()).thenReturn(CHAT_ID);
    }

    @Test
    void handle_unrelatedCallback_isNotConsumed() {
        when(callbackQuery.getData()).thenReturn("via:analyze:" + VACANCY_ID);

        boolean consumed = handler.handle(callbackQuery);

        assertThat(consumed).isFalse();
        verify(prepareApplicationPackageUseCase, never()).prepare(any());
        verify(telegramMessageSender, never()).answerCallbackQuery(any(), any());
    }

    @Test
    void handle_malformedCallback_doesNotCrashAndAcknowledgesSafely() {
        when(callbackQuery.getData()).thenReturn("am:prepare:not-a-uuid");

        boolean consumed = handler.handle(callbackQuery);

        assertThat(consumed).isTrue();
        verify(telegramMessageSender).answerCallbackQuery("callback-id", "This action is not available.");
        verify(prepareApplicationPackageUseCase, never()).prepare(any());
    }

    @Test
    void handle_validCallback_acknowledgesBeforeStartingPreparation() {
        when(callbackQuery.getData()).thenReturn("am:prepare:" + VACANCY_ID);
        UUID generationId = UUID.randomUUID();
        PrepareApplicationPackageOutcome outcome = new PrepareApplicationPackageOutcome.AlreadyInProgress(generationId);
        when(prepareApplicationPackageUseCase.prepare(VACANCY_ID)).thenReturn(outcome);
        when(formatter.toBotResponse(outcome)).thenReturn(BotResponse.text("in progress"));

        boolean consumed = handler.handle(callbackQuery);

        assertThat(consumed).isTrue();
        InOrder inOrder = Mockito.inOrder(telegramMessageSender, prepareApplicationPackageUseCase);
        inOrder.verify(telegramMessageSender).answerCallbackQuery(eq("callback-id"), any());
        inOrder.verify(telegramMessageSender).send(eq(CHAT_ID), any()); // "started" message
        inOrder.verify(prepareApplicationPackageUseCase).prepare(VACANCY_ID);
        inOrder.verify(telegramMessageSender).send(eq(CHAT_ID), any()); // final result
    }

    @Test
    void handle_delegatesToSamePrepareApplicationPackageUseCaseAsCommand() {
        when(callbackQuery.getData()).thenReturn("am:prepare:" + VACANCY_ID);
        PrepareApplicationPackageOutcome outcome = new PrepareApplicationPackageOutcome.VacancyNotFound(VACANCY_ID);
        when(prepareApplicationPackageUseCase.prepare(VACANCY_ID)).thenReturn(outcome);
        when(formatter.toBotResponse(outcome)).thenReturn(BotResponse.text("not found"));

        handler.handle(callbackQuery);

        verify(prepareApplicationPackageUseCase, times(1)).prepare(VACANCY_ID);
    }

    @Test
    void handle_preparedResult_sendsFormattedResponseWithDocuments() {
        when(callbackQuery.getData()).thenReturn("am:prepare:" + VACANCY_ID);
        PrepareApplicationPackageOutcome outcome = new PrepareApplicationPackageOutcome.VacancyNotFound(VACANCY_ID);
        when(prepareApplicationPackageUseCase.prepare(VACANCY_ID)).thenReturn(outcome);
        BotResponse formatted = BotResponse.text("formatted result");
        when(formatter.toBotResponse(outcome)).thenReturn(formatted);

        handler.handle(callbackQuery);

        ArgumentCaptor<BotResponse> captor = ArgumentCaptor.forClass(BotResponse.class);
        verify(telegramMessageSender, times(2)).send(eq(CHAT_ID), captor.capture());
        assertThat(captor.getAllValues().get(1)).isSameAs(formatted);
    }

    @Test
    void handle_useCaseThrowsUnexpectedException_sendsGenericFailureWithoutLeakingDetails() {
        when(callbackQuery.getData()).thenReturn("am:prepare:" + VACANCY_ID);
        when(prepareApplicationPackageUseCase.prepare(VACANCY_ID)).thenThrow(new RuntimeException("HTTP 429 - insufficient_quota"));

        handler.handle(callbackQuery);

        ArgumentCaptor<BotResponse> captor = ArgumentCaptor.forClass(BotResponse.class);
        verify(telegramMessageSender, times(2)).send(eq(CHAT_ID), captor.capture());
        BotResponse failureResponse = captor.getAllValues().get(1);
        assertThat(failureResponse.text()).doesNotContain("429", "insufficient_quota", "RuntimeException");
        assertThat(failureResponse.text()).contains("couldn't prepare");
    }

    @Test
    void handle_noMessage_acknowledgesSafelyWithoutCallingUseCase() {
        when(callbackQuery.getData()).thenReturn("am:prepare:" + VACANCY_ID);
        when(callbackQuery.getMessage()).thenReturn(null);

        boolean consumed = handler.handle(callbackQuery);

        assertThat(consumed).isTrue();
        verify(telegramMessageSender).answerCallbackQuery("callback-id", "This action is not available.");
        verify(prepareApplicationPackageUseCase, never()).prepare(any());
    }
}
