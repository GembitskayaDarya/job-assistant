package com.darya.jobassistant.telegram.callback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.vacancyimport.ReviewVacancyImportUseCase;
import com.darya.jobassistant.vacancyimport.dto.ReviewVacancyImportResult;
import com.darya.jobassistant.vacancyimport.model.ImportState;
import com.darya.jobassistant.vacancyimport.model.VacancyImportAction;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@ExtendWith(MockitoExtension.class)
class VacancyImportCallbackHandlerTest {

    private static final long CHAT_ID = 555L;
    private static final long USER_ID = 777L;
    private static final UUID SESSION_ID = UUID.randomUUID();

    @Mock
    private ReviewVacancyImportUseCase reviewVacancyImportUseCase;

    @Mock
    private CallbackQuery callbackQuery;

    @Mock
    private Message message;

    @Mock
    private User sender;

    private VacancyImportCallbackHandler handler;

    @BeforeEach
    void setUp() {
        handler = new VacancyImportCallbackHandler(reviewVacancyImportUseCase, new VacancyImportKeyboardFactory());
        // lenient: tests for unrecognized/malformed callback data never reach the point where
        // chat/user ids are extracted, so these stubs would otherwise be reported as unused there.
        lenient().when(callbackQuery.getMessage()).thenReturn(message);
        lenient().when(message.getChatId()).thenReturn(CHAT_ID);
        lenient().when(callbackQuery.getFrom()).thenReturn(sender);
        lenient().when(sender.getId()).thenReturn(USER_ID);
    }

    @Test
    void handle_saveCallback_passesSessionIdActionChatIdAndUserId() {
        when(callbackQuery.getData()).thenReturn("vi:save:" + SESSION_ID);
        when(reviewVacancyImportUseCase.review(SESSION_ID, CHAT_ID, USER_ID, VacancyImportAction.SAVE))
                .thenReturn(new ReviewVacancyImportResult.Saved(SESSION_ID, jobOffer(), true));

        handler.handle(callbackQuery);

        verify(reviewVacancyImportUseCase).review(SESSION_ID, CHAT_ID, USER_ID, VacancyImportAction.SAVE);
    }

    @Test
    void handle_savedResult_rendersSavedMessageWithOpenVacancyButton() {
        when(callbackQuery.getData()).thenReturn("vi:save:" + SESSION_ID);
        when(reviewVacancyImportUseCase.review(SESSION_ID, CHAT_ID, USER_ID, VacancyImportAction.SAVE))
                .thenReturn(new ReviewVacancyImportResult.Saved(SESSION_ID, jobOffer(), true));

        Optional<VacancyImportCallbackOutcome> outcome = handler.handle(callbackQuery);

        assertThat(outcome).isPresent();
        assertThat(outcome.get().editedMessage()).isNotNull();
        assertThat(outcome.get().editedMessage().text()).contains("Vacancy saved");
        assertThat(outcome.get().editedMessage().text()).contains("Backend Engineer");
        assertThat(outcome.get().editedMessage().text()).contains("Acme Corp");
        assertThat(outcome.get().editedMessage().keyboard().getKeyboard()).isNotEmpty();
    }

    @Test
    void handle_repeatedSave_rendersSameSavedVacancy() {
        when(callbackQuery.getData()).thenReturn("vi:save:" + SESSION_ID);
        when(reviewVacancyImportUseCase.review(SESSION_ID, CHAT_ID, USER_ID, VacancyImportAction.SAVE))
                .thenReturn(new ReviewVacancyImportResult.Saved(SESSION_ID, jobOffer(), false));

        Optional<VacancyImportCallbackOutcome> outcome = handler.handle(callbackQuery);

        assertThat(outcome).isPresent();
        assertThat(outcome.get().editedMessage().text()).contains("Vacancy saved");
        assertThat(outcome.get().editedMessage().text()).contains("Backend Engineer");
    }

    @Test
    void handle_retryRequestedResult_rendersNewDescriptionGuidanceAndClearsButtons() {
        when(callbackQuery.getData()).thenReturn("vi:retry:" + SESSION_ID);
        when(reviewVacancyImportUseCase.review(SESSION_ID, CHAT_ID, USER_ID, VacancyImportAction.RETRY))
                .thenReturn(new ReviewVacancyImportResult.RetryRequested(SESSION_ID));

        Optional<VacancyImportCallbackOutcome> outcome = handler.handle(callbackQuery);

        assertThat(outcome).isPresent();
        assertThat(outcome.get().editedMessage().text()).contains("Send the full vacancy description again");
        assertThat(outcome.get().editedMessage().text()).contains("link has been preserved");
        assertThat(outcome.get().editedMessage().keyboard().getKeyboard()).isEmpty();
    }

    @Test
    void handle_cancelledResult_rendersCancellationAndClearsButtons() {
        when(callbackQuery.getData()).thenReturn("vi:cancel:" + SESSION_ID);
        when(reviewVacancyImportUseCase.review(SESSION_ID, CHAT_ID, USER_ID, VacancyImportAction.CANCEL))
                .thenReturn(new ReviewVacancyImportResult.Cancelled(SESSION_ID));

        Optional<VacancyImportCallbackOutcome> outcome = handler.handle(callbackQuery);

        assertThat(outcome).isPresent();
        assertThat(outcome.get().editedMessage().text()).contains("cancelled");
        assertThat(outcome.get().editedMessage().keyboard().getKeyboard()).isEmpty();
    }

    @Test
    void handle_expiredResult_rendersExpiredGuidanceWithoutEditingMessage() {
        when(callbackQuery.getData()).thenReturn("vi:save:" + SESSION_ID);
        when(reviewVacancyImportUseCase.review(SESSION_ID, CHAT_ID, USER_ID, VacancyImportAction.SAVE))
                .thenReturn(new ReviewVacancyImportResult.Expired(SESSION_ID));

        Optional<VacancyImportCallbackOutcome> outcome = handler.handle(callbackQuery);

        assertThat(outcome).isPresent();
        assertThat(outcome.get().editedMessage()).isNull();
        assertThat(outcome.get().answerText()).contains("expired");
        assertThat(outcome.get().answerText()).contains("/add");
    }

    @Test
    void handle_invalidStateResult_rendersSafeGuidance() {
        when(callbackQuery.getData()).thenReturn("vi:save:" + SESSION_ID);
        when(reviewVacancyImportUseCase.review(SESSION_ID, CHAT_ID, USER_ID, VacancyImportAction.SAVE))
                .thenReturn(new ReviewVacancyImportResult.InvalidState(SESSION_ID, ImportState.CANCELLED));

        Optional<VacancyImportCallbackOutcome> outcome = handler.handle(callbackQuery);

        assertThat(outcome).isPresent();
        assertThat(outcome.get().editedMessage()).isNull();
        assertThat(outcome.get().answerText()).isEqualTo("This vacancy import is no longer waiting for confirmation.");
    }

    @Test
    void handle_notAvailableResult_rendersSafeGuidanceWithoutLeakingReason() {
        when(callbackQuery.getData()).thenReturn("vi:save:" + SESSION_ID);
        when(reviewVacancyImportUseCase.review(SESSION_ID, CHAT_ID, USER_ID, VacancyImportAction.SAVE))
                .thenReturn(new ReviewVacancyImportResult.NotAvailable());

        Optional<VacancyImportCallbackOutcome> outcome = handler.handle(callbackQuery);

        assertThat(outcome).isPresent();
        assertThat(outcome.get().answerText()).isEqualTo("This action is not available.");
    }

    @Test
    void handle_draftMissingResult_rendersSafeGuidance() {
        when(callbackQuery.getData()).thenReturn("vi:save:" + SESSION_ID);
        when(reviewVacancyImportUseCase.review(SESSION_ID, CHAT_ID, USER_ID, VacancyImportAction.SAVE))
                .thenReturn(new ReviewVacancyImportResult.DraftMissing(SESSION_ID));

        Optional<VacancyImportCallbackOutcome> outcome = handler.handle(callbackQuery);

        assertThat(outcome).isPresent();
        assertThat(outcome.get().answerText()).isEqualTo("This action is not available.");
    }

    @Test
    void handle_malformedCallbackData_doesNotCrashAndReturnsSafeGuidanceWithoutCallingUseCase() {
        when(callbackQuery.getData()).thenReturn("vi:save:not-a-uuid");

        Optional<VacancyImportCallbackOutcome> outcome = handler.handle(callbackQuery);

        assertThat(outcome).isPresent();
        assertThat(outcome.get().answerText()).isEqualTo("This action is not available.");
        verify(reviewVacancyImportUseCase, never()).review(any(), anyLong(), anyLong(), any());
    }

    @Test
    void handle_unknownCallbackPrefix_isNotConsumed() {
        when(callbackQuery.getData()).thenReturn("search_jobs");

        Optional<VacancyImportCallbackOutcome> outcome = handler.handle(callbackQuery);

        assertThat(outcome).isEmpty();
        verify(reviewVacancyImportUseCase, never()).review(any(), anyLong(), anyLong(), any());
    }

    @Test
    void handle_useCaseThrowsUnexpectedException_returnsGenericMessageWithoutLeakingDetails() {
        when(callbackQuery.getData()).thenReturn("vi:save:" + SESSION_ID);
        when(reviewVacancyImportUseCase.review(SESSION_ID, CHAT_ID, USER_ID, VacancyImportAction.SAVE))
                .thenThrow(new RuntimeException("ERROR: relation \"vacancy\" violates constraint"));

        Optional<VacancyImportCallbackOutcome> outcome = handler.handle(callbackQuery);

        assertThat(outcome).isPresent();
        assertThat(outcome.get().answerText()).isEqualTo("Something went wrong. Please try again.");
        assertThat(outcome.get().answerText()).doesNotContain("relation", "constraint", "vacancy");
    }

    @Test
    void handle_doesNotDependOnRepositoriesOrAiAdapters() {
        // Compile-time guarantee: the constructor only accepts ReviewVacancyImportUseCase and
        // VacancyImportKeyboardFactory - there is no repository or AI-port parameter to pass.
        VacancyImportCallbackHandler h = new VacancyImportCallbackHandler(reviewVacancyImportUseCase, new VacancyImportKeyboardFactory());
        assertThat(h).isNotNull();
    }

    private JobOffer jobOffer() {
        return new JobOffer("id", "Backend Engineer", "Acme Corp", null, null, "desc", "https://example.com/job", "linkedin.com");
    }
}
