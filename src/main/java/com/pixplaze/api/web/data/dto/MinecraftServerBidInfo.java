package com.pixplaze.api.web.data.dto;

import com.pixplaze.api.ext.data.server.MinecraftServerStateInfo;
import com.pixplaze.api.web.data.server.MinecraftServerBidStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "Заявка на регистрацию сервера: для страницы заявок владельца и админа")
public record MinecraftServerBidInfo(
        Long id,
        String name,
        @Schema(description = "Игровой адрес", example = "play.example.net")
        String host,
        Integer port,
        MinecraftServerStateInfo.IntegrationStatus integration,
        MinecraftServerBidStatus status,
        @Schema(description = "Код заявки: с интеграцией — в конфиг плагина, без — в MOTD сервера", example = "A1B2C3D4")
        String inviteCode,
        @Schema(description = "Ник владельца в Minecraft (только у заявки с интеграцией)")
        String ownerUsername,
        @Schema(description = "Имя профиля, подавшего заявку")
        String applicant,
        OffsetDateTime createdAt,
        @Schema(description = "Когда открытая заявка истечёт; null у закрытой")
        OffsetDateTime expiresAt
) {}
