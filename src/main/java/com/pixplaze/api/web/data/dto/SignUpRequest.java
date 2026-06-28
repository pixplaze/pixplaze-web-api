package com.pixplaze.api.web.data.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Запрос на регистрацию")
public record SignUpRequest(
        @Schema(description = "Имя пользователя", example = "Ivan")
        @Size(min = 5, max = 50, message = "Имя пользователя должно содержать от 5 до 50 символов")
        @NotBlank(message = "Имя пользователя не может быть пустыми")
        String username,
        @Schema(description = "Адрес электронной почты", example = "ivanov@email.com")
        @Size(min = 5, max = 255, message = "Адрес электронной почты должен содержать от 5 до 255 символов")
        @NotBlank(message = "Адрес электронной почты не может быть пустыми")
        @Email(message = "Email адрес должен быть в формате user@example.com")
        String email,
        @Schema(description = "Пароль", example = "My$password123")
        @Size(max = 255, message = "Длина пароля должна быть не более 255 символов")
        String password,
        @Schema(description = "Код приглашения. Регистрация пользователей ограничена.", example = "PH4F-PRD0")
        String inviteCode
) {}