package com.darya.jobassistant.scheduler;

import com.darya.jobassistant.companies.entity.Company;
import com.darya.jobassistant.companies.service.CompanyService;
import com.darya.jobassistant.integrations.jobsource.JobPosting;
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
        List<JobPosting> postings;
        try {
            postings = jobSource.fetchLatestPostings();
        } catch (Exception e) {
            log.warn("Failed to fetch postings from {}", jobSource.sourceName(), e);
            return;
        }

        int saved = 0;
        for (JobPosting posting : postings) {
            if (StringUtils.hasText(posting.url()) && !vacancyService.existsByUrl(posting.url())) {
                saveVacancy(posting, jobSource.sourceName());
                saved++;
            }
        }
        log.info("Ingested {} new vacancies from {} ({} fetched)", saved, jobSource.sourceName(), postings.size());
    }

    private void saveVacancy(JobPosting posting, String sourceName) {
        Company company = companyService.findOrCreateByName(posting.companyName());
        Vacancy vacancy = Vacancy.builder()
                .company(company)
                .title(posting.title())
                .description(posting.description())
                .url(posting.url())
                .salaryMin(posting.salaryMin())
                .salaryMax(posting.salaryMax())
                .currency(posting.currency())
                .source(sourceName)
                .postedAt(posting.postedAt())
                .build();
        vacancyService.save(vacancy);
    }
}
