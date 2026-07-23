package com.darya.jobassistant.vacancies.service;

import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.integrations.jobsource.JobSourceException;
import com.darya.jobassistant.integrations.jobsource.JobSourcePort;
import com.darya.jobassistant.vacancies.dto.SearchResult;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobSearchService {

    private final List<JobSourcePort> jobSources;
    private final VacancyIngestionService vacancyIngestionService;

    public List<SearchResult> search(String keyword) {
        String normalizedKeyword = keyword.toLowerCase(Locale.ROOT);
        return jobSources.stream()
                .flatMap(source -> fetchSafely(source).stream())
                .filter(job -> matches(job, normalizedKeyword))
                .map(this::persistSafely)
                .flatMap(Optional::stream)
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

    private Optional<SearchResult> persistSafely(JobOffer job) {
        try {
            Vacancy vacancy = vacancyIngestionService.persist(job);
            return Optional.of(new SearchResult(job, vacancy.getId()));
        } catch (RuntimeException e) {
            log.warn("Skipping job \"{}\" from {} - failed to persist: {}", job.title(), job.source(), e.getMessage());
            return Optional.empty();
        }
    }
}
