package com.pixplaze.api.web.mapper;

import com.pixplaze.api.ext.data.auth.AuthorizationTokenInfo;
import com.pixplaze.api.ext.data.auth.DeviceResponseInfo;
import com.pixplaze.api.ext.data.auth.VerifiableAuthorizationTokenInfo;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Сериализация ответа token-эндпоинта в формат RFC 6749 §5.1: snake_case-ключи
 * {@code access_token}/{@code token_type}/{@code expires_in}/{@code refresh_token}
 * плюс расширение {@code public_key} для device-flow сервера. Сами record'ы токенов
 * (camelCase) не трогаем — нужный формат собираем здесь, на слое представления.
 * Необязательные поля ({@code refresh_token}, {@code public_key}) опускаются, если null.
 */
@Component
public class DeviceResponseMapper {

    // MapStruct не умеет писать произвольные ключи Map через @Mapping
    // (он ищет write accessor у целевого типа), поэтому ключи RFC 8628
    // раскладываем сами в default-методе. HashMap допускает null в
    // необязательном verification_uri_complete.
    public HashMap<String, Object> toDeviceResponse(DeviceResponseInfo deviceResponseInfo) {
        var map = new HashMap<String, Object>();
        map.put("device_code", deviceResponseInfo.deviceCode());
        map.put("user_code", deviceResponseInfo.userCode());
        map.put("expires_in", deviceResponseInfo.expiresIn());
        map.put("interval", deviceResponseInfo.interval());
        map.put("verification_uri", deviceResponseInfo.verificationUri());
        map.put("verification_uri_complete", deviceResponseInfo.verificationUriComplete());
        return map;
    }

    private static final String TOKEN_TYPE_BEARER = "Bearer";

    /**
     * @param tokenInfo результат гранта — {@link AuthorizationTokenInfo} (refresh_token grant
     *                  и player-стратегия) или {@link VerifiableAuthorizationTokenInfo} (server-стратегия)
     * @param expiresInSeconds время жизни access-токена в секундах
     */
    public Map<String, Object> toTokenResponse(Object tokenInfo, long expiresInSeconds) {
        if (tokenInfo instanceof AuthorizationTokenInfo info) {
            return baseResponse(info.accessToken(), info.refreshToken(), expiresInSeconds);
        }
        if (tokenInfo instanceof VerifiableAuthorizationTokenInfo info) {
            final var response = baseResponse(info.accessToken(), info.refreshToken(), expiresInSeconds);
            putIfPresent(response, "public_key", info.publicKey());
            return response;
        }
        throw new IllegalArgumentException("Unsupported token info type: " + tokenInfo.getClass().getName());
    }

    private Map<String, Object> baseResponse(String accessToken, String refreshToken, long expiresInSeconds) {
        final var response = new LinkedHashMap<String, Object>();
        response.put("access_token", accessToken);
        response.put("token_type", TOKEN_TYPE_BEARER);
        response.put("expires_in", expiresInSeconds);
        putIfPresent(response, "refresh_token", refreshToken);
        return response;
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
