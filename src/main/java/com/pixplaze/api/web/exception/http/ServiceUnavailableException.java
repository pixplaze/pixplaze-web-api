package com.pixplaze.api.web.exception.http;

/// HTTP-семантическое исключение → 503. Статус назначается в {@code ApiExceptionHandler}.
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException() {
        super("Service unavailable!");
    }

    public ServiceUnavailableException(String message) {
        super(message);
    }
}
