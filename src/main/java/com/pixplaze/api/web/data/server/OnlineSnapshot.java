package com.pixplaze.api.web.data.server;

import java.time.Instant;

/**
 * Tier-2: онлайн-данные, полученные по протоколу Minecraft (status+ping), — то же, что видно в
 * встроенном списке серверов. Доступны для ЛЮБОГО достижимого сервера, плагин не нужен.
 * {@code null}-снимок ⇒ сервер сейчас недоступен (в листинге показывается OFFLINE + база).
 *
 * @param motd           live-MOTD с сервера
 * @param version        версия/ядро (как отдаёт status)
 * @param playersOnline  игроков онлайн
 * @param playersMax     слотов
 * @param faviconBase64  иконка сервера (data-URI/base64), если отдал status
 * @param pingMillis     латентность в мс
 * @param fetchedAt      когда снят (для freshness/адаптивной каденции)
 */
public record OnlineSnapshot(
        String motd,
        String core,
        String version,
        Integer playersOnline,
        Integer playersMax,
        String faviconBase64,
        Long pingMillis,
        Instant fetchedAt
) {}
