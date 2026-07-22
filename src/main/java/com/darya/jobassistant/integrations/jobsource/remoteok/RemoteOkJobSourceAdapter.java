package com.darya.jobassistant.integrations.jobsource.remoteok;

import com.darya.jobassistant.integrations.jobsource.JobPosting;
import com.darya.jobassistant.integrations.jobsource.JobSourcePort;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class RemoteOkJobSourceAdapter implements JobSourcePort {

    private static final String SOURCE_NAME = "remoteok";
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; job-assistant/1.0)";

    private final WebClient webClient;
    private final RemoteOkProperties remoteOkProperties;

    @Override
    public String sourceName() {
        return SOURCE_NAME;
    }

    @Override
    public List<JobPosting> fetchLatestPostings() {
        RemoteOkJobDto[] jobs = webClient.get()
                .uri(remoteOkProperties.baseUrl())
                .header(HttpHeaders.USER_AGENT, USER_AGENT)
                .retrieve()
                .bodyToMono(RemoteOkJobDto[].class)
                .block();

        if (jobs == null) {
            return List.of();
        }

        return Arrays.stream(jobs)
                .filter(job -> StringUtils.hasText(job.company())
                        && StringUtils.hasText(job.position())
                        && StringUtils.hasText(job.url()))
                .map(this::toJobPosting)
                .toList();
    }

    private JobPosting toJobPosting(RemoteOkJobDto job) {
        return new JobPosting(
                job.company(),
                job.position(),
                job.description(),
                job.url(),
                job.salaryMin(),
                job.salaryMax(),
                "USD",
                parsePostedAt(job.date())
        );
    }

    private LocalDate parsePostedAt(String date) {
        if (!StringUtils.hasText(date)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(date).toLocalDate();
        } catch (DateTimeParseException e) {
            log.debug("Could not parse RemoteOK posting date '{}'", date, e);
            return null;
        }
    }
}
