package com.darya.jobassistant.careerhistory.importing;

import java.util.List;

/**
 * The result of {@link CareerHistorySemanticComparator#diff}. {@code entries} carries only safe
 * structural labels - company/position/project names (already known, user-authored display
 * values, never raw stable {@code key}s) plus a change kind ({@code ADDED}/{@code REMOVED}/{@code
 * CHANGED}) - never full responsibility/achievement text or other long free-text content. {@code
 * entries} is capped at a bounded size (see {@link CareerHistorySemanticComparator}); {@link
 * #totalChangeCount} always reports the true, uncapped number of differences found, so a caller
 * can tell "there were more changes than shown" apart from "these are all of them."
 */
public record CareerHistoryDiff(
        boolean equal,
        List<String> entries,
        int totalChangeCount
) {
    public CareerHistoryDiff {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }
}
