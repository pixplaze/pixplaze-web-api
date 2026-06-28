package com.pixplaze.api.web.data.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Заявка на регистрацию Minecraft-сервера")
public record MinecraftServerBidRequest(
        @Schema(description = "Название сервера", example = "Pixplaze SMP")
        @Size(min = 1, max = 128, message = "Название сервера должно содержать от 1 до 128 символов")
        @NotBlank(message = "Название сервера не может быть пустым")
        String name,

        @Schema(description = "Хост сервера", example = "mc.pixplaze.net")
        @Size(min = 1, max = 128, message = "Хост должен содержать от 1 до 128 символов")
        @NotBlank(message = "Хост не может быть пустым")
        String host,

        @Schema(description = "MC-ник игрока, который станет владельцем сервера", example = "Notch")
        @Size(min = 1, max = 16, message = "Ник игрока должен содержать от 1 до 16 символов")
        @NotBlank(message = "Ник владельца не может быть пустым")
        String ownerUsername
) {}
