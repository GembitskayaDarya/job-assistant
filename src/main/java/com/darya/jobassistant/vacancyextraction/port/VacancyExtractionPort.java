package com.darya.jobassistant.vacancyextraction.port;

import com.darya.jobassistant.vacancyextraction.model.ExtractedVacancyData;

public interface VacancyExtractionPort {

    ExtractedVacancyData extract(String rawDescription);
}
