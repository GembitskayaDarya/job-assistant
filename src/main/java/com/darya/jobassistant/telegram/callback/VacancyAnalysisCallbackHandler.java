package com.darya.jobassistant.telegram.callback;

import com.darya.jobassistant.telegram.TelegramMessageSender;
import com.darya.jobassistant.telegram.command.BotResponse;
import com.darya.jobassistant.telegram.format.JobMessageFormatter;
import com.darya.jobassistant.vacancyimport.AnalyzeImportedVacancyUseCase;
import com.darya.jobassistant.vacancyimport.dto.AnalyzeImportedVacancyResult;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

/**
 * Recognizes and handles the imported-vacancy "Analyze" callback. Delegates the decision entirely
 * to {@link AnalyzeImportedVacancyUseCase} - this class only parses callback data, reads the
 * trusted chat/user ids off the callback query, and renders the result with the shared {@link
 * JobMessageFormatter} also used by {@code /analyze}. It never touches a repository, calls {@code
 * JobAnalysisService} or the Spring AI adapter directly, inspects a JPA entity, or performs its
 * own ownership check (that is {@code AnalyzeImportedVacancyUseCase}'s job, using the ids this
 * class extracted).
 *
 * <p>Unlike {@link VacancyImportCallbackHandler} (which returns a pure outcome for {@code
 * JobAssistantTelegramBot} to act on), this handler drives its own Telegram I/O directly through
 * {@link TelegramMessageSender}: the callback must be acknowledged with "Analyzing vacancy..."
 * <em>before</em> the potentially slow AI call runs, and the result is sent as a new message
 * afterward rather than an edit of the original card - a shape the bot's synchronous
 * handle-then-respond flow does not fit. {@link #handle} returns {@code true} once it has fully
 * handled (acknowledged and, on success, responded to) the callback, so {@code
 * JobAssistantTelegramBot} knows not to also route it to {@link VacancyImportCallbackHandler}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "telegram", name = "enabled", havingValue = "true")
public class VacancyAnalysisCallbackHandler {

    private static final String NOT_AVAILABLE_MESSAGE = "This action is not available.";

    private static final String ANALYZING_MESSAGE = "Analyzing vacancy...";

    private static final String IN_PROGRESS_MESSAGE = """
            This vacancy is already being analyzed.

            Try again shortly.""";

    private static final String INVALID_STATE_MESSAGE = "This vacancy has not been saved yet.";

    private static final String MISSING_VACANCY_MESSAGE = "I couldn't analyze this vacancy right now.";

    private static final String FAILURE_MESSAGE = """
            I couldn't analyze this vacancy right now.

            Please try again later.""";

    private final AnalyzeImportedVacancyUseCase analyzeImportedVacancyUseCase;
    private final JobMessageFormatter jobMessageFormatter;
    private final ApplicationPackageKeyboardFactory applicationPackageKeyboardFactory;
    private final TelegramMessageSender telegramMessageSender;

    public boolean handle(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        if (!VacancyAnalysisCallbackData.recognizes(data)) {
            return false;
        }

        Optional<VacancyAnalysisCallbackData> parsed = VacancyAnalysisCallbackData.parse(data);
        if (parsed.isEmpty() || callbackQuery.getMessage() == null || callbackQuery.getFrom() == null) {
            telegramMessageSender.answerCallbackQuery(callbackQuery.getId(), NOT_AVAILABLE_MESSAGE);
            return true;
        }

        // Acknowledge before the potentially slow AI call runs, so the Telegram client's loading
        // spinner does not sit active for the whole request.
        telegramMessageSender.answerCallbackQuery(callbackQuery.getId(), ANALYZING_MESSAGE);

        long chatId = callbackQuery.getMessage().getChatId();
        long userId = callbackQuery.getFrom().getId();
        UUID sessionId = parsed.get().sessionId();
        try {
            AnalyzeImportedVacancyResult result = analyzeImportedVacancyUseCase.analyze(sessionId, chatId, userId);
            telegramMessageSender.send(chatId, toBotResponse(result));
        } catch (RuntimeException e) {
            log.error("Failed to process vacancy analysis callback for chat {} user {}", chatId, userId, e);
            telegramMessageSender.send(chatId, BotResponse.text(FAILURE_MESSAGE));
        }
        return true;
    }

    private BotResponse toBotResponse(AnalyzeImportedVacancyResult result) {
        return switch (result) {
            case AnalyzeImportedVacancyResult.Available(var ignoredSession, var vacancyId, var vacancy, var analysis, var ignoredNew) ->
                    new BotResponse(jobMessageFormatter.format(vacancy, analysis), ParseMode.MARKDOWNV2,
                            applicationPackageKeyboardFactory.prepareApplicationPackageKeyboard(vacancyId));
            case AnalyzeImportedVacancyResult.InProgress ignored -> BotResponse.text(IN_PROGRESS_MESSAGE);
            case AnalyzeImportedVacancyResult.NotAvailable ignored -> BotResponse.text(NOT_AVAILABLE_MESSAGE);
            case AnalyzeImportedVacancyResult.InvalidState ignored -> BotResponse.text(INVALID_STATE_MESSAGE);
            case AnalyzeImportedVacancyResult.MissingLinkedVacancy ignored -> BotResponse.text(MISSING_VACANCY_MESSAGE);
            case AnalyzeImportedVacancyResult.Failed ignored -> BotResponse.text(FAILURE_MESSAGE);
        };
    }
}
