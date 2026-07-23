package com.darya.jobassistant.telegram.command;

import com.darya.jobassistant.ai.exception.JobAnalysisException;
import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.CandidateProfileProvider;
import com.darya.jobassistant.exception.VacancyNotFoundException;
import com.darya.jobassistant.integrations.ai.openai.JobAnalysisService;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.telegram.format.JobMessageFormatter;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.mapper.VacancyJobOfferMapper;
import com.darya.jobassistant.vacancies.service.VacancyQueryService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.objects.message.Message;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalyzeCommand implements TelegramCommand {

    private static final String USAGE_MESSAGE = "Usage: /analyze <vacancy UUID>";
    private static final String INVALID_ID_MESSAGE = "Invalid vacancy ID. Use the UUID shown in the search results.";
    private static final String NOT_FOUND_MESSAGE = "Vacancy not found. Run /search and use an ID from the results.";
    private static final String AI_FAILURE_MESSAGE = "Unable to analyze this job right now. Please try again later.";

    private final VacancyQueryService vacancyQueryService;
    private final VacancyJobOfferMapper vacancyJobOfferMapper;
    private final CandidateProfileProvider candidateProfileProvider;
    private final JobAnalysisService jobAnalysisService;
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

        Vacancy vacancy;
        try {
            vacancy = vacancyQueryService.getById(vacancyId);
        } catch (VacancyNotFoundException e) {
            log.info("Vacancy not found for /analyze request: {}", vacancyId);
            return BotResponse.text(NOT_FOUND_MESSAGE);
        }

        JobOffer jobOffer = vacancyJobOfferMapper.toJobOffer(vacancy);
        CandidateProfile candidateProfile = candidateProfileProvider.getProfile();

        JobAnalysis analysis;
        try {
            analysis = jobAnalysisService.analyze(candidateProfile, jobOffer);
        } catch (JobAnalysisException e) {
            log.warn("AI analysis failed for vacancy {}", vacancyId, e);
            return BotResponse.text(AI_FAILURE_MESSAGE);
        }

        String text = jobMessageFormatter.format(jobOffer, analysis);
        return new BotResponse(text, ParseMode.MARKDOWNV2, null);
    }
}
