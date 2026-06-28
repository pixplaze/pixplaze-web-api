package com.pixplaze.api.web.data.dto;

import com.pixplaze.api.web.data.auth.DeviceAuthorizationState;

import java.util.List;
import java.util.Map;

/**
 * Универсальный, не зависящий от типа субъекта ответ подтверждающему (AAD) устройству о
 * подтверждаемой device-авторизации (RFC 8628 §3.3, шаг user-interaction). Конверт-метаданные
 * + display-safe детали, которые формирует стратегия субъекта. Секреты (voucher/inviteCode,
 * device-коды, хэши) сюда не попадают.
 *
 * <p>Запрошенная привилегия отдаётся плоско: роль несёт {@code type}, а {@code source}/
 * {@code targets}/{@code permissions} — остальные измерения {@code Authority}. На стадии
 * {@code PENDING} {@code targets}/{@code permissions} обычно пусты (проставляются при выдаче).
 *
 * @param type        дискриминатор субъекта / запрошенная роль ({@code USER} / {@code MINECRAFT_PLAYER} / {@code MINECRAFT_SERVER})
 * @param status      стадия сессии ({@code PENDING} / {@code APPROVED} / {@code DENIED})
 * @param source      провенанс запроса ({@code src}): {@code MAD} / {@code AAD} / {@code NAD}
 * @param targets     запрошенные зоны ({@code aud})
 * @param permissions запрошенные точечные права
 * @param details     type-specific безопасные поля для отображения подтверждающему
 */
public record DeviceAuthorizationInfo(
        String type,
        DeviceAuthorizationState.Status status,
        String source,
        List<String> targets,
        List<String> permissions,
        Map<String, Object> details
) {}
