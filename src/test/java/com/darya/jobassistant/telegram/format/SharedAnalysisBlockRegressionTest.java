package com.darya.jobassistant.telegram.format;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.integrations.notifier.JobNotification;
import com.darya.jobassistant.integrations.notifier.telegram.TelegramJobNotificationFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Step 8.1 regression coverage: before this step, {@code TelegramJobNotificationFormatter}
 * rendered a long {@link JobAnalysis} differently from {@code JobMessageFormatter} - it built and
 * formatted progressively shortened copies of the analysis to fit Telegram's message-length
 * limit, so a candidate whose vacancy was analyzed via monitoring could see a visibly different
 * (shorter, item-capped) analysis than the same analysis rendered through the manual {@code
 * /analyze} path. This test proves that can no longer happen: for the same {@link JobAnalysis},
 * however long, both wrappers embed the exact same {@link JobAnalysisTelegramFormatter#format}
 * output.
 */
class SharedAnalysisBlockRegressionTest {

    private final JobAnalysisTelegramFormatter sharedFormatter = new JobAnalysisTelegramFormatter();
    private final JobMessageFormatter jobMessageFormatter = new JobMessageFormatter(sharedFormatter);
    private final TelegramJobNotificationFormatter notificationFormatter = new TelegramJobNotificationFormatter(sharedFormatter);

    @Test
    void oversizedAnalysis_producesByteIdenticalSharedBlockInBothManualAndMonitoringMessages() {
        JobAnalysis oversized = oversizedAnalysis();
        JobOffer jobOffer = new JobOffer(
                "1", "Senior Java Backend Engineer", "Stripe", "Remote", "$180k - $220k",
                "description", "https://stripe.com/jobs/123", "remoteok");
        JobNotification notification = new JobNotification(
                UUID.randomUUID(), 12345L, "Senior Java Backend Engineer", "Stripe", "https://stripe.com/jobs/123", oversized);

        String manualMessage = jobMessageFormatter.format(jobOffer, oversized);
        String monitoringMessage = notificationFormatter.format(notification);
        String canonicalBlock = sharedFormatter.format(oversized);

        assertThat(canonicalBlock.length()).isGreaterThan(4096);
        assertThat(manualMessage).contains(canonicalBlock);
        assertThat(monitoringMessage).contains(canonicalBlock);

        // The exact same substring, found in both messages - not merely "similar" content.
        String fromManual = manualMessage.substring(manualMessage.indexOf(canonicalBlock), manualMessage.indexOf(canonicalBlock) + canonicalBlock.length());
        String fromMonitoring = monitoringMessage.substring(
                monitoringMessage.indexOf(canonicalBlock), monitoringMessage.indexOf(canonicalBlock) + canonicalBlock.length());
        assertThat(fromManual).isEqualTo(fromMonitoring);
        assertThat(fromManual).isEqualTo(canonicalBlock);
    }

    @Test
    void oversizedAnalysis_everyProConAndSkillSurvivesUntruncatedInBothMessages() {
        JobAnalysis oversized = oversizedAnalysis();
        JobOffer jobOffer = new JobOffer(
                "1", "Backend Engineer", "Acme", "Remote", "N/A", "desc", "https://acme.example/jobs/1", "remoteok");
        JobNotification notification =
                new JobNotification(UUID.randomUUID(), 12345L, "Backend Engineer", "Acme", "https://acme.example/jobs/1", oversized);

        String manualMessage = jobMessageFormatter.format(jobOffer, oversized);
        String monitoringMessage = notificationFormatter.format(notification);

        for (String pro : oversized.pros()) {
            assertThat(manualMessage).contains(pro);
            assertThat(monitoringMessage).contains(pro);
        }
        for (String con : oversized.cons()) {
            assertThat(manualMessage).contains(con);
            assertThat(monitoringMessage).contains(con);
        }
        for (String skill : oversized.missingRequiredSkills()) {
            assertThat(manualMessage).contains(skill);
            assertThat(monitoringMessage).contains(skill);
        }
        for (String skill : oversized.missingPreferredSkills()) {
            assertThat(manualMessage).contains(skill);
            assertThat(monitoringMessage).contains(skill);
        }
    }

    @Test
    void oversizedAnalysis_neitherWrapperConstructsAShortenedJobAnalysis() {
        JobAnalysis oversized = oversizedAnalysis();
        List<String> originalPros = oversized.pros();
        List<String> originalCons = oversized.cons();

        jobMessageFormatter.format(
                new JobOffer("1", "Backend Engineer", "Acme", "Remote", "N/A", "desc", "https://acme.example/jobs/1", "remoteok"),
                oversized);
        notificationFormatter.format(
                new JobNotification(UUID.randomUUID(), 12345L, "Backend Engineer", "Acme", "https://acme.example/jobs/1", oversized));

        // The original JobAnalysis value is untouched by either wrapper.
        assertThat(oversized.pros()).isEqualTo(originalPros);
        assertThat(oversized.cons()).isEqualTo(originalCons);
    }

    private JobAnalysis oversizedAnalysis() {
        List<String> longPros = IntStream.range(0, 50)
                .mapToObj(i -> "Pro number " + i + " ".repeat(50))
                .toList();
        List<String> longCons = IntStream.range(0, 50)
                .mapToObj(i -> "Con number " + i + " ".repeat(50))
                .toList();
        List<String> longMissingRequired = IntStream.range(0, 20)
                .mapToObj(i -> "Missing required skill number " + i + " ".repeat(30))
                .toList();
        List<String> longMissingPreferred = IntStream.range(0, 20)
                .mapToObj(i -> "Missing preferred skill number " + i + " ".repeat(30))
                .toList();
        return new JobAnalysis(
                85, longPros, longCons, longMissingRequired, longMissingPreferred,
                "Experience assessment repeated many times ".repeat(200),
                "Preferences assessment repeated many times ".repeat(200),
                "Summary repeated many times ".repeat(200));
    }
}
