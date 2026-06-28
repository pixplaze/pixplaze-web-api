package com.pixplaze.api.web.service.auth;

import com.pixplaze.api.ext.data.Authority;
import com.pixplaze.api.web.data.user.ClientPrincipal;
import lombok.RequiredArgsConstructor;
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

    public ClientPrincipal read(String token) {
        final var claims = keys.parse(token);
        final var authority = AccessTokenClaims.readAuthority(claims);

        // Дискриминатор — источник, а не роль: AAD-профиль может нести производные роли
        // MINECRAFT_PLAYER/MINECRAFT_OPERATOR (для веб-взаимодействия с сервером), но остаётся
        // ApplicationClientPrincipal. Субъект MAD — это сам Minecraft-клиент (игрок/сервер).
        if (authority.from(Authority.Source.MINECRAFT_AUTHORIZED_DEVICE)) {
            if (authority.is(Authority.Role.MINECRAFT_SERVER)) {
                return MinecraftServerAccessTokenService.toPrincipal(claims, authority);
            }
            return MinecraftPlayerAccessTokenService.toPrincipal(claims, authority);
        }
        return ProfileAccessTokenService.toPrincipal(claims, authority);
    }
}
