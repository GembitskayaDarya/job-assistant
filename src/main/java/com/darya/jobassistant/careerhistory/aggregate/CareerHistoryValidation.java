package com.darya.jobassistant.careerhistory.aggregate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Shared, framework-free validation/normalization helpers for the Career History aggregate's
 * record types (package-private - not part of the public domain model). Extracted because every
 * level of the aggregate (company, position, project, and both bullet types) repeats the same
 * shapes of check: non-blank text, non-negative display order, unique-by-key within one parent,
 * and "return children sorted by display order" - unlike {@code CandidateProfileAggregate}, which
 * has only two duplicate-style checks and inlines them directly.
 */
final class CareerHistoryValidation {

    private CareerHistoryValidation() {
    }

    static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    static void requireNonNegative(int value, String message) {
        if (value < 0) {
            throw new IllegalArgumentException(message);
        }
    }

    /** Optional free-text fields are normalized the same way project config binding already does elsewhere. */
    static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Defensive copy, sorted by display order - callers must never rely on the order they happened to supply. */
    static <T> List<T> copySortedByDisplayOrder(List<T> items, ToIntFunction<T> displayOrder) {
        List<T> copy = new ArrayList<>(items == null ? List.of() : items);
        copy.sort(Comparator.comparingInt(displayOrder));
        return List.copyOf(copy);
    }

    static <T> void requireUniqueDisplayOrders(List<T> items, ToIntFunction<T> displayOrder, String message) {
        Set<Integer> seen = new HashSet<>();
        for (T item : items) {
            if (!seen.add(displayOrder.applyAsInt(item))) {
                throw new IllegalArgumentException(message + ": duplicate display order " + displayOrder.applyAsInt(item));
            }
        }
    }

    /**
     * Case-sensitive by construction (plain {@link String#equals}) - matches PostgreSQL's default
     * deterministic collation, which every Career History uniqueness constraint (V19) relies on.
     * Never considers a child's technical id - two entries are duplicates purely by this business
     * key, regardless of whether they carry the same, different, or absent ids.
     */
    static <T> void requireUniqueBy(List<T> items, Function<T, String> key, String message) {
        Set<String> seen = new HashSet<>();
        for (T item : items) {
            if (!seen.add(key.apply(item))) {
                throw new IllegalArgumentException(message + ": " + key.apply(item));
            }
        }
    }
}
