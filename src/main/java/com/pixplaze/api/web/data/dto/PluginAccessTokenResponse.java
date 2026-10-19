package com.pixplaze.api.web.data.dto;

/**
 * Ответ обмена на токен доступа к плагину ({@code POST /servers/{id}/token}).
 *
 * @param accessToken токен доступа к плагину: {@code Authorization: Bearer} для REST и кадр
 *                    {@code AUTH} для WebSocket плагина
 * @param expiresIn   срок жизни токена, секунды
 */
public record PluginAccessTokenResponse(String accessToken, long expiresIn) {
}
