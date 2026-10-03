package com.pixplaze.api.web.exception.http;

/// HTTP-семантическое исключение → 404. Статус назначается в {@code ApiExceptionHandler}
/// (типовой {@code @ExceptionHandler}), а не аннотацией — доменные классы остаются чистыми POJO.
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
