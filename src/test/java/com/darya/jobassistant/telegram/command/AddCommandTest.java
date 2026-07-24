package com.darya.jobassistant.telegram.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.vacancyimport.StartVacancyImportUseCase;
import com.darya.jobassistant.vacancyimport.dto.StartVacancyImportResult;
import com.darya.jobassistant.vacancyimport.model.ImportState;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@ExtendWith(MockitoExtension.class)
class AddCommandTest {

    private static final long CHAT_ID = 555L;
    private static final long USER_ID = 777L;

    @Mock
    private StartVacancyImportUseCase startVacancyImportUseCase;

    @Mock
    private Message message;

    @Mock
    private User sender;

    private AddCommand addCommand;

    @BeforeEach
    void setUp() {
        addCommand = new AddCommand(startVacancyImportUseCase);
        when(message.getChatId()).thenReturn(CHAT_ID);
        when(message.getFrom()).thenReturn(sender);
        when(sender.getId()).thenReturn(USER_ID);
    }

    @Test
    void execute_callsStartUseCaseWithChatAndUserId() {
        when(startVacancyImportUseCase.start(CHAT_ID, USER_ID))
                .thenReturn(new StartVacancyImportResult.Started(UUID.randomUUID()));

        addCommand.execute(message);

        verify(startVacancyImportUseCase).start(CHAT_ID, USER_ID);
    }

    @Test
    void execute_newSession_returnsUrlInstruction() {
        when(startVacancyImportUseCase.start(CHAT_ID, USER_ID))
                .thenReturn(new StartVacancyImportResult.Started(UUID.randomUUID()));

        BotResponse response = addCommand.execute(message);

        assertThat(response.text()).contains("Send the vacancy URL");
        assertThat(response.text()).contains("/cancel");
    }

    @Test
    void execute_alreadyWaitingForUrl_returnsWaitingForUrlGuidance() {
        when(startVacancyImportUseCase.start(CHAT_ID, USER_ID))
                .thenReturn(new StartVacancyImportResult.AlreadyActive(UUID.randomUUID(), ImportState.WAITING_FOR_URL));

        BotResponse response = addCommand.execute(message);

        assertThat(response.text()).contains("already active");
        assertThat(response.text()).contains("Send the vacancy URL or use /cancel");
    }

    @Test
    void execute_alreadyWaitingForDescription_returnsDescriptionGuidance() {
        when(startVacancyImportUseCase.start(CHAT_ID, USER_ID))
                .thenReturn(new StartVacancyImportResult.AlreadyActive(UUID.randomUUID(), ImportState.WAITING_FOR_DESCRIPTION));

        BotResponse response = addCommand.execute(message);

        assertThat(response.text()).contains("Send the full vacancy description or use /cancel");
    }

    @Test
    void execute_extracting_returnsProcessingGuidance() {
        when(startVacancyImportUseCase.start(CHAT_ID, USER_ID))
                .thenReturn(new StartVacancyImportResult.AlreadyActive(UUID.randomUUID(), ImportState.EXTRACTING));

        BotResponse response = addCommand.execute(message);

        assertThat(response.text()).contains("currently being processed");
    }

    @Test
    void execute_waitingForConfirmation_returnsConfirmationGuidance() {
        when(startVacancyImportUseCase.start(CHAT_ID, USER_ID))
                .thenReturn(new StartVacancyImportResult.AlreadyActive(UUID.randomUUID(), ImportState.WAITING_FOR_CONFIRMATION));

        BotResponse response = addCommand.execute(message);

        assertThat(response.text()).contains("already been recognized");
        assertThat(response.text()).contains("Confirm, retry, or cancel");
    }

    @Test
    void execute_useCaseThrowsUnexpectedException_returnsGenericMessageWithoutLeakingDetails() {
        when(startVacancyImportUseCase.start(CHAT_ID, USER_ID))
                .thenThrow(new RuntimeException("ERROR: duplicate key value violates unique constraint \"uk_vacancy_import_session_active_chat_user\""));

        BotResponse response = addCommand.execute(message);

        assertThat(response.text()).isEqualTo("Something went wrong while starting the vacancy import. Please try again.");
        assertThat(response.text()).doesNotContain("constraint", "SQL", "uk_vacancy_import_session");
    }
}
