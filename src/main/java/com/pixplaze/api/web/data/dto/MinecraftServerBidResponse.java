package com.pixplaze.api.web.data.dto;

import com.pixplaze.api.ext.data.server.MinecraftServerInfo;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Созданная заявка: код регистрации для конфига плагина и превью сервера")
public record MinecraftServerBidResponse(
        @Schema(description = "Код заявки: с интеграцией — в конфиг плагина для device-flow, без — в MOTD сервера для подтверждения пингом", example = "A1B2C3D4")
        String inviteCode,
        @Schema(description = "Превью сервера: название, игровой адрес и иконка, если сервер ответил на пинг")
        MinecraftServerInfo preview
) {}
