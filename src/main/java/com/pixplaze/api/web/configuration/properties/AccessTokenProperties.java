package com.pixplaze.api.web.configuration.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Конфигурация access-токенов (ES256). Один активный ключ подписи:
 * приватным подписываем, публичный регистрируется в {@code JwksRepository} и
 * раздаётся через JWKS. Ротация (несколько публичных ключей) — задача
 * хранилища ключей, а не конфига.
 *
 * @param kid        идентификатор ключа (в заголовке токена и в JWKS); если не задан — {@code k1}
 * @param privateKey приватный EC-ключ (PKCS#8 или SEC1, Base64 DER или PEM)
 * @param publicKey  публичный EC-ключ (X.509, Base64 DER или PEM)
 */
@ConfigurationProperties("app.security.auth.token.access")
public record AccessTokenProperties(
        int expirationMinutes,
        String kid,
        String privateKey,
        String publicKey
) {}
