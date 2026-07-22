package com.darya.jobassistant.integrations.jobsource;

import com.darya.jobassistant.exception.JobSourceNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual trigger for exercising a job source outside the scheduled ingestion job,
 * e.g. to check connectivity/mapping against the real API while developing an adapter.
 */
@RestController
@RequestMapping("/api/job-sources")
@RequiredArgsConstructor
public class JobSourceController {

    private final List<JobSourcePort> jobSources;

    @GetMapping
    public List<String> listSources() {
        return jobSources.stream().map(JobSourcePort::sourceName).toList();
    }

    @GetMapping("/{sourceName}/jobs")
    public List<JobOffer> fetchJobs(@PathVariable String sourceName) {
        return findSource(sourceName).fetchLatestPostings();
    }

    private JobSourcePort findSource(String sourceName) {
        return jobSources.stream()
                .filter(source -> source.sourceName().equalsIgnoreCase(sourceName))
                .findFirst()
                .orElseThrow(() -> new JobSourceNotFoundException(sourceName));
    }
}
