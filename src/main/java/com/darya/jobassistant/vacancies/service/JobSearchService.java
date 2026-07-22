package com.darya.jobassistant.vacancies.service;

import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.integrations.jobsource.JobSourceException;
import com.darya.jobassistant.integrations.jobsource.JobSourcePort;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobSearchService {

    private final List<JobSourcePort> jobSources;

    public List<JobOffer> search(String keyword) {
        String normalizedKeyword = keyword.toLowerCase(Locale.ROOT);
        return jobSources.stream()
                .flatMap(source -> fetchSafely(source).stream())
                .filter(job -> matches(job, normalizedKeyword))
                .toList();
    }

    private List<JobOffer> fetchSafely(JobSourcePort source) {
        try {
            return source.fetchLatestPostings();
        } catch (JobSourceException e) {
            log.warn("Skipping job source {} during search: {}", source.sourceName(), e.getMessage());
            return List.of();
        }
    }

    private boolean matches(JobOffer job, String normalizedKeyword) {
        return containsIgnoreCase(job.title(), normalizedKeyword)
                || containsIgnoreCase(job.company(), normalizedKeyword)
                || containsIgnoreCase(job.description(), normalizedKeyword);
    }

    private boolean containsIgnoreCase(String value, String normalizedKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedKeyword);
    }
}
