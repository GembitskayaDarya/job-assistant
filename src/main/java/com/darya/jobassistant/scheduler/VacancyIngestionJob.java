package com.darya.jobassistant.scheduler;

import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.companies.service.CompanyService;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.integrations.jobsource.JobSourcePort;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.service.VacancyService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "jobsource.ingestion", name = "enabled", havingValue = "true")
public class VacancyIngestionJob {

    private final List<JobSourcePort> jobSources;
    private final CompanyService companyService;
    private final VacancyService vacancyService;

    @Scheduled(
            fixedDelayString = "${jobsource.ingestion.fixed-delay-ms}",
            initialDelayString = "${jobsource.ingestion.initial-delay-ms}"
    )
    public void ingest() {
        for (JobSourcePort jobSource : jobSources) {
            ingestFrom(jobSource);
        }
    }

    private void ingestFrom(JobSourcePort jobSource) {
        List<JobOffer> offers;
        try {
            offers = jobSource.fetchLatestPostings();
        } catch (Exception e) {
            log.warn("Failed to fetch postings from {}", jobSource.sourceName(), e);
            return;
        }

        int saved = 0;
        for (JobOffer offer : offers) {
            if (StringUtils.hasText(offer.url()) && !vacancyService.existsByUrl(offer.url())) {
                saveVacancy(offer, jobSource.sourceName());
                saved++;
            }
        }
        log.info("Ingested {} new vacancies from {} ({} fetched)", saved, jobSource.sourceName(), offers.size());
    }

    private void saveVacancy(JobOffer offer, String sourceName) {
        Company company = companyService.findOrCreateByName(offer.company());
        Vacancy vacancy = Vacancy.builder()
                .company(company)
                .title(offer.title())
                .description(offer.description())
                .url(offer.url())
                .source(sourceName)
                .build();
        vacancyService.save(vacancy);
    }
}
