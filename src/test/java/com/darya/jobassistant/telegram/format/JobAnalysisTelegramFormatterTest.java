package com.darya.jobassistant.telegram.format;

import static org.assertj.core.api.Assertions.assertThat;

import com.darya.jobassistant.ai.model.JobAnalysis;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobAnalysisTelegramFormatterTest {

    private final JobAnalysisTelegramFormatter formatter = new JobAnalysisTelegramFormatter();

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
    void format_representativeAnalysis_producesExactCanonicalBlock() {
        String expected = String.join("\n\n",
                "⭐ Match: 82%",
                "✅ Strengths\n• Strong Java and Spring Boot experience\n• Relevant Kafka production experience",
                "⚠️ Considerations\n• AWS proficiency may be below the level expected",
                "🧩 Missing Required Skills\n• Kubernetes",
                "🔧 Missing Preferred Skills\n• GraphQL",
                "📈 Experience\nThe vacancy requests 5\\+ years and the candidate has 6 years\\.",
                "🧭 Preferences\nRemote work from Poland appears compatible\\. The contract type is not stated\\.",
                "💬 Summary\nThe candidate is a strong overall match with one notable infrastructure gap\\.");

        assertThat(formatter.format(representative)).isEqualTo(expected);
    }

    @Test
    void format_sectionsAppearInCanonicalOrder() {
        String message = formatter.format(representative);

        int scoreIndex = message.indexOf("⭐ Match");
        int prosIndex = message.indexOf("✅ Strengths");
        int consIndex = message.indexOf("⚠️ Considerations");
        int missingRequiredIndex = message.indexOf("🧩 Missing Required Skills");
        int missingPreferredIndex = message.indexOf("🔧 Missing Preferred Skills");
        int experienceIndex = message.indexOf("📈 Experience");
        int preferencesIndex = message.indexOf("🧭 Preferences");
        int summaryIndex = message.indexOf("💬 Summary");

        assertThat(scoreIndex)
                .isLessThan(prosIndex);
        assertThat(prosIndex).isLessThan(consIndex);
        assertThat(consIndex).isLessThan(missingRequiredIndex);
        assertThat(missingRequiredIndex).isLessThan(missingPreferredIndex);
        assertThat(missingPreferredIndex).isLessThan(experienceIndex);
        assertThat(experienceIndex).isLessThan(preferencesIndex);
        assertThat(preferencesIndex).isLessThan(summaryIndex);
    }

    @Test
    void format_scoreRenderedAsNumericPercentage() {
        assertThat(formatter.format(representative)).contains("⭐ Match: 82%");
    }

    @Test
    void format_listOrderIsPreservedNotSorted() {
        JobAnalysis analysis = new JobAnalysis(
                50, List.of("Zebra skill", "Alpha skill"), List.of(), List.of(), List.of(),
                "Not assessed.", "Not assessed.", "Summary");

        String message = formatter.format(analysis);

        assertThat(message.indexOf("Zebra skill")).isLessThan(message.indexOf("Alpha skill"));
    }

    @Test
    void format_requiredAndPreferredSkillsRemainDistinct() {
        JobAnalysis analysis = new JobAnalysis(
                50, List.of(), List.of(), List.of("Kubernetes"), List.of("GraphQL"),
                "Not assessed.", "Not assessed.", "Summary");

        String message = formatter.format(analysis);

        String requiredSection = message.substring(
                message.indexOf("🧩 Missing Required Skills"), message.indexOf("🔧 Missing Preferred Skills"));
        String preferredSection = message.substring(message.indexOf("🔧 Missing Preferred Skills"));

        assertThat(requiredSection).contains("Kubernetes").doesNotContain("GraphQL");
        assertThat(preferredSection).contains("GraphQL").doesNotContain("Kubernetes");
    }

    @Test
    void format_emptyLists_renderCanonicalNoneValue() {
        JobAnalysis analysis = new JobAnalysis(
                50, List.of(), List.of(), List.of(), List.of(),
                "Not assessed.", "Not assessed.", "Summary");

        String message = formatter.format(analysis);

        assertThat(message)
                .contains("✅ Strengths\n• None")
                .contains("⚠️ Considerations\n• None")
                .contains("🧩 Missing Required Skills\n• None")
                .contains("🔧 Missing Preferred Skills\n• None");
    }

    @Test
    void format_summaryExperienceAndPreferencesAreShown() {
        String message = formatter.format(representative);

        assertThat(message).contains("📈 Experience\nThe vacancy requests 5\\+ years and the candidate has 6 years\\.");
        assertThat(message)
                .contains("🧭 Preferences\nRemote work from Poland appears compatible\\. The contract type is not stated\\.");
        assertThat(message)
                .contains("💬 Summary\nThe candidate is a strong overall match with one notable infrastructure gap\\.");
    }

    @Test
    void format_neverContainsNullOrJavaCollectionSyntax() {
        JobAnalysis analysis = new JobAnalysis(
                0, List.of(), List.of(), List.of(), List.of(),
                "Not assessed.", "Not assessed.", "Summary");

        String message = formatter.format(analysis);

        assertThat(message).doesNotContain("null").doesNotContain("[]").doesNotContain("Optional");
    }

    @Test
    void format_specialCharactersAreEscapedPerMarkdownV2Convention() {
        JobAnalysis analysis = new JobAnalysis(
                50,
                List.of("Loves *bold* text"),
                List.of("Uses `code` blocks"),
                List.of("C++ experience"),
                List.of("Rust (systems) experience"),
                "5+ years required.",
                "Contract type: B2B only!",
                "Great fit (highly recommended) #1!");

        String message = formatter.format(analysis);

        assertThat(message)
                .contains("Loves \\*bold\\* text")
                .contains("Uses \\`code\\` blocks")
                .contains("C\\+\\+ experience")
                .contains("Rust \\(systems\\) experience")
                .contains("5\\+ years required\\.")
                .contains("Contract type: B2B only\\!")
                .contains("Great fit \\(highly recommended\\) \\#1\\!");
        // Never leak the raw, unescaped reserved characters next to the escaped ones.
        assertThat(message).doesNotContain("*bold*").doesNotContain("`code`");
    }

    @Test
    void format_doesNotMutateTheSuppliedAnalysis() {
        List<String> originalPros = representative.pros();

        formatter.format(representative);

        assertThat(representative.pros()).isEqualTo(originalPros);
        assertThat(representative.score()).isEqualTo(82);
    }
}
