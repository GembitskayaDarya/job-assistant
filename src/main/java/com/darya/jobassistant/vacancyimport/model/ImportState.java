package com.darya.jobassistant.vacancyimport.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum ImportState {
    WAITING_FOR_URL,
    WAITING_FOR_DESCRIPTION,
    EXTRACTING,
    WAITING_FOR_CONFIRMATION,
    COMPLETED,
    CANCELLED,
    FAILED,
    EXPIRED;

    private static final Set<ImportState> ACTIVE_STATES = Collections.unmodifiableSet(
            EnumSet.of(WAITING_FOR_URL, WAITING_FOR_DESCRIPTION, EXTRACTING, WAITING_FOR_CONFIRMATION));

    /**
     * The single source of truth for which states count as "still in flight" - used both by
     * {@link #isActive()} and by repository queries (active-session lookup, expiry sweep), so
     * the set of active states never has to be duplicated at either call site.
     */
    public static Set<ImportState> activeStates() {
        return ACTIVE_STATES;
    }

    public boolean isActive() {
        return ACTIVE_STATES.contains(this);
    }
}
