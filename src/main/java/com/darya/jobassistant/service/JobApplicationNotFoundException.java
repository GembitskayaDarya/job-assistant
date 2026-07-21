package com.darya.jobassistant.service;

public class JobApplicationNotFoundException extends RuntimeException {

    public JobApplicationNotFoundException(Long id) {
        super("Job application not found with id: " + id);
    }
}