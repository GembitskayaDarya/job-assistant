package com.darya.jobassistant.telegram.command;

import com.darya.jobassistant.vacancies.dto.VacancyResponse;
import com.darya.jobassistant.vacancies.service.JobSearchService;
import java.math.BigDecimal;
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
        return "Search open vacancies by keyword, e.g. /search backend";
    }

    @Override
    public BotResponse execute(Message message) {
        String keyword = extractKeyword(message.getText());
        if (keyword.isBlank()) {
            return BotResponse.text("Please provide a keyword, e.g. /search backend");
        }

        List<VacancyResponse> vacancies = jobSearchService.search(keyword);
        if (vacancies.isEmpty()) {
            return BotResponse.text("No vacancies found for \"%s\".".formatted(keyword));
        }

        String text = vacancies.stream()
                .limit(MAX_RESULTS)
                .map(this::formatVacancy)
                .collect(Collectors.joining("\n\n"));
        return new BotResponse(text, ParseMode.MARKDOWN, null);
    }

    private String extractKeyword(String text) {
        String[] parts = text.trim().split("\\s+", 2);
        return parts.length < 2 ? "" : parts[1].trim();
    }

    private String formatVacancy(VacancyResponse vacancy) {
        StringBuilder message = new StringBuilder("*%s*".formatted(escape(vacancy.title())));
        if (vacancy.companyName() != null) {
            message.append(" @ ").append(escape(vacancy.companyName()));
        }
        String salary = formatSalary(vacancy.salaryMin(), vacancy.salaryMax(), vacancy.currency());
        if (salary != null) {
            message.append("\n").append(salary);
        }
        if (vacancy.url() != null) {
            message.append("\n").append(vacancy.url());
        }
        return message.toString();
    }

    private String formatSalary(BigDecimal min, BigDecimal max, String currency) {
        if (min == null && max == null) {
            return null;
        }
        String range = min != null && max != null
                ? "%s - %s".formatted(min.toPlainString(), max.toPlainString())
                : (min != null ? min : max).toPlainString();
        return currency != null ? "%s %s".formatted(range, currency) : range;
    }

    private String escape(String text) {
        return text == null ? "" : text.replace("*", "\\*").replace("_", "\\_").replace("[", "\\[");
    }
}
