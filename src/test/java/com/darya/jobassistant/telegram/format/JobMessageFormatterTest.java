package com.darya.jobassistant.telegram.format;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobMessageFormatterTest {

    private final JobAnalysisTelegramFormatter analysisFormatter = new JobAnalysisTelegramFormatter();
    private final JobMessageFormatter formatter = new JobMessageFormatter(analysisFormatter);

    private final JobAnalysis representative = new JobAnalysis(
            82,
            List.of("Strong Java and Spring Boot experience", "Relevant Kafka production experience"),
            List.of("AWS proficiency may be below the level expected"),
            List.of("Kubernetes"),
            List.of("GraphQL"),
            "The vacancy requests 5+ years and the candidate has 6 years.",
            "Remote work from Poland appears compatible. The contract type is not stated.",
            "The candidate is a strong overall match with one notable infrastructure gap.");

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

        String message = formatter.format(job, representative);

        String expected = String.join("\n\n",
                "🔥 Senior Java Backend Engineer",
                "🏢 Company: Stripe",
                "📍 Location: Remote",
                "💰 Salary: $180k \\- $220k",
                analysisFormatter.format(representative),
                "🔗 https://stripe\\.com/jobs/123");

        assertThat(message).isEqualTo(expected);
    }

    @Test
    void format_embedsTheExactSharedAnalysisBlockExactlyOnce() {
        JobOffer job = new JobOffer(
                "1", "Backend Engineer", "Acme", "Remote", "N/A", "desc", "https://acme.example/jobs/1", "remoteok");

        String message = formatter.format(job, representative);
        String sharedBlock = analysisFormatter.format(representative);

        assertThat(message).contains(sharedBlock);
        int firstIndex = message.indexOf(sharedBlock);
        int lastIndex = message.lastIndexOf(sharedBlock);
        assertThat(firstIndex).isEqualTo(lastIndex);
    }

    @Test
    void fallsBackToNotSpecifiedWhenSalaryAndLocationAreMissing() {
        JobOffer job = new JobOffer(
                "1", "Backend Engineer", "Acme", null, null, "desc", "https://acme.example/jobs/1", "remoteok");

        String message = formatter.format(job, representative);

        assertThat(message)
                .contains("📍 Location: Not specified")
                .contains("💰 Salary: Not specified");
    }

    @Test
    void fallsBackToNotSpecifiedWhenSalaryAndLocationAreBlank() {
        JobOffer job = new JobOffer(
                "1", "Backend Engineer", "Acme", "   ", "  ", "desc", "https://acme.example/jobs/1", "remoteok");

        String message = formatter.format(job, representative);

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

        String message = formatter.format(job, representative);

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
    void escapesMarkdownV2ReservedCharactersInVacancyMetadata() {
        JobOffer job = new JobOffer(
                "1",
                "Senior Java Engineer (Backend)",
                "Acme Corp. Inc!",
                "Remote - US",
                null,
                "desc",
                "https://acme.example/jobs/123",
                "remoteok");

        String message = formatter.format(job, representative);

        assertThat(message)
                .contains("🔥 Senior Java Engineer \\(Backend\\)")
                .contains("🏢 Company: Acme Corp\\. Inc\\!")
                .contains("📍 Location: Remote \\- US")
                .contains("🔗 https://acme\\.example/jobs/123");
    }

    @Test
    void notificationOnlyMetadataNeverAppearsInTheMessage() {
        JobOffer job = new JobOffer(
                "1", "Backend Engineer", "Acme", "Remote", "N/A", "desc", "https://acme.example/jobs/1", "remoteok");

        String message = formatter.format(job, representative);

        assertThat(message).doesNotContain("recipientChatId").doesNotContain("analysisOrigin").doesNotContain("analysisVersion");
    }
}
