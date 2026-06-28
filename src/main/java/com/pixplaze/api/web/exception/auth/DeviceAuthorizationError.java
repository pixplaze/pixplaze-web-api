package com.pixplaze.api.web.exception.auth;

/**
 * Коды ошибок token-эндпоинта (RFC 8628 §3.5 device flow + RFC 6749 §5.2).
 * Логический слой: несёт только протокольный wire-код. Маппинг в HTTP-статус —
 * задача presentation-слоя (см. обработчик {@code DeviceAuthorizationException}).
 */
public enum DeviceAuthorizationError {
    AUTHORIZATION_PENDING("authorization_pending"),
    SLOW_DOWN("slow_down"),
    ACCESS_DENIED("access_denied"),
    EXPIRED_TOKEN("expired_token"),
    INVALID_REQUEST("invalid_request"),
    INVALID_GRANT("invalid_grant"),
    UNSUPPORTED_GRANT_TYPE("unsupported_grant_type"),
    SERVER_ERROR("server_error");

    private final String code;

    DeviceAuthorizationError(String code) {
        this.code = code;
    }

    /** Протокольный код ошибки для тела ответа ({@code {"error": code}}). */
    public String getCode() {
        return code;
    }
}
