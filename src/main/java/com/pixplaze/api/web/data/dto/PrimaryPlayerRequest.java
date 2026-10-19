package com.pixplaze.api.web.data.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Сделать игрока основным на сервере")
public record PrimaryPlayerRequest(
        @Schema(description = "Id сервера")
        @NotNull
        Long minecraftServerId
) {}
