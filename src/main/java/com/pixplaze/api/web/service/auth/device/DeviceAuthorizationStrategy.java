package com.pixplaze.api.web.service.auth.device;

import com.pixplaze.api.ext.data.auth.AuthorizationToken;
import com.pixplaze.api.web.service.auth.device.model.DeviceAuthorizationContext;
import com.pixplaze.api.ext.data.Authority;
import com.pixplaze.api.ext.data.auth.AuthorizationDetails;
import com.pixplaze.api.web.data.dto.DeviceAuthorizationInfo;
import com.pixplaze.api.web.exception.auth.DeviceAuthorizationException;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Стратегия device-flow для конкретного типа субъекта. Параметризуется типом полезной нагрузки
 * ({@code A}) и типом ответа-токена ({@code T}). Субъектный принципал стратегия строит уже после
 * одобрения — внутри {@link #authorize}.
 *
 * <p>Всё, что нужно для обработки, приходит одним {@link DeviceAuthorizationContext}: там уже
 * разобранные детали, восстановленная привилегия и, на этапе выдачи токенов, загруженный
 * одобряющий. Дженерик {@code A} живёт на контексте, а не на хранимой сессии — поэтому сессия
 * остаётся плоской и сериализуемой, а стратегия не теряет типизацию деталей.
 *
 * @param <A> тип полезной нагрузки запроса; {@code NoAuthorizationDetails}, если её нет
 * @param <T> тип ответа с токенами
 */
@Component
public interface DeviceAuthorizationStrategy<A extends AuthorizationDetails, T extends AuthorizationToken> {

    /// Разбирает полезную нагрузку запроса. Единственное место, где известен её конкретный тип,
    /// поэтому хранилище зовёт именно этот метод, отдавая сырую строку.
    A parse(@Nullable String clientId, Authority authority, String authorizationDetailsString);

    /// Предварительная проверка до публикации сессии. Одобряющего на этом этапе ещё нет.
    default void validate(DeviceAuthorizationContext<A> context) throws DeviceAuthorizationException {}

    /// Выдача токенов. Вызывается только после одобрения, поэтому {@code context.approver()}
    /// здесь гарантированно заполнен.
    T authorize(DeviceAuthorizationContext<A> context);

    /// Универсальная проекция подтверждаемой авторизации для подтверждающего устройства
    /// (RFC 8628 §3.3). Считается на лету — ничего лишнего в сессии не хранится.
    DeviceAuthorizationInfo describe(DeviceAuthorizationContext<A> context);
}
