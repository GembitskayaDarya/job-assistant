package com.darya.jobassistant.telegram.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.darya.jobassistant.telegram.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@ExtendWith(MockitoExtension.class)
class StartCommandTest {

    @Mock
    private UserService userService;

    @Mock
    private Message message;

    @Mock
    private User sender;

    private StartCommand startCommand;

    @BeforeEach
    void setUp() {
        startCommand = new StartCommand(userService);
        when(message.getFrom()).thenReturn(sender);
        when(sender.getId()).thenReturn(1L);
    }

    @Test
    void execute_registersTheSenderAndReturnsAWelcomeMessage() {
        BotResponse response = startCommand.execute(message);

        assertThat(response.text()).contains("Welcome to Job Assistant");
    }

    @Test
    void execute_doesNotClaimVacancySourcingIsComingSoon() {
        // Guided vacancy import (/add) already exists - claiming it as "coming soon" would
        // directly contradict /help, which documents it as available today.
        BotResponse response = startCommand.execute(message);

        assertThat(response.text()).doesNotContain("Pull vacancies from LinkedIn, Greenhouse, Lever, and RemoteOK");
    }

    @Test
    void execute_pointsUsersToHelpForTheFullWalkthrough() {
        BotResponse response = startCommand.execute(message);

        assertThat(response.text()).contains("/help");
    }

    @Test
    void execute_doesNotPromiseUnsupportedFeatures() {
        BotResponse response = startCommand.execute(message);

        assertThat(response.text()).doesNotContainIgnoringCase("firecrawl");
    }
}
