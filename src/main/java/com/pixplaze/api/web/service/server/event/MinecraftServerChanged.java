package com.pixplaze.api.web.service.server.event;

import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServer;

/// Изменилась строка {@code minecraft_server} (описание, лицензия, бан); {@code server} — её состояние после записи.
public record MinecraftServerChanged(MinecraftServer server) {
}
