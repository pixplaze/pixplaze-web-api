package com.pixplaze.api.web.service.auth.device.model;

import com.pixplaze.api.ext.data.auth.Authority;
import com.pixplaze.api.web.data.auth.Scopes;

/**
 * Запрос устройства на авторизацию (RFC 8628 §3.1) в том виде, в котором он хранится.
 *
 * <p><b>Неизменяем с момента создания.</b> Ни статуса, ни одобряющего здесь нет: решение
 * подтверждающего живёт отдельной одноразовой записью ({@link ApproverDecision}), а статус из неё
 * выводится. Благодаря этому в хранилище нет ни одной операции «прочитать значение — записать
 * новое», а значит, нет и условной перезаписи, которую пришлось бы защищать.
 *
 * <p>Запись плоская и сериализуемая: только строки.
 *
 * @param clientId       идентификатор запрашивающего устройства; сверяется на каждом опросе
 * @param userCode       короткий код, который человек вводит на странице подтверждения
 * @param deviceCodeHash хэш device-кода — ключ хранения; сырой код не хранится нигде
 * @param scope          запрошенная привилегия строкой, см. {@link Scopes}
 */
public record DeviceAuthorizationRequest(
        String clientId,
        String userCode,
        String deviceCodeHash,
        String scope
) {

    /** Тот же запрос под другим user-кодом — на случай коллизии кодов при создании. */
    public DeviceAuthorizationRequest withUserCode(String userCode) {
        return new DeviceAuthorizationRequest(clientId, userCode, deviceCodeHash, scope);
    }

    /** Запрошенная привилегия, восстановленная из {@link #scope}. */
    public Authority authority() {
        return Scopes.toAuthority(scope);
    }
}
