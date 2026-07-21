package com.darya.jobassistant.telegram.command;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;

@Component
public class CommandRegistry {

    private final List<TelegramCommand> commands;
    private final Map<String, TelegramCommand> commandsByName;

    public CommandRegistry(List<TelegramCommand> commands) {
        this.commands = commands;
        this.commandsByName = commands.stream()
                .collect(Collectors.toMap(TelegramCommand::name, Function.identity()));
    }

    public Optional<TelegramCommand> find(String name) {
        return Optional.ofNullable(commandsByName.get(name));
    }

    public List<BotCommand> toBotCommands() {
        return commands.stream()
                .map(c -> new BotCommand(c.name().substring(1), c.description()))
                .toList();
    }
}
