package com.darya.jobassistant.vacancies.dto;

import com.darya.jobassistant.vacancyextraction.model.RemotePolicy;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.util.StringUtils;

/**
 * Provider-neutral input for {@code VacancyCreationService#createIfAbsent}. Deliberately carries
 * {@code companyName} as a plain {@code String}, never a persisted {@code Company} entity: a
 * caller must not be able to hand in a JPA entity that only exists in its own (possibly
 * suspended, uncommitted) transaction. Company resolution/creation happens entirely inside {@code
 * VacancyCreationService}'s own isolated transaction, in the same physical transaction as the
 * {@code Vacancy} insert - see that class for why.
 *
 * <p>No persistence-specific transaction state or Hibernate entity ever appears here; only plain
 * data callers (RemoteOK ingestion, guided manual import, and any future discovery source) already
 * have on hand.
 */
public record VacancyCreationCommand(
        String companyName,
        String title,
        String description,
        String url,
        String location,
        RemotePolicy remoteMode,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        String currency,
        String salaryText,
        String source,
        LocalDate postedAt
) {

    public VacancyCreationCommand {
        if (!StringUtils.hasText(companyName)) {
            throw new IllegalArgumentException("Vacancy creation command requires a company name");
        }
        if (!StringUtils.hasText(title)) {
            throw new IllegalArgumentException("Vacancy creation command requires a title");
        }
        if (!StringUtils.hasText(url)) {
            throw new IllegalArgumentException("Vacancy creation command requires a URL");
        }
    }
}
