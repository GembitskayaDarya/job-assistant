package com.darya.jobassistant.exception;

import java.util.UUID;

public class VacancyNotFoundException extends NotFoundException {

    public VacancyNotFoundException(UUID id) {
        super("Vacancy not found with id: " + id);
    }
}
