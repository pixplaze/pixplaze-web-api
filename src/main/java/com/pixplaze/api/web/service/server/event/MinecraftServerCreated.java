package com.pixplaze.api.web.service.server.event;

import com.pixplaze.api.ext.data.server.MinecraftServerStateInfo;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServer;

/// Сервер зарегистрирован: после коммита он попадает в снапшот, не дожидаясь base-sync.
public record MinecraftServerCreated(MinecraftServer server, MinecraftServerStateInfo.IntegrationStatus integration) {
}
