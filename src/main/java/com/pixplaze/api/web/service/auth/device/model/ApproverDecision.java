package com.pixplaze.api.web.service.auth.device.model;

import com.pixplaze.api.web.data.auth.DeviceAuthorizationDecision;
import com.pixplaze.api.web.data.auth.DeviceAuthorizationStatus;
import com.pixplaze.api.web.data.user.ApplicationClientPrincipal;

/**
 * Решение подтверждающего по запросу устройства. Записывается один раз и больше не меняется:
 * хранилище принимает его операцией, которая может удаться лишь однажды.
 *
 * <p>От принципала сохраняются только id профиля и IP: полный принципал грузится на выдаче
 * токенов, поэтому права читаются свежими, а не замороженными на момент решения.
 *
 * @param decision          разрешил или отказал
 * @param approverProfileId профиль, принявший решение
 * @param approverIpAddress транспортный IP подтверждающего
 */
public record ApproverDecision(
        DeviceAuthorizationDecision decision,
        Long approverProfileId,
        String approverIpAddress
) {

    public static ApproverDecision of(DeviceAuthorizationDecision decision, ApplicationClientPrincipal approver) {
        return new ApproverDecision(decision, approver.getId(), approver.getIpAddress());
    }

    /** Статус флоу, в который переводит это решение. */
    public DeviceAuthorizationStatus status() {
        return switch (decision) {
            case ALLOW -> DeviceAuthorizationStatus.APPROVED;
            case DENY -> DeviceAuthorizationStatus.DENIED;
        };
    }
}
