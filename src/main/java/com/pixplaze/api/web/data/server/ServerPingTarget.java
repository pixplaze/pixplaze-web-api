package com.pixplaze.api.web.data.server;

/**
 * Цель для Tier-2 пинга: id сервера + адрес (host + Java-порт). Отдельная лёгкая проекция,
 * чтобы рефрешер не таскал полный листинг ради адреса.
 */
public record ServerPingTarget(
        long minecraftServerId,
        String host,
        int port
) {}
