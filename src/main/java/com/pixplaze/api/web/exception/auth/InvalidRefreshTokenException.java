package com.pixplaze.api.web.exception.auth;

/**
 * Refresh-токен невалиден: не найден, истёк, уже отозван или предъявлен повторно
 * (reuse). В последнем случае вся цепочка токенов профиля отзывается.
 */
public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException(String reason) {
        super(reason);
    }
}
