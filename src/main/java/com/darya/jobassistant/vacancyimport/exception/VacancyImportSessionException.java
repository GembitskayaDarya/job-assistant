package com.darya.jobassistant.vacancyimport.exception;

/**
 * Thrown when a {@code VacancyImportSession} operation would violate the session's state
 * machine or one of its field invariants (e.g. an invalid source URL). Never wraps a
 * persistence or Telegram exception - domain methods only throw this type.
 */
public class VacancyImportSessionException extends RuntimeException {

    public VacancyImportSessionException(String message) {
        super(message);
    }
}
