package com.darya.jobassistant.vacancyextraction;

import com.darya.jobassistant.vacancyextraction.exception.VacancyExtractionException;
import com.darya.jobassistant.vacancyextraction.model.ExtractedVacancyData;
import com.darya.jobassistant.vacancyextraction.model.ExtractedVacancyDataValidator;
import com.darya.jobassistant.vacancyextraction.model.PreparedVacancyContent;
import com.darya.jobassistant.vacancyextraction.model.VacancyExtractionRequest;
import com.darya.jobassistant.vacancyextraction.port.VacancyExtractionPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The one shared vacancy-extraction capability: content preparation, exactly one {@link
 * VacancyExtractionPort#extract} call, then output validation - usable identically by guided
 * manual import ({@code VacancyImportService}) today and by a future automatic-discovery caller
 * (built directly from a Firecrawl {@code JobPageContent}) without either depending on the other.
 * Deliberately not {@code @Transactional} and touches no repository: extraction is external AI
 * work and must never run inside a database transaction (see callers for their own transaction
 * boundaries around this call).
 */
@Slf4j
@Component
public class VacancyExtractionService {

    private final VacancyExtractionPort extractionPort;
    private final VacancyExtractionContentPreparer contentPreparer;

    public VacancyExtractionService(VacancyExtractionPort extractionPort, VacancyExtractionContentPreparer contentPreparer) {
        this.extractionPort = extractionPort;
        this.contentPreparer = contentPreparer;
    }

    public ExtractedVacancyData extract(VacancyExtractionRequest request) {
        PreparedVacancyContent prepared = contentPreparer.prepare(request.content());
        VacancyExtractionRequest preparedRequest = new VacancyExtractionRequest(
                request.sourceUrl(), prepared.content(), request.discoveredTitle(), request.discoveredSnippet());

        long startNanos = System.nanoTime();
        try {
            ExtractedVacancyData raw = extractionPort.extract(preparedRequest);
            ExtractedVacancyData validated = ExtractedVacancyDataValidator.validate(raw);
            logOutcome(request, prepared, "success", startNanos);
            return validated;
        } catch (VacancyExtractionException failure) {
            logOutcome(request, prepared, "failure", startNanos);
            throw failure;
        }
    }

    private void logOutcome(VacancyExtractionRequest request, PreparedVacancyContent prepared, String outcome, long startNanos) {
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
        String host = request.sourceUrl() != null ? request.sourceUrl().getHost() : "n/a";
        log.debug("Vacancy extraction operation=extract host={} outcome={} originalChars={} preparedChars={} "
                        + "truncated={} elapsedMs={}",
                host, outcome, prepared.originalCharCount(), prepared.preparedCharCount(), prepared.truncated(), elapsedMillis);
    }
}
