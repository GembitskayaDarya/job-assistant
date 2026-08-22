package com.darya.jobassistant.jobdiscovery.geo;

/**
 * Outcome of assessing one piece of vacancy text (a search hint or extracted location) against the
 * configured target-geography policy. See {@link JobGeographyPolicy}.
 */
public enum GeographyEligibility {

    /** The text explicitly names an accepted region (Poland/Europe and configured equivalents). */
    ELIGIBLE,

    /** The text explicitly names an incompatible region restriction (e.g. "US citizens only"). */
    INELIGIBLE,

    /**
     * No reliable geography signal either way - never treated as a rejection. See the Sprint 12
     * geography policy requirement to be conservative about false rejection when location
     * information is missing or incomplete.
     */
    UNKNOWN
}
