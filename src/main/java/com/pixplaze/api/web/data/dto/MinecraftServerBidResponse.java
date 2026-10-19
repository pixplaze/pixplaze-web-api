package com.pixplaze.api.web.data.dto;

import com.pixplaze.api.ext.data.server.MinecraftServerInfo;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Созданная заявка: код регистрации для MOTD и превью сервера")
public record MinecraftServerBidResponse(
        @Schema(description = "Код заявки для MOTD сервера: его подтверждает пинг или плагин (с одобрением владельца)", example = "A1B2C3D4")
        String inviteCode,
        @Schema(description = "Превью сервера: название, игровой адрес и иконка, если сервер ответил на пинг")
        MinecraftServerInfo preview
) {}
