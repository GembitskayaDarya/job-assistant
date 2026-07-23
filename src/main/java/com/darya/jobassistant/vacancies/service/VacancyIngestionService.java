package com.darya.jobassistant.vacancies.service;

import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.companies.service.CompanyService;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional
public class VacancyIngestionService {

    private final VacancyRepository vacancyRepository;
    private final CompanyService companyService;

    /**
     * Deduplicates by URL: an existing vacancy for the same URL is returned as-is,
     * never updated - matching the ingestion job's original upsert-free behavior.
     */
    public Vacancy persist(JobOffer jobOffer) {
        if (!StringUtils.hasText(jobOffer.url())) {
            throw new IllegalArgumentException("Job offer has no URL, cannot persist: " + jobOffer.title());
        }
        return vacancyRepository.findByUrl(jobOffer.url())
                .orElseGet(() -> save(jobOffer));
    }

    private Vacancy save(JobOffer jobOffer) {
        Company company = companyService.findOrCreateByName(jobOffer.company());
        Vacancy vacancy = Vacancy.builder()
                .company(company)
                .title(jobOffer.title())
                .description(jobOffer.description())
                .url(jobOffer.url())
                .source(jobOffer.source())
                .build();
        return vacancyRepository.save(vacancy);
    }
}
