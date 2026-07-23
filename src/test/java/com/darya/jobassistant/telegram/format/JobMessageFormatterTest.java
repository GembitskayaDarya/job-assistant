package com.darya.jobassistant.telegram.format;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobMessageFormatterTest {

    private final JobMessageFormatter formatter = new JobMessageFormatter();

    @Test
    void formatsCompleteJobWithAllFieldsPresent() {
        JobOffer job = new JobOffer(
                "1",
                "Senior Java Backend Engineer",
                "Stripe",
                "Remote",
                "$180k - $220k",
                "description",
                "https://stripe.com/jobs/123",
                "remoteok");
        JobAnalysis analysis = new JobAnalysis(
                94,
                List.of("Java", "Spring Boot", "Kafka", "AWS"),
                List.of(),
                List.of("Terraform"),
                "Excellent match. Strong backend experience. Worth applying.");

        String message = formatter.format(job, analysis);

        String expected = String.join("\n\n",
                "🔥 Senior Java Backend Engineer",
                "🏢 Company: Stripe",
                "📍 Location: Remote",
                "💰 Salary: $180k \\- $220k",
                "⭐ Match: 94%",
                "✅ Strengths\n• Java\n• Spring Boot\n• Kafka\n• AWS",
                "⚠ Missing Skills\n• Terraform",
                "💬 Summary\nExcellent match\\. Strong backend experience\\. Worth applying\\.",
                "🔗 https://stripe\\.com/jobs/123");

        assertThat(message).isEqualTo(expected);
    }

    @Test
    void fallsBackToNotSpecifiedWhenSalaryAndLocationAreMissing() {
        JobOffer job = new JobOffer(
                "1", "Backend Engineer", "Acme", null, null, "desc", "https://acme.example/jobs/1", "remoteok");
        JobAnalysis analysis = new JobAnalysis(50, List.of("Java"), List.of(), List.of(), "Decent match.");

        String message = formatter.format(job, analysis);

        assertThat(message)
                .contains("📍 Location: Not specified")
                .contains("💰 Salary: Not specified");
    }

    @Test
    void rendersNoneWhenStrengthsOrMissingSkillsAreEmpty() {
        JobOffer job = new JobOffer(
                "1", "Backend Engineer", "Acme", "Remote", "N/A", "desc", "https://acme.example/jobs/1", "remoteok");
        JobAnalysis analysis = new JobAnalysis(10, List.of(), List.of(), List.of(), "Poor match.");

        String message = formatter.format(job, analysis);

        assertThat(message)
                .contains("✅ Strengths\n• None")
                .contains("⚠ Missing Skills\n• None");
    }

    @Test
    void escapesMarkdownV2ReservedCharactersInDynamicFields() {
        JobOffer job = new JobOffer(
                "1",
                "Senior Java Engineer (Backend)",
                "Acme Corp. Inc!",
                "Remote - US",
                null,
                "desc",
                "https://acme.example/jobs/123",
                "remoteok");
        JobAnalysis analysis = new JobAnalysis(
                80,
                List.of("Java (Core)"),
                List.of(),
                List.of("AWS."),
                "Strong fit - worth applying!");

        String message = formatter.format(job, analysis);

        assertThat(message)
                .contains("🔥 Senior Java Engineer \\(Backend\\)")
                .contains("🏢 Company: Acme Corp\\. Inc\\!")
                .contains("📍 Location: Remote \\- US")
                .contains("• Java \\(Core\\)")
                .contains("• AWS\\.")
                .contains("💬 Summary\nStrong fit \\- worth applying\\!")
                .contains("🔗 https://acme\\.example/jobs/123");
    }
}
