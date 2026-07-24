package com.darya.jobassistant.telegram.command;

import com.darya.jobassistant.vacancyimport.StartVacancyImportUseCase;
import com.darya.jobassistant.vacancyimport.dto.StartVacancyImportResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@Slf4j
@Component
@RequiredArgsConstructor
public class AddCommand implements TelegramCommand {

    private static final String NEW_IMPORT_MESSAGE = """
            Send the vacancy URL.

            You can use a link from LinkedIn, Just Join IT, No Fluff Jobs,
            Pracuj.pl, Inhire, Remote OK, or another job site.

            Use /cancel to stop the import.""";

    private static final String WAITING_FOR_URL_MESSAGE = """
            A vacancy import is already active.

            Send the vacancy URL or use /cancel.""";

    private static final String WAITING_FOR_DESCRIPTION_MESSAGE = """
            A vacancy import is already active.

            Send the full vacancy description or use /cancel.""";

    private static final String EXTRACTING_MESSAGE = """
            The vacancy is currently being processed.

            Use /cancel if you want to stop the import.""";

    private static final String WAITING_FOR_CONFIRMATION_MESSAGE = """
            The vacancy has already been recognized.

            Confirm, retry, or cancel the current import.""";

    private static final String GENERIC_ERROR_MESSAGE =
            "Something went wrong while starting the vacancy import. Please try again.";

    private final StartVacancyImportUseCase startVacancyImportUseCase;

    @Override
    public String name() {
        return "/add";
    }

    @Override
    public String description() {
        return "Start importing a vacancy from a URL";
    }

    @Override
    public BotResponse execute(Message message) {
        long chatId = message.getChatId();
        long userId = message.getFrom().getId();
        try {
            StartVacancyImportResult result = startVacancyImportUseCase.start(chatId, userId);
            return BotResponse.text(toMessage(result));
        } catch (RuntimeException e) {
            log.error("Failed to start vacancy import for chat {} user {}", chatId, userId, e);
            return BotResponse.text(GENERIC_ERROR_MESSAGE);
        }
    }

    private String toMessage(StartVacancyImportResult result) {
        return switch (result) {
            case StartVacancyImportResult.Started started -> NEW_IMPORT_MESSAGE;
            case StartVacancyImportResult.AlreadyActive(var sessionId, var state) -> switch (state) {
                case WAITING_FOR_URL -> WAITING_FOR_URL_MESSAGE;
                case WAITING_FOR_DESCRIPTION -> WAITING_FOR_DESCRIPTION_MESSAGE;
                case EXTRACTING -> EXTRACTING_MESSAGE;
                case WAITING_FOR_CONFIRMATION -> WAITING_FOR_CONFIRMATION_MESSAGE;
                case COMPLETED, CANCELLED, FAILED, EXPIRED -> throw new IllegalStateException(
                        "AlreadyActive result must never carry a terminal state, but was " + state);
            };
        };
    }
}
