package com.pixplaze.api.web.service.server.event;

/// Изменились адреса сервера; полный набор перечитывается после коммита.
public record MinecraftServerHostsChanged(long minecraftServerId) {
}
