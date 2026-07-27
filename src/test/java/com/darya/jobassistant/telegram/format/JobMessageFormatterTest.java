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
                List.of("No Terraform mentioned"),
                List.of("Terraform"),
                List.of("Kubernetes"),
                "The vacancy requests 5+ years and the candidate has 6 years, so the requirement is met.",
                "Remote arrangement and Poland-based work both match the candidate's preferences.",
                "Excellent match. Strong backend experience. Worth applying.");

        String message = formatter.format(job, analysis);

        String expected = String.join("\n\n",
                "🔥 Senior Java Backend Engineer",
                "🏢 Company: Stripe",
                "📍 Location: Remote",
                "💰 Salary: $180k \\- $220k",
                "⭐ Match: 94%",
                "✅ Strengths\n• Java\n• Spring Boot\n• Kafka\n• AWS",
                "⚠️ Considerations\n• No Terraform mentioned",
                "🧩 Missing Required Skills\n• Terraform",
                "🔧 Missing Preferred Skills\n• Kubernetes",
                "📈 Experience\nThe vacancy requests 5\\+ years and the candidate has 6 years, so the requirement is met\\.",
                "🧭 Preferences\nRemote arrangement and Poland\\-based work both match the candidate's preferences\\.",
                "💬 Summary\nExcellent match\\. Strong backend experience\\. Worth applying\\.",
                "🔗 https://stripe\\.com/jobs/123");

        assertThat(message).isEqualTo(expected);
    }

    @Test
    void fallsBackToNotSpecifiedWhenSalaryAndLocationAreMissing() {
        JobOffer job = new JobOffer(
                "1", "Backend Engineer", "Acme", null, null, "desc", "https://acme.example/jobs/1", "remoteok");
        JobAnalysis analysis = analysis();

        String message = formatter.format(job, analysis);

        assertThat(message)
                .contains("📍 Location: Not specified")
                .contains("💰 Salary: Not specified");
    }

    @Test
    void fallsBackToNotSpecifiedWhenSalaryAndLocationAreBlank() {
        JobOffer job = new JobOffer(
                "1", "Backend Engineer", "Acme", "   ", "  ", "desc", "https://acme.example/jobs/1", "remoteok");
        JobAnalysis analysis = analysis();

        String message = formatter.format(job, analysis);

        assertThat(message)
                .contains("📍 Location: Not specified")
                .contains("💰 Salary: Not specified");
    }

    @Test
    void formatsLocationAndSalaryTextFromGuidedImportVerbatim() {
        JobOffer job = new JobOffer(
                "1",
                "Backend Engineer",
                "Acme",
                "Warszawa/ Centrum",
                "120-175 PLN netto/h +VAT",
                "desc",
                "https://acme.example/jobs/1",
                "manual_telegram");
        JobAnalysis analysis = analysis();

        String message = formatter.format(job, analysis);

        assertThat(message)
                .contains("📍 Location: Warszawa/ Centrum")
                .contains("💰 Salary: 120\\-175 PLN netto/h \\+VAT");
    }

    @Test
    void rendersNoneWhenStrengthsConsOrMissingSkillsAreEmpty() {
        JobOffer job = new JobOffer(
                "1", "Backend Engineer", "Acme", "Remote", "N/A", "desc", "https://acme.example/jobs/1", "remoteok");
        JobAnalysis analysis = new JobAnalysis(
                10, List.of(), List.of(), List.of(), List.of(),
                "No years stated; seniority appears broadly aligned.", "No preference information available.", "Poor match.");

        String message = formatter.format(job, analysis);

        assertThat(message)
                .contains("✅ Strengths\n• None")
                .contains("⚠️ Considerations\n• None")
                .contains("🧩 Missing Required Skills\n• None")
                .contains("🔧 Missing Preferred Skills\n• None");
    }

    @Test
    void displaysExperienceAndPreferencesAssessments() {
        JobOffer job = new JobOffer(
                "1", "Backend Engineer", "Acme", "Remote", "N/A", "desc", "https://acme.example/jobs/1", "remoteok");
        JobAnalysis analysis = new JobAnalysis(
                60, List.of(), List.of(), List.of(), List.of(),
                "The vacancy does not state a minimum number of years.",
                "Contract type is not stated in the vacancy, so availability is unclear.",
                "Decent match.");

        String message = formatter.format(job, analysis);

        assertThat(message).contains("📈 Experience\nThe vacancy does not state a minimum number of years\\.");
        assertThat(message)
                .contains("🧭 Preferences\nContract type is not stated in the vacancy, so availability is unclear\\.");
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
                List.of("No AWS (yet)"),
                List.of("AWS."),
                List.of("Terraform!"),
                "6 years - matches.",
                "Remote - matches!",
                "Strong fit - worth applying!");

        String message = formatter.format(job, analysis);

        assertThat(message)
                .contains("🔥 Senior Java Engineer \\(Backend\\)")
                .contains("🏢 Company: Acme Corp\\. Inc\\!")
                .contains("📍 Location: Remote \\- US")
                .contains("• Java \\(Core\\)")
                .contains("• No AWS \\(yet\\)")
                .contains("• AWS\\.")
                .contains("• Terraform\\!")
                .contains("💬 Summary\nStrong fit \\- worth applying\\!")
                .contains("🔗 https://acme\\.example/jobs/123");
    }

    private JobAnalysis analysis() {
        return new JobAnalysis(
                50, List.of("Java"), List.of(), List.of(), List.of(),
                "6 years vs. no stated requirement.", "Remote preference matches.", "Decent match.");
    }
}
