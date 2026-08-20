package com.darya.jobassistant.applicationmaterials.render.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Sprint 11 Big Block 7: the exact date-range display text used both by {@code
 * PdfBoxApplicationMaterialDocumentRenderer} (to draw "Feb 2026 - Present") and {@code
 * AtsCvVerifier} (to confirm that exact text survives extraction, in order) - one shared source so
 * the two can never drift apart. Framework-free.
 */
public final class CvDateRangeFormatter {

    private static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
    private static final String PRESENT = "Present";

    private CvDateRangeFormatter() {
    }

    /** {@code "Feb 2026 - Present"} / {@code "Sep 2019 - Oct 2022"}; both sides absent (and not current) returns {@code ""}, never a bare separator. */
    public static String format(LocalDate startDate, LocalDate endDate, boolean currentRole) {
        String start = startDate == null ? "" : startDate.format(MONTH_YEAR);
        String end = currentRole ? PRESENT : (endDate == null ? "" : endDate.format(MONTH_YEAR));
        if (start.isEmpty() && end.isEmpty()) {
            return "";
        }
        return start + " - " + end;
    }
}
