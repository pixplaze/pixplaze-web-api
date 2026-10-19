package com.pixplaze.api.web.data.dto;

import com.pixplaze.api.web.data.server.MinecraftServerRegistrationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "Заявка на регистрацию сервера — сервер с его последним кодом: для страницы заявок владельца и админа")
public record MinecraftServerBidInfo(
        @Schema(description = "Id сервера: он существует с момента заявки")
        Long id,
        String name,
        @Schema(description = "Игровой адрес; null, если заявка закрыта и адрес занял другой сервер", example = "play.example.net")
        String host,
        Integer port,
        MinecraftServerRegistrationStatus status,
        @Schema(description = "Код регистрации: владелец вписывает его в MOTD сервера", example = "A1B2C3D4")
        String inviteCode,
        @Schema(description = "Имя профиля владельца")
        String applicant,
        @Schema(description = "Когда выдан код")
        OffsetDateTime createdAt,
        @Schema(description = "Когда открытая заявка истечёт; null у закрытой")
        OffsetDateTime expiresAt
) {}
