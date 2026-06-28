package com.pixplaze.api.web.exception.auth;

import lombok.Getter;

/**
 * Ошибка token-эндпоинта (device flow / refresh_token grant). Несёт типизированный
 * {@link DeviceAuthorizationError}; HTTP-отображение собирается в обработчике контроллера.
 */
@Getter
public class DeviceAuthorizationException extends RuntimeException {
    private final DeviceAuthorizationError error;

    public DeviceAuthorizationException() {
        this(DeviceAuthorizationError.SERVER_ERROR);
    }

    public DeviceAuthorizationException(DeviceAuthorizationError error) {
        super(error.getCode());
        this.error = error;
    }

    public DeviceAuthorizationException(DeviceAuthorizationError error, Throwable cause) {
        super(error.getCode(), cause);
        this.error = error;
    }
}
