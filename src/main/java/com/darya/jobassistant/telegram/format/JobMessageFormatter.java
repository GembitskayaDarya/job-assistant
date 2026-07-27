package com.darya.jobassistant.telegram.format;

import com.darya.jobassistant.ai.model.JobAnalysis;
import com.darya.jobassistant.integrations.jobsource.JobOffer;
import com.darya.jobassistant.util.TelegramMessageUtils;
import org.springframework.stereotype.Component;

@Component
public class JobMessageFormatter {

    private static final String NOT_SPECIFIED = "Not specified";

    private final JobAnalysisTelegramFormatter analysisFormatter;

    public JobMessageFormatter(JobAnalysisTelegramFormatter analysisFormatter) {
        this.analysisFormatter = analysisFormatter;
    }

    public String format(JobOffer job, JobAnalysis analysis) {
        return String.join("\n\n",
                formatHeader(job),
                formatCompany(job),
                formatLocation(job),
                formatSalary(job),
                analysisFormatter.format(analysis),
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

    private String formatLink(JobOffer job) {
        return "🔗 " + escape(job.url());
    }

    private String escapeOrNotSpecified(String value) {
        return value == null || value.isBlank() ? NOT_SPECIFIED : escape(value);
    }

    private String escape(String text) {
        return TelegramMessageUtils.escapeMarkdownV2(text);
    }
}
