package com.darya.jobassistant.scheduler;

import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.integrations.jobsource.JobSourcePort;
import com.darya.jobassistant.vacancies.service.VacancyIngestionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "jobsource.ingestion", name = "enabled", havingValue = "true")
public class VacancyIngestionJob {

    private final List<JobSourcePort> jobSources;
    private final VacancyIngestionService vacancyIngestionService;

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

        int processed = 0;
        for (JobOffer offer : offers) {
            if (persistSafely(offer, jobSource.sourceName())) {
                processed++;
            }
        }
        log.info("Processed {} vacancies from {} ({} fetched)", processed, jobSource.sourceName(), offers.size());
    }

    private boolean persistSafely(JobOffer offer, String sourceName) {
        try {
            vacancyIngestionService.persist(offer);
            return true;
        } catch (RuntimeException e) {
            log.warn("Skipping job offer from {} - failed to persist: {}", sourceName, e.getMessage());
            return false;
        }
    }
}
