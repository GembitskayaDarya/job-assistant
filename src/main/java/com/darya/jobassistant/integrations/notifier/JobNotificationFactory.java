package com.darya.jobassistant.integrations.notifier;

import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancyextraction.model.RemotePolicy;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class JobNotificationFactory {

    public JobNotification create(Vacancy vacancy, JobAnalysis analysis, Long recipientChatId) {
        return new JobNotification(
                vacancy.getId(),
                recipientChatId,
                vacancy.getTitle(),
                vacancy.getCompany().getName(),
                vacancy.getUrl(),
                analysis);
    }

    /**
     * Builds the automatic recommendation workflow's compact, single-message-bound counterpart to
     * {@link #create}. {@code reason}/{@code strengths}/{@code risks} are deliberately mapped from
     * only three of {@link JobAnalysis}'s fields (summary/pros/cons) - {@link
     * CompactVacancyRecommendation} is not a general analysis projection, and {@code
     * CompactRecommendationTelegramFormatter} bounds each of these independently of how long the
     * underlying AI content actually is.
     */
    public CompactVacancyRecommendation createCompactRecommendation(Vacancy vacancy, JobAnalysis analysis, Long recipientChatId) {
        return new CompactVacancyRecommendation(
                vacancy.getId(),
                recipientChatId,
                vacancy.getTitle(),
                vacancy.getCompany().getName(),
                vacancy.getUrl(),
                analysis.score(),
                analysis.summary(),
                analysis.pros(),
                analysis.cons(),
                vacancy.getLocation(),
                remoteModeDisplay(vacancy.getRemoteMode()),
                salaryDisplay(vacancy));
    }

    private String remoteModeDisplay(RemotePolicy remoteMode) {
        return remoteMode == null || remoteMode == RemotePolicy.UNSPECIFIED ? null : remoteMode.name();
    }

    private String salaryDisplay(Vacancy vacancy) {
        if (StringUtils.hasText(vacancy.getSalaryText())) {
            return vacancy.getSalaryText();
        }
        BigDecimal min = vacancy.getSalaryMin();
        BigDecimal max = vacancy.getSalaryMax();
        if (min == null && max == null) {
            return null;
        }
        StringBuilder salary = new StringBuilder();
        if (min != null) {
            salary.append(min);
        }
        if (max != null) {
            if (!salary.isEmpty()) {
                salary.append(" - ");
            }
            salary.append(max);
        }
        if (vacancy.getCurrency() != null) {
            salary.append(" ").append(vacancy.getCurrency());
        }
        return salary.toString();
    }
}
