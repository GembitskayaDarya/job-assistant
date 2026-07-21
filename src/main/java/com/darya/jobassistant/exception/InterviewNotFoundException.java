package com.darya.jobassistant.exception;

import java.util.UUID;

public class InterviewNotFoundException extends NotFoundException {

    public InterviewNotFoundException(UUID id) {
        super("Interview not found with id: " + id);
    }
}
