package com.darya.jobassistant.ai;

import com.darya.jobassistant.ai.dto.AnalyzeVacancyResult;
import java.util.UUID;

/**
 * The single provider-neutral entry point for analyzing a persisted {@code Vacancy} against the
 * candidate profile. Shared by the existing {@code /analyze} command and the guided-import
 * "Analyze" callback - neither owns its own copy of this orchestration.
 */
public interface AnalyzeVacancyUseCase {

    AnalyzeVacancyResult analyze(UUID vacancyId);
}
