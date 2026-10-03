package com.pixplaze.api.web.exception.auth;

import com.pixplaze.api.web.exception.http.UnauthorizedException;

/**
 * Refresh-токен невалиден: не найден, истёк, уже отозван или предъявлен повторно
 * (reuse). В последнем случае вся цепочка токенов профиля отзывается. → 401
 * (наследует {@link UnauthorizedException}); в OAuth token-эндпоинте конвертируется
 * в {@code invalid_grant} до всплытия.
 */
public class InvalidRefreshTokenException extends UnauthorizedException {
    public InvalidRefreshTokenException(String reason) {
        super(reason);
    }
}
