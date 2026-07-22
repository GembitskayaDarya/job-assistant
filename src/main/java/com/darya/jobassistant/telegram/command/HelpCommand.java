package com.darya.jobassistant.telegram.command;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@Component
@RequiredArgsConstructor
public class HelpCommand implements TelegramCommand {

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
        return BotResponse.text(commandsProvider.getObject().stream()
                .sorted(Comparator.comparing(TelegramCommand::name))
                .map(c -> "%s - %s".formatted(c.name(), c.description()))
                .collect(Collectors.joining("\n")));
    }
}
