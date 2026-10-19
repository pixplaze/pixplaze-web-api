package com.pixplaze.api.web.data.dto;

import java.util.List;
import java.util.UUID;

/**
 * Игрок профиля для настроек: серверы, на которых он состоит, и признак основного игрока на каждом.
 *
 * @param skinHeadBase64 голова скина, Base64 PNG
 * @param servers        серверы игрока; {@code isPrimary} — от его имени профиль работает с сервером
 *                       (консоль, чат)
 */
public record ProfilePlayerInfo(UUID uuid, String username, String skinHeadBase64, List<Server> servers) {

    public record Server(Long minecraftServerId, String name, boolean isOperator, boolean isPrimary) {
    }
}
