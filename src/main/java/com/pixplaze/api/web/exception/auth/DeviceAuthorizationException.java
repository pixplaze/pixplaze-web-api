package com.pixplaze.api.web.exception.auth;

import com.pixplaze.api.ext.data.oauth.OAuthError;
import lombok.Getter;

/**
 * Ошибка token-эндпоинта (device flow / refresh_token grant). Несёт типизированный
 * {@link OAuthError}; HTTP-отображение собирается в обработчике контроллера.
 */
@Getter
public class DeviceAuthorizationException extends RuntimeException {
    private final OAuthError error;

    public DeviceAuthorizationException() {
        this(OAuthError.SERVER_ERROR);
    }

    public DeviceAuthorizationException(OAuthError error) {
        super(error.code());
        this.error = error;
    }

    public DeviceAuthorizationException(OAuthError error, Throwable cause) {
        super(error.code(), cause);
        this.error = error;
    }
}
