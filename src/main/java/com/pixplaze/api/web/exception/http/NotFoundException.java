package com.pixplaze.api.web.exception.http;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class NotFoundException extends RuntimeException {

    public NotFoundException() {
        super("Entity not found!");
    }

    public NotFoundException(String message) {
        super(message);
    }

    public NotFoundException(Class<?> entityClass) {
        super("Entity '%s' not found!".formatted(entityClass.getSimpleName()));
    }
}
