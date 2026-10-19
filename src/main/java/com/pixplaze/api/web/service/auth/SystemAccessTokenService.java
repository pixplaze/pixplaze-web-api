package com.pixplaze.api.web.service.auth;

import com.pixplaze.api.ext.data.auth.Authority;
import com.pixplaze.api.ext.data.auth.MinecraftServerTargets;
import com.pixplaze.api.web.configuration.properties.AccessTokenProperties;
import com.pixplaze.api.web.data.user.SystemPrincipal;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * ES256 системные токены web-api для REST плагина: {@code rls = RSYS}, {@code src = SAD},
 * {@code aud = mc:server:{sid}}, {@code mc.sid}. Выпускаются перед каждым запросом, без кеша,
 * живут минуту. Плагин принимает их на всех REST-маршрутах; web-api от клиентов — нет.
 */
@Service
public class SystemAccessTokenService extends AbstractEsClientTokenService<SystemPrincipal> {

    public SystemAccessTokenService(AccessTokenKeys keys, AccessTokenProperties properties) {
        super(keys, Duration.ofMinutes(properties.systemExpirationMinutes()));
    }

    /// Новый системный токен для запроса к плагину сервера {@code minecraftServerId}.
    public String issueFor(Long minecraftServerId) {
        final var principal = new SystemPrincipal();
        principal.setMinecraftServerId(Objects.requireNonNull(minecraftServerId, "Server 'id' must not be null!"));
        principal.setAuthority(Authority.as(Authority.Role.SYSTEM)
                .from(Authority.Source.SYSTEM_AUTHORIZED_DEVICE)
                .to(MinecraftServerTargets.of(minecraftServerId))
                .grant());
        return issue(principal);
    }

    @Override
    protected void writeIdentityClaims(SystemPrincipal identity, Map<String, Object> claims) {
        final var mc = new HashMap<String, Object>();
        mc.put(Authority.Claims.Minecraft.SERVER_ID, identity.getMinecraftServerId());
        claims.put(Authority.Claims.MINECRAFT_CONTEXT, mc);
    }
}
