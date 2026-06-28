package com.pixplaze.api.web.service.auth;

import com.pixplaze.api.ext.data.Authority;
import com.pixplaze.api.web.configuration.properties.AccessTokenProperties;
import com.pixplaze.api.web.data.user.ClientPrincipal;
import com.pixplaze.api.web.data.user.MinecraftPlayerPrincipal;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ES256 access-токены Minecraft-игрока (identity = {@code mc.uuid}/{@code mc.host}; плюс {@code pid}
 * при связке с профилем). Токен адресован хосту сервера ({@code aud=host} из targets, проставляется
 * стратегией при выдаче) — его верифицирует сам MC-сервер по публичному ключу.
 */
@Service
public class MinecraftPlayerAccessTokenService extends AbstractEsClientTokenService<MinecraftPlayerPrincipal> {

    public MinecraftPlayerAccessTokenService(
            AccessTokenKeys keys,
            AccessTokenProperties properties
    ) {
        super(keys, Duration.ofMinutes(properties.expirationMinutes()));
    }

    @Override
    protected void writeIdentityClaims(MinecraftPlayerPrincipal identity, Map<String, Object> claims) {
        final var mc = new HashMap<String, Object>();
        mc.put("uuid", identity.getUuid().toString());
        if (identity.getHost() != null) {
            mc.put("host", identity.getHost());
        }
        claims.put(Authority.Claims.MINECRAFT_CONTEXT, mc);
        if (identity.getProfileId() != null) {
            claims.put(AccessTokenClaims.PID, identity.getProfileId());
        }
    }

    static ClientPrincipal toPrincipal(Claims claims, Authority authority) {
        final var mc = AccessTokenClaims.minecraftContext(claims);
        final var player = new MinecraftPlayerPrincipal();
        player.setUuid(UUID.fromString((String) mc.get("uuid")));
        player.setHost((String) mc.get("host"));
        player.setUsername(claims.getSubject());
        player.setProfileId(claims.get(AccessTokenClaims.PID, Long.class));
        player.setAuthority(authority);
        return player;
    }
}
