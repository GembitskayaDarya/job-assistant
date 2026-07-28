package com.darya.jobassistant.vacancies.maintenance.canonicalurl;

/** How {@code VacancyCanonicalUrlBackfillService} should treat a freshly computed legacy plan. */
public enum VacancyCanonicalUrlBackfillMode {

    /** Plan and report only - never issues an {@code UPDATE}. The default. */
    DRY_RUN,

    /** Plan, then atomically write every safe assignment in one transaction - see that service's javadoc. */
    APPLY
}
