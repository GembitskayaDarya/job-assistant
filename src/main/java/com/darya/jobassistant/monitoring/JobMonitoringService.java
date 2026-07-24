package com.darya.jobassistant.monitoring;

import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.candidates.CandidateProfile;
import com.darya.jobassistant.candidates.CandidateProfileProvider;
import com.darya.jobassistant.integrations.ai.openai.JobAnalysisService;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.integrations.jobsource.JobSourceException;
import com.darya.jobassistant.integrations.jobsource.JobSourcePort;
import com.darya.jobassistant.integrations.notifier.JobNotification;
import com.darya.jobassistant.integrations.notifier.JobNotificationException;
import com.darya.jobassistant.integrations.notifier.JobNotificationFactory;
import com.darya.jobassistant.integrations.notifier.JobNotificationFailureType;
import com.darya.jobassistant.integrations.notifier.JobNotificationPort;
import com.darya.jobassistant.integrations.notifier.JobNotificationResult;
import com.darya.jobassistant.monitoring.dto.JobMonitoringCommand;
import com.darya.jobassistant.monitoring.dto.JobMonitoringResult;
import com.darya.jobassistant.notifications.dto.NotificationDelivery;
import com.darya.jobassistant.notifications.dto.NotificationDeliveryTransitionResult;
import com.darya.jobassistant.notifications.dto.NotificationReservationResult;
import com.darya.jobassistant.notifications.repository.NotificationDeliveryRepository;
import com.darya.jobassistant.vacancies.dto.VacancyIngestionResult;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.mapper.VacancyJobOfferMapper;
import com.darya.jobassistant.vacancies.service.VacancyIngestionService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Orchestrates one monitoring run: fetch/ingest -> analyze -> filter -> rank -> reserve delivery
 * -> send -> mark SENT/FAILED. Deliberately not {@code @Transactional}: it makes external AI and
 * Telegram calls, and must never hold a database transaction open across those. The individual
 * persistence operations it calls (ingestion, reservation, state transitions) each keep their own
 * short transaction boundaries.
 *
 * <p>Conditional on {@code telegram.enabled=true}, matching {@code JobNotificationPort}'s only
 * current implementation ({@code TelegramJobNotificationAdapter}) - without this, the required
 * {@link JobNotificationPort} dependency would have no bean to wire when Telegram is disabled.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "telegram", name = "enabled", havingValue = "true")
public class JobMonitoringService implements JobMonitoringUseCase {

    private final List<JobSourcePort> jobSources;
    private final VacancyIngestionService vacancyIngestionService;
    private final VacancyJobOfferMapper vacancyJobOfferMapper;
    private final CandidateProfileProvider candidateProfileProvider;
    private final JobAnalysisService jobAnalysisService;
    private final JobNotificationFactory jobNotificationFactory;
    private final JobNotificationPort jobNotificationPort;
    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final Clock clock;

    @Override
    public JobMonitoringResult monitor(JobMonitoringCommand command) {
        VacancyIngestionResult ingestionResult = ingestForKeyword(command.keyword());
        List<Vacancy> newVacancies = ingestionResult.persistedVacancies();

        AnalysisOutcome analysisOutcome = analyzeAll(newVacancies, command.minScore());
        List<RankedCandidate> ranked = rank(analysisOutcome.matches());
        SendOutcome sendOutcome = sendNotifications(ranked, command);

        JobMonitoringResult result = new JobMonitoringResult(
                ingestionResult.fetchedCount(),
                newVacancies.size(),
                analysisOutcome.analyzedCount(),
                analysisOutcome.matches().size(),
                sendOutcome.notifiedCount(),
                analysisOutcome.failedCount() + sendOutcome.failedCount());

        log.info("Monitoring run summary - fetched: {}, persisted: {}, analyzed: {}, matched: {}, notified: {}, failed: {}",
                result.fetchedCount(), result.persistedCount(), result.analyzedCount(),
                result.matchedCount(), result.notifiedCount(), result.failedCount());

        return result;
    }

    private VacancyIngestionResult ingestForKeyword(String keyword) {
        String normalizedKeyword = keyword.toLowerCase(Locale.ROOT);
        List<JobOffer> matchingOffers = jobSources.stream()
                .flatMap(source -> fetchSafely(source).stream())
                .filter(offer -> matchesKeyword(offer, normalizedKeyword))
                .toList();
        return vacancyIngestionService.ingest(matchingOffers);
    }

    private List<JobOffer> fetchSafely(JobSourcePort source) {
        try {
            return source.fetchLatestPostings();
        } catch (JobSourceException e) {
            log.warn("Skipping job source {} during monitoring: {}", source.sourceName(), e.getMessage());
            return List.of();
        }
    }

    private boolean matchesKeyword(JobOffer job, String normalizedKeyword) {
        return containsIgnoreCase(job.title(), normalizedKeyword)
                || containsIgnoreCase(job.company(), normalizedKeyword)
                || containsIgnoreCase(job.description(), normalizedKeyword);
    }

    private boolean containsIgnoreCase(String value, String normalizedKeyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedKeyword);
    }

    private AnalysisOutcome analyzeAll(List<Vacancy> newVacancies, int minScore) {
        if (newVacancies.isEmpty()) {
            return new AnalysisOutcome(List.of(), 0, 0);
        }

        CandidateProfile profile = loadProfile();

        List<RankedCandidate> matches = new ArrayList<>();
        int analyzedCount = 0;
        int failedCount = 0;
        int sequence = 0;
        for (Vacancy vacancy : newVacancies) {
            try {
                JobOffer jobOffer = vacancyJobOfferMapper.toJobOffer(vacancy);
                JobAnalysis analysis = jobAnalysisService.analyze(profile, jobOffer);
                analyzedCount++;
                if (analysis.score() >= minScore) {
                    matches.add(new RankedCandidate(vacancy, analysis, sequence));
                }
            } catch (RuntimeException e) {
                failedCount++;
                log.error("Failed to analyze vacancy {}", vacancy.getId(), e);
            }
            sequence++;
        }
        return new AnalysisOutcome(matches, analyzedCount, failedCount);
    }

    private CandidateProfile loadProfile() {
        try {
            return candidateProfileProvider.getProfile();
        } catch (RuntimeException e) {
            throw new JobMonitoringException("Unable to load candidate profile for job monitoring", e);
        }
    }

    private List<RankedCandidate> rank(List<RankedCandidate> matches) {
        return matches.stream()
                .sorted(Comparator.comparingInt((RankedCandidate c) -> c.analysis().score())
                        .reversed()
                        .thenComparingInt(RankedCandidate::sequence))
                .toList();
    }

    private SendOutcome sendNotifications(List<RankedCandidate> ranked, JobMonitoringCommand command) {
        int notifiedCount = 0;
        int failedCount = 0;
        int providerAttempts = 0;

        for (RankedCandidate candidate : ranked) {
            if (providerAttempts >= command.maxNotifications()) {
                break;
            }
            Vacancy vacancy = candidate.vacancy();

            JobNotification notification;
            try {
                notification = jobNotificationFactory.create(vacancy, candidate.analysis(), command.recipientChatId());
            } catch (RuntimeException e) {
                failedCount++;
                log.error("Failed to build notification for vacancy {}", vacancy.getId(), e);
                continue;
            }

            NotificationReservationResult reservation;
            try {
                reservation = notificationDeliveryRepository.reserve(
                        vacancy.getId(), command.recipientChatId(), Instant.now(clock));
            } catch (RuntimeException e) {
                failedCount++;
                log.error("Failed to reserve delivery for vacancy {}", vacancy.getId(), e);
                continue;
            }

            if (!reservation.isReserved()) {
                log.debug("Delivery already exists for vacancy {}, recipient {} - skipping", vacancy.getId(), command.recipientChatId());
                continue;
            }

            NotificationDelivery delivery = reservation.delivery();
            providerAttempts++;

            JobNotificationResult sendResult;
            try {
                sendResult = jobNotificationPort.send(notification);
            } catch (JobNotificationException e) {
                failedCount++;
                log.warn("Notification send failed for vacancy {} (delivery {}): {}",
                        vacancy.getId(), delivery.id(), e.getMessage());
                markFailedSafely(delivery.id(), e.failureType().name());
                continue;
            } catch (RuntimeException e) {
                failedCount++;
                log.error("Unexpected notification send failure for vacancy {} (delivery {})",
                        vacancy.getId(), delivery.id(), e);
                markFailedSafely(delivery.id(), JobNotificationFailureType.UNEXPECTED_FAILURE.name());
                continue;
            }

            notifiedCount++;
            log.debug("Notification sent for vacancy {} (delivery {}), externalMessageId={}",
                    vacancy.getId(), delivery.id(), sendResult.externalMessageId().orElse("n/a"));

            if (!markSentSafely(delivery.id())) {
                failedCount++;
            }
        }

        return new SendOutcome(notifiedCount, failedCount);
    }

    private boolean markSentSafely(UUID deliveryId) {
        try {
            NotificationDeliveryTransitionResult transition = notificationDeliveryRepository.markSent(deliveryId, Instant.now(clock));
            if (transition.isUpdated()) {
                return true;
            }
            log.error("CONSISTENCY: delivery {} was sent by the provider but markSent returned {}", deliveryId, transition.status());
            return false;
        } catch (RuntimeException e) {
            log.error("CONSISTENCY: delivery {} was sent by the provider but markSent threw", deliveryId, e);
            return false;
        }
    }

    private void markFailedSafely(UUID deliveryId, String failureCode) {
        try {
            NotificationDeliveryTransitionResult transition =
                    notificationDeliveryRepository.markFailed(deliveryId, Instant.now(clock), failureCode);
            if (!transition.isUpdated()) {
                log.error("Failed to persist FAILED state for delivery {}: transition returned {}", deliveryId, transition.status());
            }
        } catch (RuntimeException e) {
            log.error("Failed to persist FAILED state for delivery {}", deliveryId, e);
        }
    }

    private record RankedCandidate(Vacancy vacancy, JobAnalysis analysis, int sequence) {
    }

    private record AnalysisOutcome(List<RankedCandidate> matches, int analyzedCount, int failedCount) {
    }

    private record SendOutcome(int notifiedCount, int failedCount) {
    }
}
