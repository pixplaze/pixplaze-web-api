package com.pixplaze.api.web.data.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Заявка на регистрацию Minecraft-сервера")
public record MinecraftServerBidRequest(
        @Schema(description = "Название сервера", example = "Pixplaze SMP")
        @Size(min = 1, max = 128, message = "Название сервера должно содержать от 1 до 128 символов")
        @NotBlank(message = "Название сервера не может быть пустым")
        String name,

        @Schema(description = "Игровой адрес сервера", example = "mc.pixplaze.net")
        @Size(min = 1, max = 253, message = "Хост должен содержать от 1 до 253 символов")
        @NotBlank(message = "Хост не может быть пустым")
        String host,

        @Schema(description = "Игровой порт сервера", example = "25565")
        @NotNull(message = "Порт должен быть указан")
        @Min(value = 1, message = "Порт должен быть от 1 до 65535")
        @Max(value = 65535, message = "Порт должен быть от 1 до 65535")
        Integer port
) {
}
