package com.pixplaze.api.web.exception.auth;

import com.pixplaze.api.ext.data.oauth.OAuthError;
import lombok.Getter;

/**
 * Ошибка token-эндпоинта (device flow / refresh_token grant). Несёт типизированный
 * {@link OAuthError}; HTTP-отображение собирается в обработчике контроллера.
 *
 * <p>{@code reason} — внутренняя причина отказа для лога (DEBUG в {@code ApiExceptionHandler}); клиенту
 * уходит только код ошибки, причина наружу не попадает.
 */
@Getter
public class DeviceAuthorizationException extends RuntimeException {
    private final OAuthError error;
    private final String reason;

    public DeviceAuthorizationException() {
        this(OAuthError.SERVER_ERROR);
    }

    public DeviceAuthorizationException(OAuthError error) {
        this(error, (String) null);
    }

    public DeviceAuthorizationException(OAuthError error, Throwable cause) {
        this(error, null, cause);
    }

    public DeviceAuthorizationException(OAuthError error, String reason) {
        super(error.code());
        this.error = error;
        this.reason = reason;
    }

    public DeviceAuthorizationException(OAuthError error, String reason, Throwable cause) {
        super(error.code(), cause);
        this.error = error;
        this.reason = reason;
    }

    /// Причина для лога: своя и сообщение исходного исключения, если оно есть.
    public String describe() {
        final var cause = getCause() == null ? null : getCause().getClass().getSimpleName() + ": " + getCause().getMessage();
        if (reason == null) {
            return cause == null ? "no reason given" : cause;
        }
        return cause == null ? reason : reason + " (" + cause + ")";
    }
}
