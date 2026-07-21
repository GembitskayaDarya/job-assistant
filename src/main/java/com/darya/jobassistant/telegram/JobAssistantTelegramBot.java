package com.darya.jobassistant.telegram;

import com.darya.jobassistant.tracking.dto.ApplicationResponse;
import com.darya.jobassistant.tracking.service.ApplicationService;
import com.darya.jobassistant.util.TelegramMessageUtils;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Slf4j
public class JobAssistantTelegramBot extends TelegramLongPollingBot {

    private final String botUsername;
    private final ApplicationService applicationService;

    public JobAssistantTelegramBot(String botToken, String botUsername, ApplicationService applicationService) {
        super(botToken);
        this.botUsername = botUsername;
        this.applicationService = applicationService;
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }
        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText().trim();
        sendMessage(chatId, handleCommand(text, chatId));
    }

    private String handleCommand(String text, Long chatId) {
        if (text.startsWith("/start")) {
            return "Welcome to Job Assistant Bot! Send /help to see available commands.";
        }
        if (text.startsWith("/help")) {
            return "/list - list your tracked job applications\n/help - show this help";
        }
        if (text.startsWith("/list")) {
            return formatApplications(applicationService.findByTelegramChatId(chatId));
        }
        return "Sorry, I didn't understand that command. Send /help for a list of commands.";
    }

    private String formatApplications(List<ApplicationResponse> applications) {
        if (applications.isEmpty()) {
            return "You have no tracked job applications yet.";
        }
        return applications.stream()
                .map(a -> "%s @ %s - %s".formatted(
                        a.vacancyTitle() != null ? a.vacancyTitle() : "General application",
                        a.companyName(),
                        a.status()))
                .collect(Collectors.joining("\n"));
    }

    private void sendMessage(Long chatId, String text) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId.toString())
                .text(TelegramMessageUtils.truncate(text))
                .build();
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Failed to send Telegram message to chat {}", chatId, e);
        }
    }
}
