package com.darya.jobassistant.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.telegram.command.BotResponse;
import com.darya.jobassistant.vacancyimport.ProvideVacancyUrlUseCase;
import com.darya.jobassistant.vacancyimport.dto.ProvideVacancyUrlResult;
import com.darya.jobassistant.vacancyimport.model.ImportState;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@ExtendWith(MockitoExtension.class)
class VacancyImportMessageHandlerTest {

    private static final long CHAT_ID = 555L;
    private static final long USER_ID = 777L;

    @Mock
    private ProvideVacancyUrlUseCase provideVacancyUrlUseCase;

    @Mock
    private Message message;

    @Mock
    private User sender;

    private VacancyImportMessageHandler handler;

    @BeforeEach
    void setUp() {
        handler = new VacancyImportMessageHandler(provideVacancyUrlUseCase);
        when(message.getChatId()).thenReturn(CHAT_ID);
        when(message.getFrom()).thenReturn(sender);
        when(sender.getId()).thenReturn(USER_ID);
        when(message.getText()).thenReturn("https://example.com/job/123");
    }

    @Test
    void handle_passesRawTextWithCorrectChatAndUserIds() {
        when(provideVacancyUrlUseCase.provideUrl(CHAT_ID, USER_ID, "https://example.com/job/123"))
                .thenReturn(new ProvideVacancyUrlResult.Accepted(UUID.randomUUID(), URI.create("https://example.com/job/123")));

        handler.handle(message);

        verify(provideVacancyUrlUseCase).provideUrl(CHAT_ID, USER_ID, "https://example.com/job/123");
    }

    @Test
    void handle_accepted_returnsDescriptionInstruction() {
        when(provideVacancyUrlUseCase.provideUrl(CHAT_ID, USER_ID, "https://example.com/job/123"))
                .thenReturn(new ProvideVacancyUrlResult.Accepted(UUID.randomUUID(), URI.create("https://example.com/job/123")));

        Optional<BotResponse> response = handler.handle(message);

        assertThat(response).isPresent();
        assertThat(response.get().text()).contains("Vacancy link received");
        assertThat(response.get().text()).contains("full vacancy description");
    }

    @Test
    void handle_invalidUrl_returnsHelpfulGuidance() {
        when(provideVacancyUrlUseCase.provideUrl(CHAT_ID, USER_ID, "https://example.com/job/123"))
                .thenReturn(new ProvideVacancyUrlResult.InvalidUrl("URL must use http or https"));

        Optional<BotResponse> response = handler.handle(message);

        assertThat(response).isPresent();
        assertThat(response.get().text()).contains("does not look like a valid vacancy URL");
        assertThat(response.get().text()).doesNotContain("URL must use http or https");
    }

    @Test
    void handle_noActiveSession_returnsEmptyToPreserveFallbackBehavior() {
        when(provideVacancyUrlUseCase.provideUrl(CHAT_ID, USER_ID, "https://example.com/job/123"))
                .thenReturn(new ProvideVacancyUrlResult.NoActiveSession());

        Optional<BotResponse> response = handler.handle(message);

        assertThat(response).isEmpty();
    }

    @Test
    void handle_expiredSession_returnsRestartGuidance() {
        when(provideVacancyUrlUseCase.provideUrl(CHAT_ID, USER_ID, "https://example.com/job/123"))
                .thenReturn(new ProvideVacancyUrlResult.SessionExpired());

        Optional<BotResponse> response = handler.handle(message);

        assertThat(response).isPresent();
        assertThat(response.get().text()).contains("has expired");
        assertThat(response.get().text()).contains("/add");
    }

    @Test
    void handle_waitingForDescriptionState_returnsCorrectGuidance() {
        when(provideVacancyUrlUseCase.provideUrl(CHAT_ID, USER_ID, "https://example.com/job/123"))
                .thenReturn(new ProvideVacancyUrlResult.UnexpectedState(ImportState.WAITING_FOR_DESCRIPTION));

        Optional<BotResponse> response = handler.handle(message);

        assertThat(response).isPresent();
        assertThat(response.get().text()).contains("vacancy link has already been received");
    }

    @Test
    void handle_extractingState_returnsCorrectGuidance() {
        when(provideVacancyUrlUseCase.provideUrl(CHAT_ID, USER_ID, "https://example.com/job/123"))
                .thenReturn(new ProvideVacancyUrlResult.UnexpectedState(ImportState.EXTRACTING));

        Optional<BotResponse> response = handler.handle(message);

        assertThat(response).isPresent();
        assertThat(response.get().text()).contains("currently being processed");
    }

    @Test
    void handle_waitingForConfirmationState_returnsCorrectGuidance() {
        when(provideVacancyUrlUseCase.provideUrl(CHAT_ID, USER_ID, "https://example.com/job/123"))
                .thenReturn(new ProvideVacancyUrlResult.UnexpectedState(ImportState.WAITING_FOR_CONFIRMATION));

        Optional<BotResponse> response = handler.handle(message);

        assertThat(response).isPresent();
        assertThat(response.get().text()).contains("already been recognized");
    }

    @Test
    void handle_useCaseThrowsUnexpectedException_returnsGenericMessageWithoutLeakingDetails() {
        when(provideVacancyUrlUseCase.provideUrl(CHAT_ID, USER_ID, "https://example.com/job/123"))
                .thenThrow(new RuntimeException("ERROR: relation \"vacancy_import_session\" violates constraint"));

        Optional<BotResponse> response = handler.handle(message);

        assertThat(response).isPresent();
        assertThat(response.get().text())
                .isEqualTo("Something went wrong while processing your message. Please try again.");
        assertThat(response.get().text()).doesNotContain("relation", "constraint", "vacancy_import_session");
    }
}
