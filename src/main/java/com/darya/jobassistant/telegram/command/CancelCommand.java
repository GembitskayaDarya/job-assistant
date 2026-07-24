package com.darya.jobassistant.telegram.command;

import com.darya.jobassistant.vacancyimport.CancelVacancyImportUseCase;
import com.darya.jobassistant.vacancyimport.dto.CancelVacancyImportResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@Slf4j
@Component
@RequiredArgsConstructor
public class CancelCommand implements TelegramCommand {

    private static final String CANCELLED_MESSAGE = "Vacancy import cancelled.";

    private static final String NO_ACTIVE_IMPORT_MESSAGE = """
            There is no active vacancy import.

            Use /add to start one.""";

    private static final String GENERIC_ERROR_MESSAGE =
            "Something went wrong while cancelling the vacancy import. Please try again.";

    private final CancelVacancyImportUseCase cancelVacancyImportUseCase;

    @Override
    public String name() {
        return "/cancel";
    }

    @Override
    public String description() {
        return "Cancel the vacancy import currently in progress";
    }

    @Override
    public BotResponse execute(Message message) {
        long chatId = message.getChatId();
        long userId = message.getFrom().getId();
        try {
            CancelVacancyImportResult result = cancelVacancyImportUseCase.cancel(chatId, userId);
            return BotResponse.text(toMessage(result));
        } catch (RuntimeException e) {
            log.error("Failed to cancel vacancy import for chat {} user {}", chatId, userId, e);
            return BotResponse.text(GENERIC_ERROR_MESSAGE);
        }
    }

    private String toMessage(CancelVacancyImportResult result) {
        return switch (result) {
            case CANCELLED -> CANCELLED_MESSAGE;
            case NO_ACTIVE_SESSION -> NO_ACTIVE_IMPORT_MESSAGE;
        };
    }
}
