package com.pixplaze.api.web.data.server;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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
public record PluginSnapshot(
        Double tps,
        Long uptimeMillis,
        String difficulty,
        List<String> plugins,
        Map<String, Object> metadata,
        Instant fetchedAt
) {}
