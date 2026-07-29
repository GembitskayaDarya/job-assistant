package com.darya.jobassistant.vacancyextraction.port;

import com.darya.jobassistant.vacancyextraction.model.ExtractedVacancyData;
import com.darya.jobassistant.vacancyextraction.model.VacancyExtractionRequest;

public interface VacancyExtractionPort {

    ExtractedVacancyData extract(VacancyExtractionRequest request);
}
