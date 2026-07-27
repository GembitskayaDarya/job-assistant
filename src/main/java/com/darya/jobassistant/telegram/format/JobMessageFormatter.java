package com.darya.jobassistant.telegram.format;

import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.util.TelegramMessageUtils;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class JobMessageFormatter {

    private static final String NOT_SPECIFIED = "Not specified";
    private static final String NONE = "None";

    public String format(JobOffer job, JobAnalysis analysis) {
        return String.join("\n\n",
                formatHeader(job),
                formatCompany(job),
                formatLocation(job),
                formatSalary(job),
                formatMatch(analysis),
                formatStrengths(analysis),
                formatConsiderations(analysis),
                formatMissingRequiredSkills(analysis),
                formatMissingPreferredSkills(analysis),
                formatExperienceAssessment(analysis),
                formatPreferencesAssessment(analysis),
                formatSummary(analysis),
                formatLink(job));
    }

    private String formatHeader(JobOffer job) {
        return "🔥 " + escape(job.title());
    }

    private String formatCompany(JobOffer job) {
        return "🏢 Company: " + escapeOrNotSpecified(job.company());
    }

    private String formatLocation(JobOffer job) {
        return "📍 Location: " + escapeOrNotSpecified(job.location());
    }

    private String formatSalary(JobOffer job) {
        return "💰 Salary: " + escapeOrNotSpecified(job.salary());
    }

    private String formatMatch(JobAnalysis analysis) {
        return "⭐ Match: " + analysis.score() + "%";
    }

    private String formatStrengths(JobAnalysis analysis) {
        return "✅ Strengths\n" + formatBulletList(analysis.pros());
    }

    private String formatConsiderations(JobAnalysis analysis) {
        return "⚠️ Considerations\n" + formatBulletList(analysis.cons());
    }

    private String formatMissingRequiredSkills(JobAnalysis analysis) {
        return "🧩 Missing Required Skills\n" + formatBulletList(analysis.missingRequiredSkills());
    }

    private String formatMissingPreferredSkills(JobAnalysis analysis) {
        return "🔧 Missing Preferred Skills\n" + formatBulletList(analysis.missingPreferredSkills());
    }

    private String formatExperienceAssessment(JobAnalysis analysis) {
        return "📈 Experience\n" + escape(analysis.experienceAssessment());
    }

    private String formatPreferencesAssessment(JobAnalysis analysis) {
        return "🧭 Preferences\n" + escape(analysis.preferencesAssessment());
    }

    private String formatSummary(JobAnalysis analysis) {
        return "💬 Summary\n" + escape(analysis.summary());
    }

    private String formatLink(JobOffer job) {
        return "🔗 " + escape(job.url());
    }

    private String formatBulletList(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "• " + NONE;
        }
        return items.stream()
                .map(item -> "• " + escape(item))
                .collect(Collectors.joining("\n"));
    }

    private String escapeOrNotSpecified(String value) {
        return value == null || value.isBlank() ? NOT_SPECIFIED : escape(value);
    }

    private String escape(String text) {
        return TelegramMessageUtils.escapeMarkdownV2(text);
    }
}
