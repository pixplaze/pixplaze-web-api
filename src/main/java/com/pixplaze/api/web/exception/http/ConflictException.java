package com.pixplaze.api.web.exception.http;

/// HTTP-семантическое исключение → 409. Статус назначается в {@code ApiExceptionHandler}.
public class ConflictException extends RuntimeException {

    public ConflictException() {
        super("Conflict!");
    }

    public ConflictException(String message) {
        super(message);
    }
}
