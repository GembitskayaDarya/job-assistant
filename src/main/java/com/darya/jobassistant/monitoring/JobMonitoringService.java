package com.darya.jobassistant.monitoring;

import com.darya.jobassistant.ai.model.AnalysisOrigin;
import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.ai.repository.JobAnalysisRepository;
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
import com.darya.jobassistant.notifications.query.JobNotificationCandidate;
import com.darya.jobassistant.notifications.query.JobNotificationCandidateQueryPort;
import com.darya.jobassistant.notifications.repository.NotificationDeliveryRepository;
import com.darya.jobassistant.vacancies.dto.VacancyIngestionResult;
import com.darya.jobassistant.vacancies.entity.Vacancy;
import com.darya.jobassistant.vacancies.mapper.VacancyJobOfferMapper;
import com.darya.jobassistant.vacancies.service.VacancyIngestionService;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Orchestrates one monitoring run:
 * <pre>
 * load profile once -&gt; ingest -&gt; analyze + persist each new vacancy -&gt; query durable backlog
 * -&gt; reserve -&gt; send -&gt; mark SENT/FAILED
 * </pre>
 * Deliberately not {@code @Transactional}: it makes external AI and Telegram calls, and must
 * never hold a database transaction open across those. The individual persistence operations it
 * calls (ingestion, analysis persistence, reservation, state transitions) each keep their own
 * short transaction boundaries. The backlog query runs unconditionally after analysis, whether or
 * not this run fetched, persisted, or successfully analyzed anything - that is what lets
 * previously persisted, already-analyzed vacancies remain deliverable across runs and restarts.
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
    private final JobAnalysisRepository jobAnalysisRepository;
    private final JobNotificationCandidateQueryPort jobNotificationCandidateQueryPort;
    private final JobNotificationFactory jobNotificationFactory;
    private final JobNotificationPort jobNotificationPort;
    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final Clock clock;

    @Override
    public JobMonitoringResult monitor(JobMonitoringCommand command) {
        CandidateProfile profile = loadProfile();

        VacancyIngestionResult ingestionResult = ingestForKeyword(command.keyword());
        List<Vacancy> newVacancies = ingestionResult.persistedVacancies();

        AnalysisOutcome analysisOutcome = analyzeAndPersistAll(newVacancies, profile);

        List<JobNotificationCandidate> candidates = jobNotificationCandidateQueryPort.findCandidates(
                command.recipientChatId(), command.minScore(), command.maxNotifications());

        SendOutcome sendOutcome = sendNotifications(candidates, command);

        JobMonitoringResult result = new JobMonitoringResult(
                ingestionResult.fetchedCount(),
                newVacancies.size(),
                analysisOutcome.analyzedCount(),
                candidates.size(),
                sendOutcome.notifiedCount(),
                analysisOutcome.failedCount() + sendOutcome.failedCount());

        log.info("Monitoring run summary - fetched: {}, persisted: {}, analyzed: {}, matched: {}, notified: {}, failed: {}",
                result.fetchedCount(), result.persistedCount(), result.analyzedCount(),
                result.matchedCount(), result.notifiedCount(), result.failedCount());

        return result;
    }

    private CandidateProfile loadProfile() {
        try {
            return candidateProfileProvider.getProfile();
        } catch (RuntimeException e) {
            throw new JobMonitoringException("Unable to load candidate profile for job monitoring", e);
        }
    }

    private VacancyIngestionResult ingestForKeyword(String keyword) {
        String normalizedKeyword = keyword.toLowerCase(Locale.ROOT);
        List<JobOffer> fetchedOffers = jobSources.stream()
                .flatMap(source -> fetchSafely(source).stream())
                .toList();
        List<JobOffer> matchingOffers = fetchedOffers.stream()
                .filter(offer -> matchesKeyword(offer, normalizedKeyword))
                .toList();
        log.debug("Keyword \"{}\" matched {} of {} offers fetched from all sources",
                keyword, matchingOffers.size(), fetchedOffers.size());
        return vacancyIngestionService.ingest(matchingOffers);
    }

    private List<JobOffer> fetchSafely(JobSourcePort source) {
        try {
            List<JobOffer> offers = source.fetchLatestPostings();
            log.debug("Fetched {} postings from {}", offers.size(), source.sourceName());
            return offers;
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

    /**
     * Analyzes and persists each newly inserted vacancy in ingestion order. A vacancy counts
     * toward {@code analyzedCount} only once both the AI analysis and its persistence succeed;
     * either failing counts once toward {@code failedCount} for that vacancy, and processing
     * continues with the rest.
     *
     * <p>A vacancy that already has any {@code job_analysis} row - regardless of its version or
     * origin - is skipped entirely rather than analyzed: monitoring only ever fills in analyses
     * for vacancies that have none yet, never reanalyzes an outdated or manually-produced one.
     * That is what keeps a version bump from triggering surprise bulk AI calls, and what keeps
     * monitoring from taking ownership of a vacancy a user already analyzed themselves. Skipped
     * vacancies count toward neither {@code analyzedCount} nor {@code failedCount}.
     */
    private AnalysisOutcome analyzeAndPersistAll(List<Vacancy> newVacancies, CandidateProfile profile) {
        int analyzedCount = 0;
        int failedCount = 0;
        for (Vacancy vacancy : newVacancies) {
            if (jobAnalysisRepository.findByVacancyId(vacancy.getId()).isPresent()) {
                continue;
            }
            try {
                JobOffer jobOffer = vacancyJobOfferMapper.toJobOffer(vacancy);
                JobAnalysis analysis = jobAnalysisService.analyze(profile, jobOffer);
                jobAnalysisRepository.persist(vacancy.getId(), analysis, AnalysisOrigin.MONITORING);
                analyzedCount++;
            } catch (RuntimeException e) {
                failedCount++;
                log.error("Failed to analyze or persist analysis for vacancy {}", vacancy.getId(), e);
            }
        }
        return new AnalysisOutcome(analyzedCount, failedCount);
    }

    /**
     * Sends notifications for backlog candidates in exactly the order the query returned them
     * (score, then age, then vacancy id) - already bounded to {@code command.maxNotifications()}
     * by the query itself, so no separate attempt budget is tracked here.
     */
    private SendOutcome sendNotifications(List<JobNotificationCandidate> candidates, JobMonitoringCommand command) {
        int notifiedCount = 0;
        int failedCount = 0;

        for (JobNotificationCandidate candidate : candidates) {
            Vacancy vacancy = candidate.vacancy();

            JobNotification notification;
            try {
                notification = jobNotificationFactory.create(vacancy, candidate.analysis().analysis(), command.recipientChatId());
            } catch (RuntimeException e) {
                failedCount++;
                log.error("Failed to build notification for vacancy {}", vacancy.getId(), e);
                continue;
            }

            NotificationReservationResult reservation;
            try {
                reservation = notificationDeliveryRepository.reserve(
                        vacancy.getId(), command.recipientChatId(), clock.instant());
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
            NotificationDeliveryTransitionResult transition = notificationDeliveryRepository.markSent(deliveryId, clock.instant());
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
                    notificationDeliveryRepository.markFailed(deliveryId, clock.instant(), failureCode);
            if (!transition.isUpdated()) {
                log.error("Failed to persist FAILED state for delivery {}: transition returned {}", deliveryId, transition.status());
            }
        } catch (RuntimeException e) {
            log.error("Failed to persist FAILED state for delivery {}", deliveryId, e);
        }
    }

    private record AnalysisOutcome(int analyzedCount, int failedCount) {
    }

    private record SendOutcome(int notifiedCount, int failedCount) {
    }
}
