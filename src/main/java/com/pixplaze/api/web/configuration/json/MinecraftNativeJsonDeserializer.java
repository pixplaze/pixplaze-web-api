package com.pixplaze.api.web.configuration.json;

import com.pixplaze.api.ext.data.player.MinecraftPlayerListInfo;
import com.pixplaze.api.web.data.server.MinecraftServerCore;
import com.pixplaze.api.web.data.server.MinecraftServerState;
import com.pixplaze.api.web.data.server.RawMinecraftServer;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class MinecraftNativeJsonDeserializer extends StdDeserializer<RawMinecraftServer> {
    private static final Pattern versionPattern = Pattern.compile("^([A-Za-z\\s]+?)\\s*([0-9]+(?:\\.[0-9xX]+)+)(?:\\s*[\\-/]\\s*([0-9x]+(?:\\.[0-9xX]+)+))?");
    public MinecraftNativeJsonDeserializer() {
        super(RawMinecraftServer.class);
    }

    @Override
    public synchronized RawMinecraftServer deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) {
        JsonNode root = deserializationContext.readTree(jsonParser); // Читаем весь JSON в дерево

        RawMinecraftServer server = new RawMinecraftServer();
        server.setCore(new MinecraftServerCore());
        server.setState(new MinecraftServerState());

        // Читаем description.text
        if (root.has("description")) {
            if (root.get("description").has("text")) {
                server.setMotd(root.get("description").get("text").asString());
            } else {
                server.setMotd(root.get("description").asString());
            }
        }

        // Читаем favicon
        if (root.has("favicon")) {
            server.setFavicon(root.get("favicon").asString());
        }

        // Читаем version.name
        if (root.has("version") && root.get("version").has("name")) {
            final var versionString = root.get("version").get("name").asString();
            server.getCore().setName(versionString);

            final var versionMatcher = versionPattern.matcher(versionString);
            if (versionMatcher.matches()) {
                final var groupCount = versionMatcher.groupCount();

                final var coreName = versionMatcher.group(1);
                final var coreVersions = IntStream.rangeClosed(2, groupCount)
                        .mapToObj(versionMatcher::group)
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .map(s -> s.replaceAll("\\.[xX]", ""))
                        .toList();
                var coreVersion = versionString;

                if (coreVersions.size() > 2) {
                    coreVersion = String.join(", ", coreVersions);
                } else if (!coreVersions.isEmpty()) {
                    coreVersion = String.join(" - ", coreVersions);
                }

                server.getCore().setName(coreName);
                server.getCore().setVersion(coreVersion);
            }
        }

        // Читаем players.max и players.online
        if (root.has("players")) {
            JsonNode playersNode = root.get("players");
            int max = playersNode.path("max").asInt(0);
            int online = playersNode.path("online").asInt(0);
            server.getState().setPlayers(new MinecraftPlayerListInfo(max, online));
        }

        return server;
    }


//    @Override
//    public synchronized RawMinecraftServer deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) {
//        RawMinecraftServer rawMinecraftServer = new RawMinecraftServer();
//        rawMinecraftServer.setCore(new MinecraftServerCore());
//        rawMinecraftServer.setState(new MinecraftServerState());
//
//        // Убедимся, что мы стоим на начале объекта '{'
//        if (jsonParser.currentToken() == JsonToken.START_OBJECT) {
//            jsonParser.nextToken();
//        }
//
//        // Главный цикл: читаем поля корневого объекта, пока не встретим '}'
//        while (jsonParser.currentToken() != JsonToken.END_OBJECT && jsonParser.currentToken() != null) {
//            String fieldName = jsonParser.currentName();
//            jsonParser.nextToken(); // Переходим к значению поля
//
//            switch (fieldName) {
//                case "description" -> {
//                    // description — это объект {"text": "..."}.
//                    // Вместо ручного парсинга можно попросить Jackson прочитать его как дерево.
//                    JsonNode node = jsonParser.readValueAsTree();
//                    if (node.has("text")) {
//                        rawMinecraftServer.setMotd(node.get("text").asString());
//                    }
//                }
//                case "favicon" -> {
//                    rawMinecraftServer.setFavicon(jsonParser.getValueAsString());
//                    jsonParser.nextToken();
//                }
//                case "version" -> {
//                    // Читаем свойства внутри объекта version
//                    if (jsonParser.currentToken() == JsonToken.START_OBJECT) {
//                        jsonParser.nextToken();
//                        while (jsonParser.currentToken() != JsonToken.END_OBJECT) {
//                            String vField = jsonParser.currentName();
//                            jsonParser.nextToken();
//                            if ("name".equals(vField)) {
//                                rawMinecraftServer.getCore().setName(jsonParser.getValueAsString());
//                            }
//                            jsonParser.skipChildren(); // Безопасно пропускает значение (даже если это объект) и шагает на следующий токен
//                        }
//                    }
//                    jsonParser.nextToken(); // Сдвигаемся с END_OBJECT на следующее поле корня
//                }
//                case "players" -> {
//                    var playersMax = 0;
//                    var playersOnline = 0;
//                    if (jsonParser.currentToken() == JsonToken.START_OBJECT) {
//                        jsonParser.nextToken();
//                        while (jsonParser.currentToken() != JsonToken.END_OBJECT) {
//                            String plField = jsonParser.currentName();
//                            jsonParser.nextToken();
//                            if ("max".equals(plField)) {
//                                playersMax = jsonParser.getIntValue();
//                            } else if ("online".equals(plField)) {
//                                playersOnline = jsonParser.getIntValue();
//                            }
//                            jsonParser.skipChildren(); // Пропускает всё, включая массивы (например, sample)
//                        }
//                    }
//                    rawMinecraftServer.getState().setPlayers(new MinecraftPlayerListInfo(playersMax, playersOnline));
//                    jsonParser.nextToken(); // Сдвигаемся с END_OBJECT на следующее поле корня
//                }
//                default -> {
//                    // Пропускаем любые неизвестные поля (enforcesSecureChat и т.д.)
//                    jsonParser.skipChildren();
//                    jsonParser.nextToken();
//                }
//            }
//        }
//
//        return rawMinecraftServer;
//    }
}
