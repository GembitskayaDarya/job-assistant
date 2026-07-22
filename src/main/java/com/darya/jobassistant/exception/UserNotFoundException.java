package com.darya.jobassistant.exception;

import java.util.UUID;

public class UserNotFoundException extends NotFoundException {

    public UserNotFoundException(UUID id) {
        super("User not found with id: " + id);
    }
}
