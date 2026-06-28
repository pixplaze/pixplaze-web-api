package com.pixplaze.api.web.service.auth.device;

import com.pixplaze.api.ext.data.Authority;
import com.pixplaze.api.web.data.auth.DeviceAuthorizationSession;
import com.pixplaze.api.web.data.dto.DeviceAuthorizationInfo;
import com.pixplaze.api.web.exception.auth.DeviceAuthorizationException;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Стратегия device-flow для конкретного типа субъекта. Параметризуется типом сырых
 * {@code authorizationDetails} ({@code A}), которые живут в {@link DeviceAuthorizationSession}
 * на время процесса, и типом ответа-токена ({@code T}). Сам субъектный принципал стратегия
 * строит уже после одобрения — внутри {@link #authorize(DeviceAuthorizationSession)}.
 *
 * @param <A> тип сырых authorizationDetails запроса (server / player / {@link Void} для profile)
 * @param <T> тип ответа с токенами
 */
@Component
public interface DeviceAuthorizationStrategy<A, T> {
    /// Десериализует сырые authorizationDetails из запроса. Возвращает {@code null}, если у
    /// стратегии нет отдельного payload (profile-flow).
    default @Nullable A parse(@Nullable String clientId, Authority authority, String authorizationDetailsString) {
        return null;
    }
    default void validate(DeviceAuthorizationSession<A> deviceAuthorizationSession) throws DeviceAuthorizationException {}
    T authorize(DeviceAuthorizationSession<A> deviceAuthorizationSession);

    /// Универсальная проекция подтверждаемой авторизации для подтверждающего устройства
    /// (RFC 8628 §3.3). Считается на лету из состояния сессии — ничего в сессии не хранится.
    DeviceAuthorizationInfo describe(DeviceAuthorizationSession<A> deviceAuthorizationSession);
}
