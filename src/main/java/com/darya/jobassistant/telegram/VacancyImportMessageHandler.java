package com.darya.jobassistant.telegram;

import com.darya.jobassistant.telegram.command.BotResponse;
import com.darya.jobassistant.vacancyimport.ProvideVacancyUrlUseCase;
import com.darya.jobassistant.vacancyimport.dto.ProvideVacancyUrlResult;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;

/**
 * Handles a plain-text (non-command) message as a possible step of an in-progress vacancy
 * import. Returns {@link Optional#empty()} when there is no active session for the sender, so
 * {@code JobAssistantTelegramBot} can fall back to its existing behavior for ordinary chat
 * messages unrelated to any import - this component only ever produces a response when the
 * sender actually has an import in progress.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VacancyImportMessageHandler {

    private static final String ACCEPTED_MESSAGE = """
            ✅ Vacancy link received.

            Now send the full vacancy description in one message.

            Copy the job title, company, requirements, and description.
            Use /cancel to stop the import.""";

    private static final String INVALID_URL_MESSAGE = """
            That does not look like a valid vacancy URL.

            Send one complete http:// or https:// link.
            Use /cancel to stop the import.""";

    private static final String SESSION_EXPIRED_MESSAGE = """
            This vacancy import has expired.

            Use /add to start a new one.""";

    private static final String WAITING_FOR_DESCRIPTION_MESSAGE = """
            The vacancy link has already been received.

            Send the full vacancy description or use /cancel.""";

    private static final String EXTRACTING_MESSAGE = """
            The vacancy is currently being processed.

            Use /cancel if you want to stop the import.""";

    private static final String WAITING_FOR_CONFIRMATION_MESSAGE = """
            The vacancy has already been recognized.

            Confirm, retry, or cancel the current import.""";

    private static final String GENERIC_ERROR_MESSAGE =
            "Something went wrong while processing your message. Please try again.";

    private final ProvideVacancyUrlUseCase provideVacancyUrlUseCase;

    public Optional<BotResponse> handle(Message message) {
        long chatId = message.getChatId();
        long userId = message.getFrom().getId();
        try {
            ProvideVacancyUrlResult result = provideVacancyUrlUseCase.provideUrl(chatId, userId, message.getText());
            return toResponse(result);
        } catch (RuntimeException e) {
            log.error("Failed to process vacancy import message for chat {} user {}", chatId, userId, e);
            return Optional.of(BotResponse.text(GENERIC_ERROR_MESSAGE));
        }
    }

    private Optional<BotResponse> toResponse(ProvideVacancyUrlResult result) {
        return switch (result) {
            case ProvideVacancyUrlResult.NoActiveSession ignored -> Optional.empty();
            case ProvideVacancyUrlResult.Accepted ignored -> Optional.of(BotResponse.text(ACCEPTED_MESSAGE));
            case ProvideVacancyUrlResult.InvalidUrl ignored -> Optional.of(BotResponse.text(INVALID_URL_MESSAGE));
            case ProvideVacancyUrlResult.SessionExpired ignored -> Optional.of(BotResponse.text(SESSION_EXPIRED_MESSAGE));
            case ProvideVacancyUrlResult.UnexpectedState(var state) -> Optional.of(BotResponse.text(switch (state) {
                case WAITING_FOR_DESCRIPTION -> WAITING_FOR_DESCRIPTION_MESSAGE;
                case EXTRACTING -> EXTRACTING_MESSAGE;
                case WAITING_FOR_CONFIRMATION -> WAITING_FOR_CONFIRMATION_MESSAGE;
                case WAITING_FOR_URL, COMPLETED, CANCELLED, FAILED, EXPIRED -> throw new IllegalStateException(
                        "UnexpectedState result must never carry state " + state);
            }));
        };
    }
}
