package com.darya.jobassistant.telegram.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@ExtendWith(MockitoExtension.class)
class HelpCommandTest {

    @Mock
    private ObjectProvider<List<TelegramCommand>> commandsProvider;

    @Mock
    private Message message;

    private HelpCommand helpCommand;

    @BeforeEach
    void setUp() {
        helpCommand = new HelpCommand(commandsProvider);
    }

    @Test
    void name_isHelp() {
        assertThat(helpCommand.name()).isEqualTo("/help");
    }

    @Test
    void execute_listsAddAndCancelAmongRegisteredCommands() {
        when(commandsProvider.getObject()).thenReturn(List.of(
                fakeCommand("/add", "Start importing a vacancy from a URL"),
                fakeCommand("/cancel", "Cancel the vacancy import currently in progress"),
                fakeCommand("/help", "Show available commands")));

        BotResponse response = helpCommand.execute(message);

        assertThat(response.text()).contains("/add - Start importing a vacancy from a URL");
        assertThat(response.text()).contains("/cancel - Cancel the vacancy import currently in progress");
    }

    @Test
    void execute_explainsThatBothUrlAndDescriptionAreRequired() {
        when(commandsProvider.getObject()).thenReturn(List.of(fakeCommand("/add", "Start importing a vacancy from a URL")));

        BotResponse response = helpCommand.execute(message);

        assertThat(response.text()).contains("Send the vacancy URL");
        assertThat(response.text()).contains("Send the full vacancy description");
        assertThat(response.text()).contains("paste it yourself");
    }

    @Test
    void execute_mentionsSaveAndAnalyze() {
        when(commandsProvider.getObject()).thenReturn(List.of(fakeCommand("/add", "Start importing a vacancy from a URL")));

        BotResponse response = helpCommand.execute(message);

        assertThat(response.text()).contains("Save");
        assertThat(response.text()).contains("Analyze");
    }

    @Test
    void execute_doesNotMentionFirecrawlOrAutomaticScraping() {
        when(commandsProvider.getObject()).thenReturn(List.of(fakeCommand("/add", "Start importing a vacancy from a URL")));

        BotResponse response = helpCommand.execute(message);

        assertThat(response.text()).doesNotContainIgnoringCase("firecrawl");
        assertThat(response.text()).doesNotContainIgnoringCase("automatically scrape");
        assertThat(response.text()).doesNotContainIgnoringCase("automatically open");
        assertThat(response.text()).contains("does not open or scrape the link");
    }

    @Test
    void execute_usesThePlainTextBotResponseAbstraction() {
        when(commandsProvider.getObject()).thenReturn(List.of(fakeCommand("/add", "desc")));

        BotResponse response = helpCommand.execute(message);

        assertThat(response.parseMode()).isNull();
        assertThat(response.keyboard()).isNull();
    }

    @Test
    void execute_commandsProviderThrows_returnsSafeGenericMessageInstead() {
        when(commandsProvider.getObject()).thenThrow(new RuntimeException("bean lookup failed"));

        BotResponse response = helpCommand.execute(message);

        assertThat(response.text()).isEqualTo("Something went wrong while building the help message. Please try again.");
        assertThat(response.text()).doesNotContain("bean lookup failed");
    }

    private TelegramCommand fakeCommand(String name, String description) {
        return new TelegramCommand() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return description;
            }

            @Override
            public BotResponse execute(Message message) {
                return BotResponse.text("");
            }
        };
    }
}
