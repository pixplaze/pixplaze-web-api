package com.pixplaze.api.web.service.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Locator;

import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Общая для всех способов подписи логика выпуска и валидации JWT.
 *
 * <p>Сам алгоритм генерации и проверки токена един и не зависит ни от типа
 * криптографии, ни от типа объекта, из которого выпускается токен. Конкретные
 * реализации задают только:
 * <ul>
 *     <li>{@link #signingKey()} — ключ подписи (секретный HMAC либо приватный EC);</li>
 *     <li>{@link #signingKid()} — идентификатор ключа подписи (для асимметрии
 *         попадает в заголовок {@code kid}; для HMAC — {@code null});</li>
 *     <li>{@link #verificationKeyLocator()} — как по заголовку токена (в т.ч. {@code kid})
 *         найти ключ проверки подписи;</li>
 *     <li>{@link #subjectOf(Object)} / {@link #buildClaims(Object)} — как из
 *         произвольного объекта-идентичности {@code <E>} получить subject и claims.</li>
 * </ul>
 *
 * <p>Реализация одна — асимметричная ES256 ({@link AbstractEsClientTokenService}): токен
 * проверяется и здесь, и в сторонних сервисах по публичному ключу, с ротацией по
 * {@code kid}. Абстракция оставлена обобщённой по способу подписи и типу {@code <E>}.
 *
 * @param <E> тип объекта, из которого выпускается токен
 */
public abstract class AbstractTokenService<E> implements TokenService<E> {

    private final Duration expirationTerm;

    protected AbstractTokenService(Duration expirationTerm) {
        this.expirationTerm = expirationTerm;
    }

    /** Дефолтный срок жизни токена в секундах — для claim {@code expires_in} (RFC 6749 §5.1). */
    public long getExpiresInSeconds() {
        return expirationTerm.toSeconds();
    }

    /** Ключ, которым подписывается токен: секретный (HMAC) либо приватный (EC). */
    protected abstract Key signingKey();

    /** Идентификатор ключа подписи: для асимметрии — {@code kid} в заголовок, для HMAC — {@code null}. */
    protected String signingKid() {
        return null;
    }

    /** Аудитория токена (стандартный claim {@code aud}) для конкретного объекта; по умолчанию пусто. */
    protected Collection<String> audience(E identity) {
        return List.of();
    }

    /** Локатор ключа проверки подписи по заголовку JWS (по {@code kid} для асимметрии). */
    protected abstract Locator<Key> verificationKeyLocator();

    /** subject (тема) токена для конкретного объекта-идентичности. */
    protected abstract String subjectOf(E identity);

    /** Дополнительные claims для конкретного объекта-идентичности; по умолчанию пусто. */
    public Map<String, Object> buildClaims(E identity) {
        return Map.of();
    }

    /** Идентификатор токена (jti); по умолчанию не задаётся. */
    public String buildId() {
        return null;
    }

    @Override
    public String issue(E identity) {
        return issue(identity, null);
    }

    @Override
    public String issue(E identity, Duration ttl) {
        final var effectiveTtl = ttl != null ? ttl : expirationTerm;
        final var now = Instant.now();
        final var builder = Jwts.builder();

        final var kid = signingKid();
        if (kid != null) {
            builder.header().keyId(kid).and();
        }

        final var audience = audience(identity);
        if (audience != null && !audience.isEmpty()) {
            builder.audience().add(audience).and();
        }

        return builder
                .id(buildId())
                .subject(subjectOf(identity))
                .claims(buildClaims(identity))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(effectiveTtl)))
                .signWith(signingKey())
                .compact();
    }

    @Override
    public Claims extractClaims(String token) {
        return Jwts.parser()
                .keyLocator(verificationKeyLocator())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
