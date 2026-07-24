package com.darya.jobassistant.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.telegram.callback.VacancyImportKeyboardFactory;
import com.darya.jobassistant.telegram.command.BotResponse;
import com.darya.jobassistant.telegram.format.VacancyDraftPreviewFormatter;
import com.darya.jobassistant.vacancyextraction.model.ExtractedVacancyData;
import com.darya.jobassistant.vacancyextraction.model.RemotePolicy;
import com.darya.jobassistant.vacancyimport.ContinueVacancyImportUseCase;
import com.darya.jobassistant.vacancyimport.dto.ContinueVacancyImportResult;
import com.darya.jobassistant.vacancyimport.model.ImportState;
import com.darya.jobassistant.vacancyimport.model.VacancyImportDraft;
import java.net.URI;
import java.time.Instant;
import java.util.List;
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
    private static final String TEXT = "some message text";

    @Mock
    private ContinueVacancyImportUseCase continueVacancyImportUseCase;

    @Mock
    private Message message;

    @Mock
    private User sender;

    private VacancyImportMessageHandler handler;

    private final VacancyDraftPreviewFormatter vacancyDraftPreviewFormatter = new VacancyDraftPreviewFormatter();
    private final VacancyImportKeyboardFactory vacancyImportKeyboardFactory = new VacancyImportKeyboardFactory();

    @BeforeEach
    void setUp() {
        handler = new VacancyImportMessageHandler(continueVacancyImportUseCase, vacancyDraftPreviewFormatter, vacancyImportKeyboardFactory);
        when(message.getChatId()).thenReturn(CHAT_ID);
        when(message.getFrom()).thenReturn(sender);
        when(sender.getId()).thenReturn(USER_ID);
        when(message.getText()).thenReturn(TEXT);
    }

    @Test
    void handle_passesCorrectChatUserIdsAndRawText() {
        when(continueVacancyImportUseCase.handleText(CHAT_ID, USER_ID, TEXT))
                .thenReturn(new ContinueVacancyImportResult.NoActiveSession());

        handler.handle(message);

        verify(continueVacancyImportUseCase).handleText(CHAT_ID, USER_ID, TEXT);
    }

    @Test
    void handle_urlAccepted_returnsDescriptionInstruction() {
        when(continueVacancyImportUseCase.handleText(CHAT_ID, USER_ID, TEXT))
                .thenReturn(new ContinueVacancyImportResult.UrlAccepted(UUID.randomUUID(), URI.create("https://example.com/job/123")));

        Optional<BotResponse> response = handler.handle(message);

        assertThat(response).isPresent();
        assertThat(response.get().text()).contains("Vacancy link received");
        assertThat(response.get().text()).contains("full vacancy description");
    }

    @Test
    void handle_vacancyRecognized_returnsTextPreviewWithConfirmationButtons() {
        UUID sessionId = UUID.randomUUID();
        VacancyImportDraft draft = draft(new ExtractedVacancyData(
                "Senior Java Backend Developer",
                "Example Company",
                "Europe",
                RemotePolicy.REMOTE,
                List.of("B2B"),
                List.of("Java", "Spring Boot", "Kafka", "AWS"),
                null));
        when(continueVacancyImportUseCase.handleText(CHAT_ID, USER_ID, TEXT))
                .thenReturn(new ContinueVacancyImportResult.VacancyRecognized(sessionId, draft));

        Optional<BotResponse> response = handler.handle(message);

        assertThat(response).isPresent();
        assertThat(response.get().text()).contains("Vacancy recognized");
        assertThat(response.get().text()).contains("Senior Java Backend Developer");
        assertThat(response.get().text()).contains("Example Company");
        assertThat(response.get().text()).contains("has not been saved yet");
        assertThat(response.get().keyboard()).isEqualTo(vacancyImportKeyboardFactory.confirmationKeyboard(sessionId));
    }

    @Test
    void handle_extractionFailed_returnsSafeMessageWithoutProviderDetails() {
        when(continueVacancyImportUseCase.handleText(CHAT_ID, USER_ID, TEXT))
                .thenReturn(new ContinueVacancyImportResult.ExtractionFailed(UUID.randomUUID()));

        Optional<BotResponse> response = handler.handle(message);

        assertThat(response).isPresent();
        assertThat(response.get().text()).contains("couldn't recognize this vacancy");
        assertThat(response.get().text()).contains("/add");
        assertThat(response.get().text()).doesNotContain("429", "insufficient_quota", "OpenAI", "exception");
    }

    private VacancyImportDraft draft(ExtractedVacancyData data) {
        Instant now = Instant.parse("2026-07-24T10:00:00Z");
        return new VacancyImportDraft(UUID.randomUUID(), UUID.randomUUID(), data, now, now);
    }

    @Test
    void handle_invalidUrl_returnsUrlGuidance() {
        when(continueVacancyImportUseCase.handleText(CHAT_ID, USER_ID, TEXT))
                .thenReturn(new ContinueVacancyImportResult.InvalidUrl("URL must use http or https"));

        Optional<BotResponse> response = handler.handle(message);

        assertThat(response).isPresent();
        assertThat(response.get().text()).contains("does not look like a valid vacancy URL");
        assertThat(response.get().text()).doesNotContain("URL must use http or https");
    }

    @Test
    void handle_invalidDescription_returnsDescriptionGuidanceWithoutInternalThreshold() {
        when(continueVacancyImportUseCase.handleText(CHAT_ID, USER_ID, TEXT))
                .thenReturn(new ContinueVacancyImportResult.InvalidDescription("Vacancy description is too short to be meaningful"));

        Optional<BotResponse> response = handler.handle(message);

        assertThat(response).isPresent();
        assertThat(response.get().text()).contains("too short");
        assertThat(response.get().text()).doesNotContain("20");
    }

    @Test
    void handle_noActiveSession_returnsEmptyToPreserveFallbackBehavior() {
        when(continueVacancyImportUseCase.handleText(CHAT_ID, USER_ID, TEXT))
                .thenReturn(new ContinueVacancyImportResult.NoActiveSession());

        Optional<BotResponse> response = handler.handle(message);

        assertThat(response).isEmpty();
    }

    @Test
    void handle_expiredSession_returnsRestartGuidance() {
        when(continueVacancyImportUseCase.handleText(CHAT_ID, USER_ID, TEXT))
                .thenReturn(new ContinueVacancyImportResult.SessionExpired());

        Optional<BotResponse> response = handler.handle(message);

        assertThat(response).isPresent();
        assertThat(response.get().text()).contains("has expired");
        assertThat(response.get().text()).contains("/add");
    }

    @Test
    void handle_extractingState_returnsProcessingGuidance() {
        when(continueVacancyImportUseCase.handleText(CHAT_ID, USER_ID, TEXT))
                .thenReturn(new ContinueVacancyImportResult.UnexpectedState(ImportState.EXTRACTING));

        Optional<BotResponse> response = handler.handle(message);

        assertThat(response).isPresent();
        assertThat(response.get().text()).contains("already been received");
        assertThat(response.get().text()).contains("being processed");
    }

    @Test
    void handle_waitingForConfirmationState_returnsConfirmationGuidance() {
        when(continueVacancyImportUseCase.handleText(CHAT_ID, USER_ID, TEXT))
                .thenReturn(new ContinueVacancyImportResult.UnexpectedState(ImportState.WAITING_FOR_CONFIRMATION));

        Optional<BotResponse> response = handler.handle(message);

        assertThat(response).isPresent();
        assertThat(response.get().text()).contains("already been recognized");
    }

    @Test
    void handle_useCaseThrowsUnexpectedException_returnsGenericMessageWithoutLeakingDetails() {
        when(continueVacancyImportUseCase.handleText(CHAT_ID, USER_ID, TEXT))
                .thenThrow(new RuntimeException("ERROR: relation \"vacancy_import_session\" violates constraint"));

        Optional<BotResponse> response = handler.handle(message);

        assertThat(response).isPresent();
        assertThat(response.get().text())
                .isEqualTo("Something went wrong while processing your message. Please try again.");
        assertThat(response.get().text()).doesNotContain("relation", "constraint", "vacancy_import_session");
    }
}
