package com.darya.jobassistant.integrations.jobsource.remoteok;

import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.integrations.jobsource.JobSourceException;
import com.darya.jobassistant.integrations.jobsource.JobSourcePort;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

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
    public List<JobOffer> fetchLatestPostings() {
        RemoteOkJobDto[] jobs = webClient.get()
                .uri(remoteOkProperties.baseUrl())
                .header(HttpHeaders.USER_AGENT, USER_AGENT)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new JobSourceException(
                                "RemoteOK rejected the request: HTTP %d - %s"
                                        .formatted(response.statusCode().value(), body)))))
                .onStatus(HttpStatusCode::is5xxServerError, response -> Mono.error(new JobSourceException(
                        "RemoteOK is currently unavailable: HTTP " + response.statusCode().value())))
                .bodyToMono(RemoteOkJobDto[].class)
                .onErrorMap(ex -> !(ex instanceof JobSourceException),
                        ex -> new JobSourceException("Failed to fetch job postings from RemoteOK", ex))
                .block();

        if (jobs == null) {
            return List.of();
        }

        return Arrays.stream(jobs)
                .filter(job -> StringUtils.hasText(job.company())
                        && StringUtils.hasText(job.position())
                        && StringUtils.hasText(job.url()))
                .map(this::toJobOffer)
                .toList();
    }

    private JobOffer toJobOffer(RemoteOkJobDto job) {
        return new JobOffer(
                job.id(),
                job.position(),
                job.company(),
                job.location(),
                formatSalary(job.salaryMin(), job.salaryMax()),
                job.description(),
                job.url(),
                SOURCE_NAME,
                job.tags() == null ? List.of() : job.tags()
        );
    }

    private String formatSalary(BigDecimal min, BigDecimal max) {
        if (min == null && max == null) {
            return null;
        }
        if (min != null && max != null) {
            return "$%s - $%s".formatted(formatAmount(min), formatAmount(max));
        }
        return "$%s".formatted(formatAmount(min != null ? min : max));
    }

    private String formatAmount(BigDecimal amount) {
        return NumberFormat.getIntegerInstance(Locale.US).format(amount);
    }
}
