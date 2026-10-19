package com.pixplaze.api.web.data.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Добавление в друзья по имени профиля")
public record FriendAddRequest(
        @Schema(description = "Имя профиля", example = "Ivan")
        @NotBlank
        String name
) {}
