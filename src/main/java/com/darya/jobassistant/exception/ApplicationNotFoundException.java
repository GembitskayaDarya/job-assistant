package com.darya.jobassistant.exception;

import java.util.UUID;

public class ApplicationNotFoundException extends NotFoundException {

    public ApplicationNotFoundException(UUID id) {
        super("Application not found with id: " + id);
    }
}
