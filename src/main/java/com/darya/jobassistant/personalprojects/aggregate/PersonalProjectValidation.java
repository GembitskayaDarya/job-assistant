package com.darya.jobassistant.personalprojects.aggregate;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Sprint 11 Step 5: small, package-private validation helpers shared by {@link PersonalProject}
 * and its children - the same shape as {@code careerhistory.aggregate.CareerHistoryValidation}'s
 * generic helpers, deliberately duplicated here rather than reused across packages: that type is
 * package-private to {@code careerhistory.aggregate}, and widening it to {@code public} (or
 * extracting a brand-new cross-cutting {@code util} abstraction) solely to serve this one new
 * package is exactly the kind of premature abstraction this codebase avoids - a genuinely shared
 * helper can be extracted later if a third consumer appears.
 */
final class PersonalProjectValidation {

    private PersonalProjectValidation() {
    }

    static String requireNonBlank(String value, String message) {
        String trimmed = blankToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static void requireNonNegative(int value, String message) {
        if (value < 0) {
            throw new IllegalArgumentException(message);
        }
    }

    static <T> List<T> copySortedByDisplayOrder(List<T> items, ToIntFunction<T> displayOrder) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .sorted(Comparator.comparingInt(displayOrder::applyAsInt))
                .toList();
    }

    static <T> void requireUniqueDisplayOrders(List<T> items, ToIntFunction<T> displayOrder, String message) {
        Set<Integer> seen = new HashSet<>();
        for (T item : items) {
            if (!seen.add(displayOrder.applyAsInt(item))) {
                throw new IllegalArgumentException(message);
            }
        }
    }

    /** Normalized (trimmed, case-folded) uniqueness - see {@link PersonalProjectTechnology}'s javadoc for why. */
    static <T> void requireUniqueNormalized(List<T> items, Function<T, String> key, String message) {
        Set<String> seen = new HashSet<>();
        for (T item : items) {
            String normalized = key.apply(item).trim().toLowerCase(Locale.ROOT);
            if (!seen.add(normalized)) {
                throw new IllegalArgumentException(message);
            }
        }
    }
}
