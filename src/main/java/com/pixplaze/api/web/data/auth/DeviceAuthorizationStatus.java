package com.pixplaze.api.web.data.auth;

/**
 * Позиция сессии device-flow (RFC 8628) в флоу; определяет ответ на опрос.
 *
 * <p>Вынесен из сессии отдельным типом, потому что виден наружу: его отдаёт подтверждающему
 * устройству {@code DeviceAuthorizationInfo}, тогда как сама сессия — служебная запись флоу.
 */
public enum DeviceAuthorizationStatus {
    PENDING,
    APPROVED,
    DENIED;

    public String code() {
        return name().toLowerCase();
    }
}
