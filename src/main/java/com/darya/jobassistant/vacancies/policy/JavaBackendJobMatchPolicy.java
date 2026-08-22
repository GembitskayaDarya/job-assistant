package com.darya.jobassistant.vacancies.policy;

import com.darya.jobassistant.integrations.jobsource.JobOffer;
import org.springframework.stereotype.Component;

/**
 * Deterministic, source-independent coarse pre-filter for Java/JVM backend engineering roles.
 * Runs before company resolution, vacancy persistence, and AI analysis so that clearly
 * irrelevant offers (other tech stacks, non-engineering roles) never reach those steps. Nuanced
 * suitability scoring against the candidate profile remains the AI analyzer's job - this policy
 * only rules out offers that could not plausibly be a match.
 *
 * <p>Delegates its rule engine to {@link BackendRoleSignals}, shared with the automatic-discovery
 * pipeline ({@code jobdiscovery}), which has no {@link JobOffer} to hand this policy - see that
 * class for the actual signal lists and the title-exclusion-overrides-everything rule.
 */
@Component
public class JavaBackendJobMatchPolicy implements JobOfferMatchPolicy {

    @Override
    public boolean matches(JobOffer jobOffer) {
        return BackendRoleSignals.matches(jobOffer.title(), jobOffer.description(), jobOffer.tags());
    }
}
