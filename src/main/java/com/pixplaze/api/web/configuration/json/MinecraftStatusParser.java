package com.pixplaze.api.web.configuration.json;

import com.pixplaze.api.ext.data.server.MinecraftServerCoreInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerInfo;
import com.pixplaze.api.web.service.server.MinecraftServerStates;
import tools.jackson.databind.JsonNode;

import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Разбор ответа status протокола Minecraft (то, что видно во встроенном списке серверов) в описание
 * сервера (motd, иконка, ядро) и часть состояния от пинга (задержка, игроки). Не регистрируется в общем
 * {@code JsonMapper}: ответ протокола — не {@link MinecraftServerInfo}, и регистрация сломала бы обычную
 * десериализацию этого DTO.
 */
public final class MinecraftStatusParser {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^([A-Za-z\\s]+?)\\s*([0-9]+(?:\\.[0-9xX]+)+)(?:\\s*[\\-/]\\s*([0-9x]+(?:\\.[0-9xX]+)+))?");

    private MinecraftStatusParser() {
    }

    public static MinecraftServerInfo parse(JsonNode root, long pingMillis) {
        Integer playersOnline = null;
        Integer playersMax = null;
        if (root.has("players")) {
            final var players = root.get("players");
            playersMax = players.path("max").asInt(0);
            playersOnline = players.path("online").asInt(0);
        }

        return MinecraftServerInfo.builder()
                .motd(parseMotd(root))
                .iconBase64(root.has("favicon") ? root.get("favicon").asString() : null)
                .core(parseCore(root))
                .state(MinecraftServerStates.ping(pingMillis, playersOnline, playersMax))
                .build();
    }

    private static String parseMotd(JsonNode root) {
        if (!root.has("description")) {
            return null;
        }

        final var description = root.get("description");
        if (!description.has("text")) {
            return description.asString();
        }

        return description.has("extra")
                ? MinecraftJsonConverter.convertToLegacy(description)
                : description.get("text").asString();
    }

    /// {@code version.name} вида «Paper 1.21.4» или «Requires MC 1.8 / 1.21» → имя ядра и версия(и).
    private static MinecraftServerCoreInfo parseCore(JsonNode root) {
        if (!root.has("version") || !root.get("version").has("name")) {
            return null;
        }

        final var versionString = root.get("version").get("name").asString();
        final var matcher = VERSION_PATTERN.matcher(versionString);
        if (!matcher.matches()) {
            return new MinecraftServerCoreInfo(versionString, null);
        }

        final var coreVersions = IntStream.rangeClosed(2, matcher.groupCount())
                .mapToObj(matcher::group)
                .filter(Objects::nonNull)
                .map(String::trim)
                .map(version -> version.replaceAll("\\.[xX]", ""))
                .toList();

        var coreVersion = versionString;
        if (coreVersions.size() > 2) {
            coreVersion = String.join(", ", coreVersions);
        } else if (!coreVersions.isEmpty()) {
            coreVersion = String.join(" - ", coreVersions);
        }

        return new MinecraftServerCoreInfo(matcher.group(1), coreVersion);
    }
}
