package com.pixplaze.api.web.data.server;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record MinecraftServerSnapshot() {

    /**
     * Tier-2: онлайн-данные, полученные по протоколу Minecraft (status+ping), — то же, что видно в
     * встроенном списке серверов. Доступны для ЛЮБОГО достижимого сервера, плагин не нужен.
     * {@code null}-снимок ⇒ сервер сейчас недоступен (в листинге показывается OFFLINE + база).
     *
     * @param motd           live-MOTD с сервера
     * @param version        версия/ядро (как отдаёт status)
     * @param playersOnline  игроков онлайн
     * @param playersMax     слотов
     * @param iconBase64  иконка сервера (data-URI/base64), если отдал status
     * @param pingMillis     латентность в мс
     * @param fetchedAt      когда снят (для freshness/адаптивной каденции)
     */
    public record Online(
            String motd,
            String core,
            String version,
            Integer playersOnline,
            Integer playersMax,
            String iconBase64,
            Long pingMillis,
            Instant fetchedAt
    ) {}

    /**
     * Tier-3: специфические данные, которые присылает сам сервер с установленным нашим плагином
     * (push-heartbeat; в фолбэке — pull). Доступны только при {@link IntegrationType#PLUGIN}.
     * Протухший снимок (старый {@code fetchedAt}) трактуется как «плагин молчит» → тир скрывается.
     *
     * @param tps           текущий TPS
     * @param uptimeMillis  аптайм сервера
     * @param difficulty    сложность
     * @param plugins       список плагинов
     * @param metadata      произвольные кастомные поля интеграции
     * @param fetchedAt     когда получен (для freshness)
     */
    public record Plugin(
            Double tps,
            Long uptimeMillis,
            String difficulty,
            List<String> plugins,
            Map<String, Object> metadata,
            Instant fetchedAt
    ) {}
}
