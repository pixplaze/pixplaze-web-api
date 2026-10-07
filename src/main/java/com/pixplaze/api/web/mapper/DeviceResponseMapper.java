package com.pixplaze.api.web.mapper;

import com.pixplaze.api.ext.data.auth.AuthorizationToken;
import com.pixplaze.api.ext.data.auth.VerifiableAuthorizationTokenInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Сериализация ответа token-эндпоинта в формат RFC 6749 §5.1: snake_case-ключи
 * {@code access_token}/{@code token_type}/{@code expires_in}/{@code refresh_token}
 * плюс расширение {@code public_key} для device-flow сервера. Сами record'ы токенов
 * (camelCase — их же отдаёт вход в приложение) не трогаем — формат собираем здесь, на слое представления.
 * Необязательные поля ({@code refresh_token}, {@code public_key}) опускаются, если null.
 */
@Component
@RequiredArgsConstructor
public class DeviceResponseMapper {

    private static final String TOKEN_TYPE_BEARER = "Bearer";

    private final JsonMapper jsonMapper;
    private final Clock clock = Clock.systemUTC();

    public Map<String, Object> toTokenResponse(AuthorizationToken token) {
        final var response = new LinkedHashMap<String, Object>();
        response.put("access_token", token.accessToken());
        response.put("token_type", TOKEN_TYPE_BEARER);
        response.put("expires_in", expiresIn(token.accessToken()));
        putIfPresent(response, "refresh_token", token.refreshToken());
        if (token instanceof VerifiableAuthorizationTokenInfo verifiable) {
            putIfPresent(response, "public_key", verifiable.publicKey());
        }
        return response;
    }

    /// Секунды до {@code exp} access-токена. Токен только что подписан нами же, поэтому payload читаем
    /// без проверки подписи: так срок верен для любого субъекта, и типам токенов не нужно его нести.
    private long expiresIn(String accessToken) {
        final var payload = accessToken.split("\\.")[1];
        final var claims = jsonMapper.readTree(new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8));
        return Math.max(0, claims.get("exp").asLong() - clock.instant().getEpochSecond());
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
