package com.darya.jobassistant.vacancies.service;

import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.vacancies.dto.VacancyCreationCommand;
import com.darya.jobassistant.vacancies.dto.VacancyCreationResult;
import com.darya.jobassistant.vacancies.dto.VacancyIngestionResult;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.policy.JobOfferMatchPolicy;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Deliberately carries no {@code @Transactional}: every persistence operation - company
 * resolution/creation included - and its transactional correctness is now fully owned by {@link
 * VacancyCreationService#createIfAbsent}, which manages its own isolated {@code REQUIRES_NEW}
 * transaction internally (see that class's javadoc for why). This class only maps a {@link
 * JobOffer} into a provider-neutral {@link VacancyCreationCommand} and applies {@link
 * JobOfferMatchPolicy} - neither needs a surrounding transaction, and wrapping the whole {@link
 * #ingest} batch loop in one ambient transaction (as this class did before) would hold a database
 * connection open across many independent, already-self-contained creation attempts for no
 * benefit - and would have been exactly the kind of outer transaction that previously caused a
 * {@code vacancy_company_id_fkey} failure when company resolution lived here instead of inside
 * {@link VacancyCreationService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VacancyIngestionService {

    private final VacancyCreationService vacancyCreationService;
    private final JobOfferMatchPolicy jobOfferMatchPolicy;

    /**
     * Deduplicates by canonical URL (see {@link VacancyCreationService}): an existing vacancy for
     * an equivalent URL is returned as-is, never updated - matching the ingestion job's original
     * upsert-free behavior.
     */
    public Vacancy persist(JobOffer jobOffer) {
        return vacancyCreationService.createIfAbsent(buildCreationCommand(jobOffer)).vacancy();
    }

    /**
     * Batch counterpart used by automatic ingestion. Novelty is decided by {@link
     * VacancyCreationService#createIfAbsent}, which is safe under concurrent ingestion callers -
     * see that class for how the application-level canonical lookup and the database's partial
     * unique index combine to guarantee that.
     *
     * <p>Every fetched offer is checked against {@link JobOfferMatchPolicy} before persistence;
     * offers that don't match are skipped without creating a {@code Company} or {@code Vacancy}
     * record. {@code fetchedCount} still reflects every offer passed in, so after filtering
     * {@code fetchedCount == newlyPersisted + alreadyKnown} is no longer guaranteed - offers may
     * have been filtered out or failed during per-offer processing.
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
                VacancyCreationResult outcome = vacancyCreationService.createIfAbsent(buildCreationCommand(jobOffer));
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

    private VacancyCreationCommand buildCreationCommand(JobOffer jobOffer) {
        if (!StringUtils.hasText(jobOffer.url())) {
            throw new IllegalArgumentException("Job offer has no URL, cannot persist: " + jobOffer.title());
        }
        return new VacancyCreationCommand(
                jobOffer.company(),
                jobOffer.title(),
                jobOffer.description(),
                jobOffer.url(),
                null,
                null,
                null,
                null,
                null,
                null,
                jobOffer.source(),
                null);
    }
}
