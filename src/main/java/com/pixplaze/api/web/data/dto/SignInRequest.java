package com.pixplaze.api.web.data.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


@Schema(description = "Запрос на аутентификацию")
public record SignInRequest(
        @Schema(description = "Имя пользователя", example = "Jon")
        @Size(min = 3, max = 32, message = "Имя пользователя должно содержать от 5 до 32 символов")
        @NotBlank(message = "Имя пользователя не может быть пустыми")
        String username,
        @Schema(description = "Пароль", example = "my_1secret1_password")
        @Size(max = 255, message = "Длина пароля должна быть от 8 до 255 символов")
        @NotBlank(message = "Пароль не может быть пустыми")
        String password
) {}