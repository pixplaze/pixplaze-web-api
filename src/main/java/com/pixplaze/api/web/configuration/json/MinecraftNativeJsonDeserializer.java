package com.pixplaze.api.web.configuration.json;

import com.pixplaze.api.ext.data.player.MinecraftPlayerListInfo;
import com.pixplaze.api.web.data.server.MinecraftServer;
import com.pixplaze.api.web.data.server.MinecraftServerCore;
import com.pixplaze.api.web.data.server.MinecraftServerState;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

public class MinecraftNativeJsonDeserializer extends StdDeserializer<MinecraftServer> {
    public MinecraftNativeJsonDeserializer() {
        super(MinecraftServer.class);
    }

    @Override
    public synchronized MinecraftServer deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) {
        MinecraftServer minecraftServer = new MinecraftServer();
        minecraftServer.setCore(new MinecraftServerCore());
        minecraftServer.setState(new MinecraftServerState());

        if (jsonParser.currentToken() == null) {
            jsonParser.nextToken();
        }

        while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = jsonParser.currentName();
            jsonParser.nextToken();

            switch (fieldName) {
                case "description" -> minecraftServer.setMotd(jsonParser.getValueAsString());
                case "favicon" -> minecraftServer.setFavicon(jsonParser.getValueAsString());
                case "version" -> {
                    while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
                        String vField = jsonParser.currentName();
                        jsonParser.nextToken();
                        if ("name".equals(vField)) {
                            minecraftServer.getCore().setName(jsonParser.getValueAsString());
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

                        minecraftServer.getState().setPlayers(new MinecraftPlayerListInfo(playersMax, playersOnline));
                    }
                }

                default -> jsonParser.skipChildren();
            }
        }

        return minecraftServer;
    }
}
