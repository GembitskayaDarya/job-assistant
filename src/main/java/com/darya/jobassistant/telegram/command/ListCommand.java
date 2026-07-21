package com.darya.jobassistant.telegram.command;

import com.darya.jobassistant.tracking.dto.ApplicationResponse;
import com.darya.jobassistant.tracking.service.ApplicationService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Message;

@Component
@RequiredArgsConstructor
public class ListCommand implements TelegramCommand {

    private final ApplicationService applicationService;

    @Override
    public String name() {
        return "/list";
    }

    @Override
    public String description() {
        return "List your tracked job applications";
    }

    @Override
    public String execute(Message message) {
        return formatApplications(applicationService.findByTelegramChatId(message.getChatId()));
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
}
