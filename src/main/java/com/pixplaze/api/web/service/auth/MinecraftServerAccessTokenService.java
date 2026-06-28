package com.pixplaze.api.web.service.auth;

import com.pixplaze.api.ext.data.Authority;
import com.pixplaze.api.web.configuration.properties.AccessTokenProperties;
import com.pixplaze.api.web.data.user.ClientPrincipal;
import com.pixplaze.api.web.data.user.MinecraftServerPrincipal;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

/**
 * ES256 access-токены Minecraft-сервера (identity = {@code mc.sid}/{@code mc.host};
 * {@code aud = [gateway, host]} из targets, проставляется стратегией при выдаче).
 * Единственный сервис, отдающий публичный ключ/JWKS — серверный токен верифицируется снаружи.
 */
@Service
public class MinecraftServerAccessTokenService extends AbstractEsClientTokenService<MinecraftServerPrincipal> {

    public MinecraftServerAccessTokenService(
            AccessTokenKeys keys,
            AccessTokenProperties properties
    ) {
        super(keys, Duration.ofMinutes(properties.expirationMinutes()));
    }

    @Override
    protected void writeIdentityClaims(MinecraftServerPrincipal identity, Map<String, Object> claims) {
        claims.put(Authority.Claims.MINECRAFT_CONTEXT, Map.of("sid", identity.getServerId(), "host", identity.getHost()));
    }

    /** Активный публичный ключ (Base64 DER) — для device-flow выдачи серверу. */
    public String getPublicKeyBase64() {
        return keys().publicKeyBase64();
    }

    /** Публичные ключи в формате JWKS для эндпоинта раздачи. */
    public Map<String, Object> getJwks() {
        return keys().jwkSet();
    }

    /// claims → MinecraftServerPrincipal (вызывается ClientPrincipalReader по ролям).
    static ClientPrincipal toPrincipal(Claims claims, Authority authority) {
        final var mc = AccessTokenClaims.minecraftContext(claims);
        final var server = new MinecraftServerPrincipal();
        server.setServerId(AccessTokenClaims.asLong(mc.get("sid")));
        server.setHost((String) mc.get("host"));
        server.setName(claims.getSubject());
        server.setAuthority(authority);
        return server;
    }
}
