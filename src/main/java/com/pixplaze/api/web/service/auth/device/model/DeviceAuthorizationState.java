package com.pixplaze.api.web.service.auth.device.model;

import com.pixplaze.api.web.data.auth.DeviceAuthorizationStatus;
import org.jspecify.annotations.Nullable;

/**
 * Снимок всего, что хранилище знает о запросе устройства, прочитанный одной операцией.
 *
 * <p>Единственная модель чтения для всех путей — опроса, подтверждения и выдачи. Детали лежат
 * сырой строкой и разбираются только там, где нужны (проверка, экран подтверждения, выдача),
 * поэтому чтение их на опросе ничего не стоит: это ещё одно поле того же {@code HMGET}.
 *
 * <p>Статус не хранится, а выводится: нет решения — {@code PENDING}, иначе статус решения.
 *
 * @param request      неизменяемый запрос устройства
 * @param details      полезная нагрузка запроса как её прислало устройство; {@code null}, если её нет
 * @param decision     решение подтверждающего; {@code null}, пока его нет
 * @param attemptsLeft сколько опросов ещё разрешено. На пути опроса — уже за вычетом текущего,
 *                     поэтому отрицательное значение значит «этот опрос вышел за бюджет»
 */
public record DeviceAuthorizationState(
        DeviceAuthorizationRequest request,
        @Nullable String details,
        @Nullable ApproverDecision decision,
        int attemptsLeft
) {

    public DeviceAuthorizationStatus status() {
        return decision == null ? DeviceAuthorizationStatus.PENDING : decision.status();
    }

    /// Остался ли у устройства хотя бы один опрос, на котором оно сможет забрать токены.
    public boolean canBePolledAgain() {
        return attemptsLeft > 0;
    }
}
