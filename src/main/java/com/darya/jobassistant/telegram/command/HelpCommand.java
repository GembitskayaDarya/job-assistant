package com.darya.jobassistant.telegram.command;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;

/**
 * The command list itself is generated from every registered {@link TelegramCommand}, so it can
 * never drift out of sync with what the bot actually supports. The guided-import walkthrough
 * below it is the one place that explanation lives - {@code StartCommand} points here rather than
 * repeating it, so there is exactly one copy to keep accurate.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HelpCommand implements TelegramCommand {

    private static final String GUIDED_IMPORT_GUIDE = """
            How to add a vacancy:
            1. Send /add
            2. Send the vacancy URL
            3. Send the full vacancy description (paste it yourself - the bot does not open or scrape the link)
            4. Review the recognized details
            5. Press Save
            6. Press Analyze to compare it against your profile

            Use /cancel at any time to stop an import in progress.""";

    private static final String GENERIC_ERROR_MESSAGE = "Something went wrong while building the help message. Please try again.";

    private final ObjectProvider<List<TelegramCommand>> commandsProvider;

    @Override
    public String name() {
        return "/help";
    }

    @Override
    public String description() {
        return "Show available commands";
    }

    @Override
    public BotResponse execute(Message message) {
        try {
            String commandList = commandsProvider.getObject().stream()
                    .sorted(Comparator.comparing(TelegramCommand::name))
                    .map(c -> "%s - %s".formatted(c.name(), c.description()))
                    .collect(Collectors.joining("\n"));
            return BotResponse.text(GUIDED_IMPORT_GUIDE + "\n\nCommands:\n" + commandList);
        } catch (RuntimeException e) {
            log.error("Failed to build help message", e);
            return BotResponse.text(GENERIC_ERROR_MESSAGE);
        }
    }
}
