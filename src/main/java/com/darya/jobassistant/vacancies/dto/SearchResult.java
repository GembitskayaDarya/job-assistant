package com.darya.jobassistant.vacancies.dto;

import com.darya.jobassistant.integrations.jobsource.JobOffer;
import java.util.UUID;

public record SearchResult(JobOffer jobOffer, UUID vacancyId) {
}
