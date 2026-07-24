package com.darya.jobassistant.telegram.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.vacancyimport.CancelVacancyImportUseCase;
import com.darya.jobassistant.vacancyimport.dto.CancelVacancyImportResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@ExtendWith(MockitoExtension.class)
class CancelCommandTest {

    private static final long CHAT_ID = 555L;
    private static final long USER_ID = 777L;

    @Mock
    private CancelVacancyImportUseCase cancelVacancyImportUseCase;

    @Mock
    private Message message;

    @Mock
    private User sender;

    private CancelCommand cancelCommand;

    @BeforeEach
    void setUp() {
        cancelCommand = new CancelCommand(cancelVacancyImportUseCase);
        when(message.getChatId()).thenReturn(CHAT_ID);
        when(message.getFrom()).thenReturn(sender);
        when(sender.getId()).thenReturn(USER_ID);
    }

    @Test
    void execute_callsCancelUseCaseWithChatAndUserId() {
        when(cancelVacancyImportUseCase.cancel(CHAT_ID, USER_ID)).thenReturn(CancelVacancyImportResult.CANCELLED);

        cancelCommand.execute(message);

        verify(cancelVacancyImportUseCase).cancel(CHAT_ID, USER_ID);
    }

    @Test
    void execute_cancelled_returnsConfirmationMessage() {
        when(cancelVacancyImportUseCase.cancel(CHAT_ID, USER_ID)).thenReturn(CancelVacancyImportResult.CANCELLED);

        BotResponse response = cancelCommand.execute(message);

        assertThat(response.text()).isEqualTo("Vacancy import cancelled.");
    }

    @Test
    void execute_noActiveSession_returnsHelpfulMessage() {
        when(cancelVacancyImportUseCase.cancel(CHAT_ID, USER_ID)).thenReturn(CancelVacancyImportResult.NO_ACTIVE_SESSION);

        BotResponse response = cancelCommand.execute(message);

        assertThat(response.text()).contains("no active vacancy import");
        assertThat(response.text()).contains("/add");
    }

    @Test
    void execute_useCaseThrowsUnexpectedException_returnsGenericMessageWithoutLeakingDetails() {
        when(cancelVacancyImportUseCase.cancel(CHAT_ID, USER_ID))
                .thenThrow(new RuntimeException("Connection to postgres://prod-db:5432 refused"));

        BotResponse response = cancelCommand.execute(message);

        assertThat(response.text()).isEqualTo("Something went wrong while cancelling the vacancy import. Please try again.");
        assertThat(response.text()).doesNotContain("postgres", "5432", "Connection");
    }
}
