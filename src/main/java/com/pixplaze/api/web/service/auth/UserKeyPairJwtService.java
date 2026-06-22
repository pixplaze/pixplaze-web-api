package com.pixplaze.api.web.service.auth;

import com.pixplaze.api.ext.data.Authority;
import com.pixplaze.api.web.data.user.Role;
import com.pixplaze.api.web.data.user.ClientPrincipial;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/**
 * Аналог {@link UserJwtService}, но на асимметричной паре ключей (RSA / RS256).
 * Подпись выполняется приватным ключом, проверка — публичным, поэтому публичный
 * ключ можно безопасно отдавать другим сервисам для самостоятельной валидации
 * токена (см. {@link #getPublicKey()} / {@link #getPublicKeyPem()}).
 */
public class UserKeyPairJwtService<E extends ClientPrincipial> implements JwtService<E> {

    private static final String KEY_ALGORITHM = "RSA";

    public static class UserClaims {
        public static final String ID = "pid";
        public static final String EMAIL = "email";
        public static final String ROLE = "rid";
    }

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final Duration expirationTerm;

    /**
     * @param privateKeyBase64 приватный ключ в формате PKCS#8 (DER), закодированный Base64
     * @param publicKeyBase64  публичный ключ в формате X.509/SubjectPublicKeyInfo (DER), закодированный Base64
     */
    public UserKeyPairJwtService(String privateKeyBase64, String publicKeyBase64, Duration expirationTerm) {
        this.privateKey = parsePrivateKey(privateKeyBase64);
        this.publicKey = parsePublicKey(publicKeyBase64);
        this.expirationTerm = expirationTerm;
    }

    /** Публичный ключ для проверки подписи токена другими сервисами. */
    public PublicKey getPublicKey() {
        return publicKey;
    }

    /** Публичный ключ в PEM (SubjectPublicKeyInfo) — удобно отдавать по HTTP/в конфиг другим сервисам. */
    public String getPublicKeyPem() {
        var base64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        var pem = new StringBuilder("-----BEGIN PUBLIC KEY-----\n");
        for (var i = 0; i < base64.length(); i += 64) {
            pem.append(base64, i, Math.min(i + 64, base64.length())).append('\n');
        }
        return pem.append("-----END PUBLIC KEY-----\n").toString();
    }

    @Override
    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public ClientPrincipial readClaims(String token) {
        return readClaims(extractClaims(token));
    }

    public ClientPrincipial readClaims(Claims claims) {
        return new ClientPrincipial(
                claims.get(UserClaims.ID, Long.class),
                claims.getSubject(),
                claims.get(UserClaims.EMAIL, String.class),
                null,
                Authority.as(Authority.Role.valueOf((String) claims.get(Authority.Claims.ROLE))).grant()
        );
    }

    @Override
    public String generate(E details) {
        return Jwts.builder()
                .id(buildId())
                .subject(details.getUsername())
                .claims(buildClaims(details))
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(expirationTerm)))
                .signWith(privateKey)
                .compact();
    }

    public Map<String, Object> buildClaims(E details) {
        return Map.of();
    }

    public String buildId() {
        return null;
    }

    private static PrivateKey parsePrivateKey(String privateKeyBase64) {
        try {
            var keySpec = new PKCS8EncodedKeySpec(Decoders.BASE64.decode(privateKeyBase64));
            return KeyFactory.getInstance(KEY_ALGORITHM).generatePrivate(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalArgumentException("Failed to parse JWT private key", e);
        }
    }

    private static PublicKey parsePublicKey(String publicKeyBase64) {
        try {
            var keySpec = new X509EncodedKeySpec(Decoders.BASE64.decode(publicKeyBase64));
            return KeyFactory.getInstance(KEY_ALGORITHM).generatePublic(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalArgumentException("Failed to parse JWT public key", e);
        }
    }
}
