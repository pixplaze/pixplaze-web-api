package com.pixplaze.api.web.configuration.json;

import com.pixplaze.api.ext.data.server.MinecraftServerCoreInfo;
import com.pixplaze.api.ext.data.server.MinecraftServerInfo;
import com.pixplaze.api.web.service.server.MinecraftServerStates;
import org.jspecify.annotations.NonNull;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
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
        String coreVersion = getVersionRange(versionString);
        String coreName = getVersionName(versionString);
        return new MinecraftServerCoreInfo(coreName, coreVersion);
    }

    private static String getVersionName(String versionString) {
        int versionPrefixIndex = versionString.indexOf(" " + 1);
        if (versionPrefixIndex == -1) {
            return "N/A";
        }
        return versionString.substring(versionPrefixIndex);
    }

    private static String getVersionRange(String string) {
        Pattern pattern = Pattern.compile("([\\dxX.]+)[-\\s/,]?");
        string = string.trim().substring(string.indexOf(" ") + 1);
        Matcher matcher = pattern.matcher(string);
        List<String> versions = parseVersionRange(matcher);
        return joinVersionRange(versions);
    }

    private static @NonNull List<String> parseVersionRange(Matcher matcher) {
        return matcher.results()
                .map(matchResult -> matchResult.group(1))
                .filter(Objects::nonNull)
                .map(String::trim)
                .map(MinecraftStatusParser::removeVersionWildcard)
                .toList();
    }

    private static String joinVersionRange(List<String> versionRanges) {
        if (versionRanges.size() > 2) {
            return String.join(", ", versionRanges);
        }

        if (!versionRanges.isEmpty()) {
            return String.join(" - ", versionRanges);
        }

        return "N/A";
    }

    private static @NonNull String removeVersionWildcard(String s) {
        return s.replaceAll("\\.[xX]", "");
    }
}
