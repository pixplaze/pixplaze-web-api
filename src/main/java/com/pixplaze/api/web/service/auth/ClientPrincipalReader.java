package com.pixplaze.api.web.service.auth;

import com.pixplaze.api.ext.data.auth.Authority;
import com.pixplaze.api.ext.data.auth.AuthorityClaims;
import com.pixplaze.api.web.data.user.ClientPrincipal;
import io.jsonwebtoken.UnsupportedJwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Единственная точка чтения произвольного access-токена в {@link ClientPrincipal} (для JWT-фильтра).
 * Верифицирует подпись общим {@link AccessTokenKeys}, затем по ролям выбирает, чей это субъект, и
 * делегирует сборку принципала static-фабрике соответствующего token-сервиса. Сами сервисы он не
 * держит — только ключи.
 */
@Service
@RequiredArgsConstructor
public class ClientPrincipalReader {

    private final AccessTokenKeys keys;

    @Value("${app.url.api.gateway}")
    private String apiGateway;

    public ClientPrincipal read(String token) {
        final var claims = keys.parse(token);
        final var authority = AuthorityClaims.read(claims);

        // Ключ подписи общий для всех токенов, поэтому адресата проверяем явно: web-api принимает только
        // токены со шлюзом в aud. Токен доступа к плагину и системный токен web-api для плагина адресованы
        // серверу (mc:server:{id}) и отсекаются здесь же. Отказ — 401 в JWT-фильтре.
        if (!authority.to(apiGateway)) {
            throw new UnsupportedJwtException("Token is not addressed to this API");
        }

        // Дискриминатор — источник: AAD — профиль, MAD — сам Minecraft-клиент (игрок или сервер).
        if (authority.from(Authority.Source.MINECRAFT_AUTHORIZED_DEVICE)) {
            if (authority.is(Authority.Role.MINECRAFT_SERVER)) {
                return MinecraftServerAccessTokenService.toPrincipal(claims, authority);
            }
            return MinecraftPlayerAccessTokenService.toPrincipal(claims, authority);
        }
        return ProfileAccessTokenService.toPrincipal(claims, authority);
    }
}
