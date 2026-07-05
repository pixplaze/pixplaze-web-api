package com.pixplaze.api.web.data.dto;

import java.util.List;
import java.util.Map;

/**
 * Tier-3 push: сервер с нашим плагином периодически присылает своё специфическое состояние
 * (heartbeat) под своим MAD-токеном ({@code ROLE_MINECRAFT_SERVER}). Идентичность сервера берётся
 * из токена (не из тела), поэтому тело — только полезные данные.
 *
 * @param tps          текущий TPS
 * @param uptimeMillis аптайм сервера
 * @param difficulty   сложность
 * @param plugins      список плагинов
 * @param metadata     произвольные кастомные поля интеграции
 */
public record MinecraftServerHeartbeatRequest(
        Double tps,
        Long uptimeMillis,
        String difficulty,
        List<String> plugins,
        Map<String, Object> metadata
) {}
