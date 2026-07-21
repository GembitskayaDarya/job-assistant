package com.darya.jobassistant.exception;

import java.util.UUID;

public class CompanyNotFoundException extends NotFoundException {

    public CompanyNotFoundException(UUID id) {
        super("Company not found with id: " + id);
    }
}
