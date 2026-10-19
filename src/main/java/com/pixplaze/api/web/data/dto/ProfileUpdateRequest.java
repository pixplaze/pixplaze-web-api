package com.pixplaze.api.web.data.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

@Schema(description = "Изменение профиля: переданные поля меняются, null — без изменений")
public record ProfileUpdateRequest(
        @Schema(description = "Имя пользователя", example = "Ivan")
        @Size(min = 3, max = 32, message = "Имя пользователя должно содержать от 3 до 32 символов")
        String username,
        @Schema(description = "Адрес электронной почты", example = "ivanov@email.com")
        @Size(min = 5, max = 64, message = "Адрес электронной почты должен содержать от 5 до 64 символов")
        @Email(message = "Email адрес должен быть в формате user@example.com")
        String email,
        @Schema(description = "Новый пароль", example = "My$password123")
        @Size(min = 8, max = 255, message = "Длина пароля должна быть от 8 до 255 символов")
        String password,
        @Schema(description = "Текущий пароль; обязателен, если меняется пароль")
        String currentPassword,
        @Schema(description = "Скрывать мой онлайн: друзья не видят, что мои игроки в сети")
        Boolean isOnlineHidden
) {}
