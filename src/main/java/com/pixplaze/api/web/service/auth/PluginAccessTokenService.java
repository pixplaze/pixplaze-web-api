package com.pixplaze.api.web.service.auth;

import com.pixplaze.api.ext.data.auth.Authority;
import com.pixplaze.api.web.configuration.properties.AccessTokenProperties;
import com.pixplaze.api.web.data.user.PluginAccessPrincipal;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * ES256 токены доступа к плагину: {@code sub} — UUID основного игрока профиля на сервере, {@code pid},
 * {@code mc.sid}, игровые роли, {@code src = AAD}, {@code aud = mc:server:{sid}}. Короткие, без refresh:
 * фронт получает новый обменом. Проверяет их только плагин.
 */
@Service
public class PluginAccessTokenService extends AbstractEsClientTokenService<PluginAccessPrincipal> {

    public PluginAccessTokenService(AccessTokenKeys keys, AccessTokenProperties properties) {
        super(keys, Duration.ofMinutes(properties.pluginExpirationMinutes()));
    }

    @Override
    protected String subjectOf(PluginAccessPrincipal identity) {
        return identity.getName();
    }

    @Override
    protected void writeIdentityClaims(PluginAccessPrincipal identity, Map<String, Object> claims) {
        claims.put(AccessTokenClaims.PID, identity.getProfileId());
        final var mc = new HashMap<String, Object>();
        mc.put(Authority.Claims.Minecraft.SERVER_ID, identity.getMinecraftServerId());
        if (identity.getPlayerName() != null) {
            mc.put(Authority.Claims.Minecraft.PLAYER_NAME, identity.getPlayerName());
        }
        claims.put(Authority.Claims.MINECRAFT_CONTEXT, mc);
    }
}
