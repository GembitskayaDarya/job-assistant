package com.darya.jobassistant.exception;

public class JobSourceNotFoundException extends NotFoundException {

    public JobSourceNotFoundException(String sourceName) {
        super("Job source not found: " + sourceName);
    }
}
