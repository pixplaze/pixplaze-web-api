package com.pixplaze.api.web.data.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Созданная заявка с кодом регистрации для конфига сервера")
public record MinecraftServerBidResponse(
        Long id,
        String name,
        String host,
        String ownerUsername,
        @Schema(description = "Код регистрации (помещается в конфиг сервера для device-flow)", example = "A1B2C3D4")
        String inviteCode
) {}
