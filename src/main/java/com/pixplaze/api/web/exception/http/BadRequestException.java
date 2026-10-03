package com.pixplaze.api.web.exception.http;

/// HTTP-семантическое исключение → 400. Статус назначается в {@code ApiExceptionHandler}.
public class BadRequestException extends RuntimeException {

    public BadRequestException() {
        super("Bad request!");
    }

    public BadRequestException(String message) {
        super(message);
    }
}
