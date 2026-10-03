package com.pixplaze.api.web.exception.http;

/// HTTP-семантическое исключение → 501. Статус назначается в {@code ApiExceptionHandler}.
public class NotImplementedException extends RuntimeException {
    public NotImplementedException() {
        this("Call is not implemented yet!");
    }

    public NotImplementedException(String message) {
        super(message);
    }
}
