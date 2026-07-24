package com.darya.jobassistant.vacancies.policy;

import com.darya.jobassistant.integrations.jobsource.JobOffer;

/**
 * Deterministic, source-independent rule deciding whether a normalized {@link JobOffer} is
 * relevant enough to proceed to company resolution, persistence, and AI analysis.
 */
public interface JobOfferMatchPolicy {

    boolean matches(JobOffer jobOffer);
}
