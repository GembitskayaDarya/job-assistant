package com.darya.jobassistant.telegram.command;

import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.vacancies.service.JobSearchService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@Component
@RequiredArgsConstructor
public class SearchCommand implements TelegramCommand {

    private static final int MAX_RESULTS = 5;

    private final JobSearchService jobSearchService;

    @Override
    public String name() {
        return "/search";
    }

    @Override
    public String description() {
        return "Search open jobs by keyword, e.g. /search backend";
    }

    @Override
    public BotResponse execute(Message message) {
        String keyword = extractKeyword(message.getText());
        if (keyword.isBlank()) {
            return BotResponse.text("Please provide a keyword, e.g. /search backend");
        }

        List<JobOffer> jobs = jobSearchService.search(keyword);
        if (jobs.isEmpty()) {
            return BotResponse.text("No jobs found for \"%s\".".formatted(keyword));
        }

        String text = jobs.stream()
                .limit(MAX_RESULTS)
                .map(this::formatJob)
                .collect(Collectors.joining("\n\n"));
        return new BotResponse(text, ParseMode.MARKDOWN, null);
    }

    private String extractKeyword(String text) {
        String[] parts = text.trim().split("\\s+", 2);
        return parts.length < 2 ? "" : parts[1].trim();
    }

    private String formatJob(JobOffer job) {
        StringBuilder message = new StringBuilder("*%s*".formatted(escape(job.title())));
        if (job.company() != null) {
            message.append(" @ ").append(escape(job.company()));
        }
        if (job.salary() != null) {
            message.append("\n").append(escape(job.salary()));
        }
        message.append("\n").append(job.url());
        return message.toString();
    }

    private String escape(String text) {
        return text == null ? "" : text.replace("*", "\\*").replace("_", "\\_").replace("[", "\\[");
    }
}
