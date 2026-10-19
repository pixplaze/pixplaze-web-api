package com.pixplaze.api.web.data.dto;

/**
 * Ответ браузерного входа и обновления токена (sign-up, sign-in, refresh). Refresh-токен едет
 * в HttpOnly-cookie, а не в теле.
 *
 * @param accessToken access-токен профиля
 * @param permissions подсказка с правами для интерфейса
 */
public record ProfileTokenResponse(String accessToken, PermissionsHint permissions) {
}
