package com.darya.jobassistant.integrations.notifier;

import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import org.springframework.stereotype.Component;

@Component
public class JobNotificationFactory {

    public JobNotification create(Vacancy vacancy, JobAnalysis analysis, Long recipientChatId) {
        return new JobNotification(
                vacancy.getId(),
                recipientChatId,
                vacancy.getTitle(),
                vacancy.getCompany().getName(),
                vacancy.getUrl(),
                analysis.score(),
                analysis.summary(),
                analysis.pros(),
                analysis.cons(),
                analysis.missingRequiredSkills(),
                analysis.missingPreferredSkills(),
                analysis.experienceAssessment(),
                analysis.preferencesAssessment());
    }
}
