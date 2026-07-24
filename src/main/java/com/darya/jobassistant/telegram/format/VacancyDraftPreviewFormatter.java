package com.darya.jobassistant.telegram.format;

import com.darya.jobassistant.vacancyextraction.model.ExtractedVacancyData;
import com.darya.jobassistant.vacancyextraction.model.RemotePolicy;
import com.darya.jobassistant.vacancyimport.model.VacancyImportDraft;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Renders a plain-text (no Markdown, no inline keyboard) preview of a recognized {@link
 * VacancyImportDraft} for the confirmation step. Every field on {@link ExtractedVacancyData} is
 * either shown here or reserved for the not-yet-implemented final {@code Vacancy} creation - a
 * field that is neither would have no reason to exist.
 */
@Component
public class VacancyDraftPreviewFormatter {

    private static final int MAX_SKILLS_SHOWN = 10;
    private static final String HEADER = "✅ Vacancy recognized";
    private static final String NOT_SAVED_YET = "The vacancy has not been saved yet.";

    public String formatRecognized(VacancyImportDraft draft) {
        ExtractedVacancyData data = draft.data();
        List<String> sections = new ArrayList<>();
        sections.add(HEADER);
        sections.add(data.title() + "\n" + data.company());

        String details = formatDetailLines(data);
        if (!details.isEmpty()) {
            sections.add(details);
        }
        sections.add(NOT_SAVED_YET);
        return String.join("\n\n", sections);
    }

    private String formatDetailLines(ExtractedVacancyData data) {
        List<String> lines = new ArrayList<>();
        addLineIfPresent(lines, "Location", formatLocationLine(data));
        addLineIfPresent(lines, "Contract", formatJoinedLine(data.contractTypes()));
        addLineIfPresent(lines, "Salary", data.salaryText());
        addLineIfPresent(lines, "Skills", formatSkillsLine(data));
        return String.join("\n", lines);
    }

    private String formatLocationLine(ExtractedVacancyData data) {
        String remoteLabel = remoteLabel(data.remotePolicy());
        if (remoteLabel != null && data.location() != null) {
            return remoteLabel + " / " + data.location();
        }
        if (remoteLabel != null) {
            return remoteLabel;
        }
        return data.location();
    }

    private String remoteLabel(RemotePolicy remotePolicy) {
        return switch (remotePolicy) {
            case REMOTE -> "Remote";
            case HYBRID -> "Hybrid";
            case ONSITE -> "Onsite";
            case UNSPECIFIED -> null;
        };
    }

    private String formatSkillsLine(ExtractedVacancyData data) {
        if (data.requiredSkills().isEmpty()) {
            return null;
        }
        String shown = data.requiredSkills().stream().limit(MAX_SKILLS_SHOWN).collect(Collectors.joining(", "));
        int remaining = data.requiredSkills().size() - MAX_SKILLS_SHOWN;
        return remaining > 0 ? shown + " (+" + remaining + " more)" : shown;
    }

    private String formatJoinedLine(List<String> values) {
        return values.isEmpty() ? null : String.join(", ", values);
    }

    private void addLineIfPresent(List<String> lines, String label, String value) {
        if (value != null && !value.isBlank()) {
            lines.add(label + ": " + value);
        }
    }
}
