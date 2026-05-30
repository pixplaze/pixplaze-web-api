package com.pixplaze.api.web.data.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Ответ c токеном доступа")
public record JwtAuthenticationResponseInfo(
        @Schema(description = "Токен доступа", example = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJhZG1pbiIsImV4cCI6MTYyMjUwNj...")
        String accessToken,
        @Schema(description = "Токен обновления сессии", example = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJhZG1pbiIsImV4cCI6MTYyMjUwNj...")
        String refreshToken
) {
    public JwtAuthenticationResponseInfo response() {
        return new JwtAuthenticationResponseInfo(accessToken, null);
    }
}
