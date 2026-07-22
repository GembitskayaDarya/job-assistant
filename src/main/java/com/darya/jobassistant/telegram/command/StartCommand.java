package com.darya.jobassistant.telegram.command;

import com.darya.jobassistant.telegram.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

@Component
@RequiredArgsConstructor
public class StartCommand implements TelegramCommand {

    private static final String WELCOME_MESSAGE = """
            *Welcome to Job Assistant* 👋

            I bring some engineering discipline to your job search — application tracking, interview scheduling, and, soon, automated vacancy sourcing and AI-drafted cover letters, all from Telegram instead of a spreadsheet.

            *Right now I can:*
            • Track applications — company, status, dates, notes (`/list`)
            • Keep interviews linked to the applications they belong to

            *Coming soon:*
            • Pull vacancies from LinkedIn, Greenhouse, Lever, and RemoteOK
            • Store your CV and match it against open roles
            • Draft cover letters with OpenAI

            Pick a starting point below, or send `/help` for the full command list.""";

    private final UserService userService;

    @Override
    public String name() {
        return "/start";
    }

    @Override
    public String description() {
        return "Start using the bot";
    }

    @Override
    public BotResponse execute(Message message) {
        User sender = message.getFrom();
        userService.registerOrUpdate(sender.getId(), sender.getUserName(), sender.getFirstName(), sender.getLastName());
        return new BotResponse(WELCOME_MESSAGE, ParseMode.MARKDOWN, welcomeKeyboard());
    }

    private InlineKeyboardMarkup welcomeKeyboard() {
        return InlineKeyboardMarkup.builder()
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder().text("🔍 Search jobs").callbackData("search_jobs").build(),
                        InlineKeyboardButton.builder().text("📄 My CV").callbackData("my_cv").build()))
                .keyboardRow(new InlineKeyboardRow(
                        InlineKeyboardButton.builder().text("📊 Applications").callbackData("applications").build(),
                        InlineKeyboardButton.builder().text("⚙ Settings").callbackData("settings").build()))
                .build();
    }
}
