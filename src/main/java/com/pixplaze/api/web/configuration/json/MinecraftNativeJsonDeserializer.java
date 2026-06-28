package com.pixplaze.api.web.configuration.json;

import com.pixplaze.api.ext.data.player.MinecraftPlayerListInfo;
import com.pixplaze.api.web.data.server.RawMinecraftServer;
import com.pixplaze.api.web.data.server.MinecraftServerCore;
import com.pixplaze.api.web.data.server.MinecraftServerState;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

public class MinecraftNativeJsonDeserializer extends StdDeserializer<RawMinecraftServer> {
    public MinecraftNativeJsonDeserializer() {
        super(RawMinecraftServer.class);
    }

    @Override
    public synchronized RawMinecraftServer deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) {
        RawMinecraftServer rawMinecraftServer = new RawMinecraftServer();
        rawMinecraftServer.setCore(new MinecraftServerCore());
        rawMinecraftServer.setState(new MinecraftServerState());

        if (jsonParser.currentToken() == null) {
            jsonParser.nextToken();
        }

        while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = jsonParser.currentName();
            jsonParser.nextToken();

            switch (fieldName) {
                case "description" -> rawMinecraftServer.setMotd(jsonParser.getValueAsString());
                case "favicon" -> rawMinecraftServer.setFavicon(jsonParser.getValueAsString());
                case "version" -> {
                    while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
                        String vField = jsonParser.currentName();
                        jsonParser.nextToken();
                        if ("name".equals(vField)) {
                            rawMinecraftServer.getCore().setName(jsonParser.getValueAsString());
                        } else {
                            jsonParser.skipChildren();
                        }
                    }
                }

                case "players" -> {
                    var playersMax = 0;
                    var playersOnline = 0;
                    while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
                        String plField = jsonParser.currentName();
                        jsonParser.nextToken();
                        if ("max".equals(plField)) {
                            playersMax = jsonParser.getIntValue();
                        } else if ("online".equals(plField)) {
                            playersOnline = jsonParser.getIntValue();
                        } else {
                            jsonParser.skipChildren();
                        }

                        rawMinecraftServer.getState().setPlayers(new MinecraftPlayerListInfo(playersMax, playersOnline));
                    }
                }

                default -> jsonParser.skipChildren();
            }
        }

        return rawMinecraftServer;
    }
}
