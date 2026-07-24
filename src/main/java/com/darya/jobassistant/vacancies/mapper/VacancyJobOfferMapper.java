package com.darya.jobassistant.vacancies.mapper;

import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Vacancy has no distinct tags field, so {@link JobOffer#tags()} is always empty here - this
 * preserves current model truth rather than inventing data.
 */
@Component
public class VacancyJobOfferMapper {

    public JobOffer toJobOffer(Vacancy vacancy) {
        return new JobOffer(
                vacancy.getId().toString(),
                vacancy.getTitle(),
                vacancy.getCompany().getName(),
                vacancy.getLocation(),
                salary(vacancy),
                vacancy.getDescription(),
                vacancy.getUrl(),
                vacancy.getSource(),
                List.of());
    }

    /**
     * Prefers the free-text {@code salaryText} captured verbatim from a guided import, copying it
     * through unmodified; falls back to formatting {@code salaryMin}/{@code salaryMax}/
     * {@code currency} only for vacancies that never had a {@code salaryText} (e.g. structured
     * job-board sources like RemoteOK).
     */
    private String salary(Vacancy vacancy) {
        String salaryText = vacancy.getSalaryText();
        if (salaryText != null && !salaryText.isBlank()) {
            return salaryText;
        }
        return formatSalary(vacancy);
    }

    private String formatSalary(Vacancy vacancy) {
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
