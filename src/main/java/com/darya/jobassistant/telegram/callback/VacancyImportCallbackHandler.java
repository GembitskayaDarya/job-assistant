package com.darya.jobassistant.telegram.callback;

import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.telegram.command.BotResponse;
import com.darya.jobassistant.vacancyimport.ReviewVacancyImportUseCase;
import com.darya.jobassistant.vacancyimport.dto.ReviewVacancyImportResult;
import com.darya.jobassistant.vacancyimport.model.ImportState;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

/**
 * Recognizes and handles vacancy-import inline-button callbacks. Delegates the actual decision
 * entirely to {@link ReviewVacancyImportUseCase} - this class only parses callback data, reads the
 * trusted chat/user ids off the callback query itself, and maps the provider-neutral result to
 * Telegram output. It never touches a repository, calls AI, or performs its own ownership check
 * (that is {@code ReviewVacancyImportUseCase}'s job, using the ids this class extracted).
 *
 * <p>Returns {@link Optional#empty()} for anything that isn't recognized vacancy-import callback
 * data, so {@code JobAssistantTelegramBot} can leave unrelated callback data (the pre-existing
 * {@code StartCommand} buttons, and whatever future handlers are added) untouched.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VacancyImportCallbackHandler {

    private static final String NOT_AVAILABLE_MESSAGE = "This action is not available.";

    private static final String SESSION_EXPIRED_MESSAGE = """
            This vacancy import has expired.

            Use /add to start a new one.""";

    private static final String INVALID_STATE_MESSAGE = "This vacancy import is no longer waiting for confirmation.";

    private static final String RETRY_MESSAGE = """
            🔄 Vacancy description discarded.

            Send the full vacancy description again.
            The vacancy link has been preserved.""";

    private static final String CANCELLED_MESSAGE = "❌ Vacancy import cancelled.";

    private static final String GENERIC_ERROR_MESSAGE = "Something went wrong. Please try again.";

    private final ReviewVacancyImportUseCase reviewVacancyImportUseCase;
    private final VacancyImportKeyboardFactory keyboardFactory;

    public Optional<VacancyImportCallbackOutcome> handle(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        if (!VacancyImportCallbackData.recognizes(data)) {
            return Optional.empty();
        }
        Optional<VacancyImportCallbackData> parsed = VacancyImportCallbackData.parse(data);
        if (parsed.isEmpty()) {
            return Optional.of(VacancyImportCallbackOutcome.toast(NOT_AVAILABLE_MESSAGE));
        }
        if (callbackQuery.getMessage() == null || callbackQuery.getFrom() == null) {
            return Optional.of(VacancyImportCallbackOutcome.toast(NOT_AVAILABLE_MESSAGE));
        }
        VacancyImportCallbackData callback = parsed.get();
        long chatId = callbackQuery.getMessage().getChatId();
        long userId = callbackQuery.getFrom().getId();
        try {
            ReviewVacancyImportResult result =
                    reviewVacancyImportUseCase.review(callback.sessionId(), chatId, userId, callback.action());
            return Optional.of(toOutcome(result));
        } catch (RuntimeException e) {
            log.error("Failed to process vacancy import callback for chat {} user {}", chatId, userId, e);
            return Optional.of(VacancyImportCallbackOutcome.toast(GENERIC_ERROR_MESSAGE));
        }
    }

    private VacancyImportCallbackOutcome toOutcome(ReviewVacancyImportResult result) {
        return switch (result) {
            case ReviewVacancyImportResult.Saved(var ignored, var vacancy, var newlyCreated) ->
                    VacancyImportCallbackOutcome.edit(savedResponse(vacancy));
            case ReviewVacancyImportResult.RetryRequested ignored ->
                    VacancyImportCallbackOutcome.edit(new BotResponse(RETRY_MESSAGE, null, VacancyImportKeyboardFactory.NO_BUTTONS));
            case ReviewVacancyImportResult.Cancelled ignored ->
                    VacancyImportCallbackOutcome.edit(new BotResponse(CANCELLED_MESSAGE, null, VacancyImportKeyboardFactory.NO_BUTTONS));
            case ReviewVacancyImportResult.Expired ignored -> VacancyImportCallbackOutcome.toast(SESSION_EXPIRED_MESSAGE);
            case ReviewVacancyImportResult.InvalidState(var ignored, var state) ->
                    VacancyImportCallbackOutcome.toast(state == ImportState.EXPIRED ? SESSION_EXPIRED_MESSAGE : INVALID_STATE_MESSAGE);
            case ReviewVacancyImportResult.NotAvailable ignored -> VacancyImportCallbackOutcome.toast(NOT_AVAILABLE_MESSAGE);
            case ReviewVacancyImportResult.DraftMissing ignored -> VacancyImportCallbackOutcome.toast(NOT_AVAILABLE_MESSAGE);
            case ReviewVacancyImportResult.Failed ignored -> VacancyImportCallbackOutcome.toast(GENERIC_ERROR_MESSAGE);
        };
    }

    private BotResponse savedResponse(JobOffer vacancy) {
        String text = "✅ Vacancy saved\n\n" + vacancy.title() + "\n" + vacancy.company();
        return new BotResponse(text, null, keyboardFactory.openVacancyKeyboard(vacancy.url()));
    }
}
