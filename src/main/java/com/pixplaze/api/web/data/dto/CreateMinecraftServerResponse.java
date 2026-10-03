package com.pixplaze.api.web.data.dto;

import com.pixplaze.api.web.data.server.IntegrationType;
import com.pixplaze.api.web.data.server.MinecraftServerStatus;

public record CreateMinecraftServerResponse(
        MinecraftServerStatus status,
        IntegrationType integrationType
) {
    public static CreateMinecraftServerResponse integrated() {
        return new CreateMinecraftServerResponse(MinecraftServerStatus.ONLINE, IntegrationType.PLUGIN);
    }
}
