package com.darya.jobassistant.vacancies.service;

import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.companies.service.CompanyService;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.vacancies.dto.VacancyCreationResult;
import com.darya.jobassistant.vacancies.dto.VacancyIngestionResult;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.policy.JobOfferMatchPolicy;
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

    private final VacancyCreationService vacancyCreationService;
    private final CompanyService companyService;
    private final JobOfferMatchPolicy jobOfferMatchPolicy;

    /**
     * Deduplicates by canonical URL (see {@link VacancyCreationService}): an existing vacancy for
     * an equivalent URL is returned as-is, never updated - matching the ingestion job's original
     * upsert-free behavior.
     */
    public Vacancy persist(JobOffer jobOffer) {
        requireUrl(jobOffer);
        return vacancyCreationService.createIfAbsent(buildVacancy(jobOffer)).vacancy();
    }

    /**
     * Batch counterpart used by automatic ingestion. Novelty is decided by {@link
     * VacancyCreationService#createIfAbsent}, which is safe under concurrent ingestion callers -
     * see that class for how the application-level canonical lookup and the database's partial
     * unique index combine to guarantee that.
     *
     * <p>Every fetched offer is checked against {@link JobOfferMatchPolicy} before company
     * resolution or persistence; offers that don't match are skipped without creating a
     * {@code Company} or {@code Vacancy} record. {@code fetchedCount} still reflects every offer
     * passed in, so after filtering {@code fetchedCount == newlyPersisted + alreadyKnown} is no
     * longer guaranteed - offers may have been filtered out or failed during per-offer processing.
     */
    public VacancyIngestionResult ingest(List<JobOffer> jobOffers) {
        List<Vacancy> persisted = new ArrayList<>();
        int alreadyKnown = 0;
        for (JobOffer jobOffer : jobOffers) {
            if (!jobOfferMatchPolicy.matches(jobOffer)) {
                log.debug("Filtered out job offer from {} (id={}, url={}): \"{}\"",
                        jobOffer.source(), jobOffer.id(), jobOffer.url(), jobOffer.title());
                continue;
            }
            try {
                requireUrl(jobOffer);
                VacancyCreationResult outcome = vacancyCreationService.createIfAbsent(buildVacancy(jobOffer));
                if (outcome.newlyCreated()) {
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

    private void requireUrl(JobOffer jobOffer) {
        if (!StringUtils.hasText(jobOffer.url())) {
            throw new IllegalArgumentException("Job offer has no URL, cannot persist: " + jobOffer.title());
        }
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
}
