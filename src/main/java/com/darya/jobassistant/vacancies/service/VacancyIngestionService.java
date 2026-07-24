package com.darya.jobassistant.vacancies.service;

import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.companies.service.CompanyService;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.vacancies.dto.VacancyIngestionResult;
import com.darya.jobassistant.vacancies.dto.VacancyPersistenceResult;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.repository.VacancyRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
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
        return persistOrGetExisting(jobOffer).vacancy();
    }

    /**
     * Batch counterpart used by automatic ingestion. Unlike {@link #persist(JobOffer)}, novelty
     * is decided atomically by the database via {@link VacancyRepository#saveIfAbsent} - there is
     * no preliminary existence check, so this is safe under concurrent ingestion callers.
     */
    public VacancyIngestionResult ingest(List<JobOffer> jobOffers) {
        List<Vacancy> persisted = new ArrayList<>();
        int alreadyKnown = 0;
        for (JobOffer jobOffer : jobOffers) {
            try {
                VacancyPersistenceResult outcome = insertIfAbsent(jobOffer);
                if (outcome.isInserted()) {
                    persisted.add(outcome.vacancy());
                } else {
                    alreadyKnown++;
                }
            } catch (RuntimeException e) {
                log.warn("Skipping job offer \"{}\" from {} - failed to persist: {}",
                        jobOffer.title(), jobOffer.source(), e.getMessage());
            }
        }
        return new VacancyIngestionResult(jobOffers.size(), persisted, alreadyKnown);
    }

    private PersistOutcome persistOrGetExisting(JobOffer jobOffer) {
        requireUrl(jobOffer);
        return vacancyRepository.findByUrl(jobOffer.url())
                .map(existing -> new PersistOutcome(existing, false))
                .orElseGet(() -> new PersistOutcome(save(jobOffer), true));
    }

    private VacancyPersistenceResult insertIfAbsent(JobOffer jobOffer) {
        requireUrl(jobOffer);
        return vacancyRepository.saveIfAbsent(buildVacancy(jobOffer));
    }

    private void requireUrl(JobOffer jobOffer) {
        if (!StringUtils.hasText(jobOffer.url())) {
            throw new IllegalArgumentException("Job offer has no URL, cannot persist: " + jobOffer.title());
        }
    }

    private Vacancy save(JobOffer jobOffer) {
        return vacancyRepository.save(buildVacancy(jobOffer));
    }

    private Vacancy buildVacancy(JobOffer jobOffer) {
        Company company = companyService.findOrCreateByName(jobOffer.company());
        return Vacancy.builder()
                .company(company)
                .title(jobOffer.title())
                .description(jobOffer.description())
                .url(jobOffer.url())
                .source(jobOffer.source())
                .build();
    }

    private record PersistOutcome(Vacancy vacancy, boolean newlyPersisted) {
    }
}
