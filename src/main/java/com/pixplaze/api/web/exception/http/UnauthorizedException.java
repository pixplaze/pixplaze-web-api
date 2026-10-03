package com.pixplaze.api.web.exception.http;

/// HTTP-семантическое исключение → 401. Статус назначается в {@code ApiExceptionHandler}.
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException() {
        super("Unauthorized!");
    }

    public UnauthorizedException(String message) {
        super(message);
    }
}
