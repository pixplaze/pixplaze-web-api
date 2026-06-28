package com.pixplaze.api.web.service.auth;

import com.pixplaze.api.web.configuration.properties.AccessTokenProperties;
import com.pixplaze.api.web.repository.JwksRepository;
import com.pixplaze.api.web.util.EcKeys;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Locator;
import io.jsonwebtoken.UnsupportedJwtException;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.Map;

/**
 * Единственный держатель ES256-ключа access-токенов: парсит приватный ключ подписи и
 * регистрирует публичный в {@link JwksRepository} РОВНО ОДИН раз (на старте). Инжектится во
 * все token-сервисы, чтобы подпись/верификация шли общим ключом без повторного парсинга и
 * без многократной регистрации JWKS.
 */
@Component
public class AccessTokenKeys {

    private static final String DEFAULT_KID = "k1";

    private final String kid;
    private final PrivateKey signingKey;
    private final JwksRepository jwksRepository;

    public AccessTokenKeys(AccessTokenProperties properties, JwksRepository jwksRepository) {
        this.kid = properties.kid() != null ? properties.kid() : DEFAULT_KID;
        this.signingKey = EcKeys.parsePrivateKey(properties.privateKey());
        this.jwksRepository = jwksRepository;
        jwksRepository.put(kid, EcKeys.parsePublicKey(properties.publicKey()));
    }

    public Key signingKey() {
        return signingKey;
    }

    public String kid() {
        return kid;
    }

    public Locator<Key> verificationKeyLocator() {
        return header -> {
            final var headerKid = (String) header.get("kid");
            return jwksRepository.findByKid(headerKid).orElseThrow(
                    () -> new UnsupportedJwtException("Unknown or missing key id (kid): " + headerKid));
        };
    }

    /// Парсинг+верификация токена общим ключом — для {@code ClientPrincipalReader}, у которого нет issuer-сервисов.
    public Claims parse(String token) {
        return Jwts.parser()
                .keyLocator(verificationKeyLocator())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /// Активный публичный ключ как Base64 от DER (X.509) — отдаётся только серверным token-сервисом.
    public String publicKeyBase64() {
        return Base64.getEncoder().encodeToString(jwksRepository.findByKid(kid).orElseThrow().getEncoded());
    }

    /// Публичные ключи в формате JWKS для эндпоинта раздачи — только серверный token-сервис.
    public Map<String, Object> jwkSet() {
        return jwksRepository.asJwkSet();
    }
}
