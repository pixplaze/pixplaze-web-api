package com.pixplaze.api.web.exception.http;

/// HTTP-семантическое исключение → 403. Статус назначается в {@code ApiExceptionHandler}.
public class ForbiddenException extends RuntimeException {

    public ForbiddenException() {
        super("Forbidden!");
    }

    public ForbiddenException(String message) {
        super(message);
    }
}
