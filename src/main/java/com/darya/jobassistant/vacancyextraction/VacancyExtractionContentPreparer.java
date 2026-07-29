package com.darya.jobassistant.vacancyextraction;

import com.darya.jobassistant.vacancyextraction.config.VacancyExtractionProperties;
import com.darya.jobassistant.vacancyextraction.model.PreparedVacancyContent;
import org.springframework.stereotype.Component;

/**
 * Deterministic, provider-neutral content size bound applied before every extraction AI call -
 * shared by guided manual import and any future automatic-discovery caller, so the same input can
 * never reach the AI provider unbounded from one path but capped from the other. No AI
 * summarization, no external calls, no HTML/Markdown cleanup: only a head+tail character cap with
 * a stable, visible omission marker in between. Single implementation, so no interface exists for
 * this class - see the project's port/adapter rule of thumb.
 */
@Component
public class VacancyExtractionContentPreparer {

    private static final String OMISSION_MARKER = "\n\n[CONTENT OMITTED FOR LENGTH LIMIT]\n\n";

    private final VacancyExtractionProperties properties;

    public VacancyExtractionContentPreparer(VacancyExtractionProperties properties) {
        this.properties = properties;
    }

    public PreparedVacancyContent prepare(String content) {
        int originalCharCount = content.length();
        int maxInputChars = properties.maxInputChars();
        if (originalCharCount <= maxInputChars) {
            return new PreparedVacancyContent(content, originalCharCount, originalCharCount, false);
        }

        int tailInputChars = properties.tailInputChars();
        int headChars = maxInputChars - tailInputChars;
        String head = headSubstring(content, headChars);
        String tail = tailInputChars == 0 ? "" : tailSubstring(content, tailInputChars);
        String prepared = head + OMISSION_MARKER + tail;
        return new PreparedVacancyContent(prepared, originalCharCount, prepared.length(), true);
    }

    /**
     * {@code content.substring(0, length)}, except the cut point is pulled back one position when
     * it would otherwise fall between a high surrogate (the last character kept) and its low
     * surrogate (the first character dropped) - excluding the whole pair rather than splitting it.
     */
    private String headSubstring(String content, int length) {
        int end = length;
        if (end > 0 && end < content.length()
                && Character.isHighSurrogate(content.charAt(end - 1))
                && Character.isLowSurrogate(content.charAt(end))) {
            end--;
        }
        return content.substring(0, end);
    }

    /**
     * {@code content.substring(content.length() - length)}, except the cut point is pulled back
     * one position when it would otherwise fall between a high surrogate (the last character
     * dropped) and its low surrogate (the first character kept) - including the whole pair rather
     * than splitting it.
     */
    private String tailSubstring(String content, int length) {
        int start = content.length() - length;
        if (start > 0
                && Character.isLowSurrogate(content.charAt(start))
                && Character.isHighSurrogate(content.charAt(start - 1))) {
            start--;
        }
        return content.substring(start);
    }
}
