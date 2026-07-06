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
import java.util.stream.IntStream;

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
                if (root.get("description").has("extra")) {
                    server.setMotd(MinecraftJsonConverter.convertToLegacy(root.get("description")));
                } else {
                    server.setMotd(root.get("description").get("text").asString());
                }
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
}
