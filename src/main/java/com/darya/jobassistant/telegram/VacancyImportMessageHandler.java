package com.darya.jobassistant.telegram;

import com.darya.jobassistant.telegram.command.BotResponse;
import com.darya.jobassistant.telegram.format.VacancyDraftPreviewFormatter;
import com.darya.jobassistant.vacancyimport.ContinueVacancyImportUseCase;
import com.darya.jobassistant.vacancyimport.dto.ContinueVacancyImportResult;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;

/**
 * Handles a plain-text (non-command) message as a possible step of an in-progress vacancy
 * import. Delegates entirely to {@link ContinueVacancyImportUseCase} for the decision of what
 * the text actually represents (URL, description, or neither) - that decision depends on the
 * sender's current import-session state, which this adapter never inspects itself. Returns
 * {@link Optional#empty()} when there is no active session for the sender, so {@code
 * JobAssistantTelegramBot} can fall back to its existing behavior for ordinary chat messages
 * unrelated to any import.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VacancyImportMessageHandler {

    private static final String URL_ACCEPTED_MESSAGE = """
            ✅ Vacancy link received.

            Now send the full vacancy description in one message.

            Copy the job title, company, requirements, and description.
            Use /cancel to stop the import.""";

    private static final String EXTRACTION_FAILED_MESSAGE = """
            I couldn't recognize this vacancy right now.

            Use /add to start a new import.""";

    private static final String INVALID_URL_MESSAGE = """
            That does not look like a valid vacancy URL.

            Send one complete http:// or https:// link.
            Use /cancel to stop the import.""";

    private static final String INVALID_DESCRIPTION_MESSAGE = """
            The vacancy description is too short.

            Send the full job title, company, requirements, and description
            in one message, or use /cancel.""";

    private static final String SESSION_EXPIRED_MESSAGE = """
            This vacancy import has expired.

            Use /add to start a new one.""";

    private static final String EXTRACTING_MESSAGE = """
            The vacancy description has already been received.

            The vacancy is being processed.
            Use /cancel if you want to stop the import.""";

    private static final String WAITING_FOR_CONFIRMATION_MESSAGE = """
            The vacancy has already been recognized.

            Confirm, retry, or cancel the current import.""";

    private static final String GENERIC_ERROR_MESSAGE =
            "Something went wrong while processing your message. Please try again.";

    private final ContinueVacancyImportUseCase continueVacancyImportUseCase;
    private final VacancyDraftPreviewFormatter vacancyDraftPreviewFormatter;

    public Optional<BotResponse> handle(Message message) {
        long chatId = message.getChatId();
        long userId = message.getFrom().getId();
        try {
            ContinueVacancyImportResult result = continueVacancyImportUseCase.handleText(chatId, userId, message.getText());
            return toResponse(result);
        } catch (RuntimeException e) {
            log.error("Failed to process vacancy import message for chat {} user {}", chatId, userId, e);
            return Optional.of(BotResponse.text(GENERIC_ERROR_MESSAGE));
        }
    }

    private Optional<BotResponse> toResponse(ContinueVacancyImportResult result) {
        return switch (result) {
            case ContinueVacancyImportResult.NoActiveSession ignored -> Optional.empty();
            case ContinueVacancyImportResult.UrlAccepted ignored -> Optional.of(BotResponse.text(URL_ACCEPTED_MESSAGE));
            case ContinueVacancyImportResult.VacancyRecognized(var ignored, var draft) ->
                    Optional.of(BotResponse.text(vacancyDraftPreviewFormatter.formatRecognized(draft)));
            case ContinueVacancyImportResult.ExtractionFailed ignored ->
                    Optional.of(BotResponse.text(EXTRACTION_FAILED_MESSAGE));
            case ContinueVacancyImportResult.InvalidUrl ignored -> Optional.of(BotResponse.text(INVALID_URL_MESSAGE));
            case ContinueVacancyImportResult.InvalidDescription ignored ->
                    Optional.of(BotResponse.text(INVALID_DESCRIPTION_MESSAGE));
            case ContinueVacancyImportResult.SessionExpired ignored -> Optional.of(BotResponse.text(SESSION_EXPIRED_MESSAGE));
            case ContinueVacancyImportResult.UnexpectedState(var state) -> Optional.of(BotResponse.text(switch (state) {
                case EXTRACTING -> EXTRACTING_MESSAGE;
                case WAITING_FOR_CONFIRMATION -> WAITING_FOR_CONFIRMATION_MESSAGE;
                case WAITING_FOR_URL, WAITING_FOR_DESCRIPTION, COMPLETED, CANCELLED, FAILED, EXPIRED ->
                        throw new IllegalStateException("UnexpectedState result must never carry state " + state);
            }));
        };
    }
}
