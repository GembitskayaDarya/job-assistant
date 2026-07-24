package com.darya.jobassistant.telegram.command;

import com.darya.jobassistant.ai.AnalyzeVacancyUseCase;
import com.darya.jobassistant.ai.dto.AnalyzeVacancyResult;
import com.darya.jobassistant.telegram.format.JobMessageFormatter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.objects.message.Message;

/**
 * Delegates all analysis orchestration to the shared {@link AnalyzeVacancyUseCase} - the same use
 * case the guided-import "Analyze" callback uses - keeping only what is genuinely specific to this
 * command: parsing/validating its argument and mapping the provider-neutral result to a Telegram
 * message. This command knows nothing about import sessions.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyzeCommand implements TelegramCommand {

    private static final String USAGE_MESSAGE = "Usage: /analyze <vacancy UUID>";
    private static final String INVALID_ID_MESSAGE = "Invalid vacancy ID. Use the UUID shown in the search results.";
    private static final String NOT_FOUND_MESSAGE = "Vacancy not found. Run /search and use an ID from the results.";
    private static final String IN_PROGRESS_MESSAGE = "This vacancy is already being analyzed. Please try again shortly.";
    private static final String AI_FAILURE_MESSAGE = "Unable to analyze this job right now. Please try again later.";

    private final AnalyzeVacancyUseCase analyzeVacancyUseCase;
    private final JobMessageFormatter jobMessageFormatter;

    @Override
    public String name() {
        return "/analyze";
    }

    @Override
    public String description() {
        return "Analyze a vacancy against your profile, e.g. /analyze <vacancy UUID>";
    }

    @Override
    public BotResponse execute(Message message) {
        String[] parts = message.getText().trim().split("\\s+");
        if (parts.length != 2) {
            return BotResponse.text(USAGE_MESSAGE);
        }

        UUID vacancyId;
        try {
            vacancyId = UUID.fromString(parts[1]);
        } catch (IllegalArgumentException e) {
            return BotResponse.text(INVALID_ID_MESSAGE);
        }

        AnalyzeVacancyResult result = analyzeVacancyUseCase.analyze(vacancyId);
        return switch (result) {
            case AnalyzeVacancyResult.Available(var vacancy, var analysis, var ignored) ->
                    new BotResponse(jobMessageFormatter.format(vacancy, analysis), ParseMode.MARKDOWNV2, null);
            case AnalyzeVacancyResult.VacancyNotFound ignored -> {
                log.info("Vacancy not found for /analyze request: {}", vacancyId);
                yield BotResponse.text(NOT_FOUND_MESSAGE);
            }
            case AnalyzeVacancyResult.InProgress ignored -> BotResponse.text(IN_PROGRESS_MESSAGE);
            case AnalyzeVacancyResult.Failed ignored -> {
                log.warn("AI analysis failed for vacancy {}", vacancyId);
                yield BotResponse.text(AI_FAILURE_MESSAGE);
            }
        };
    }
}
