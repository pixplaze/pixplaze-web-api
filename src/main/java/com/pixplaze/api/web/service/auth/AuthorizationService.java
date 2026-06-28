package com.pixplaze.api.web.service.auth;

import com.pixplaze.api.ext.data.Authority;
import com.pixplaze.api.ext.data.auth.AuthorizationTokenInfo;
import com.pixplaze.api.web.data.db.tables.pojos.Profile;
import com.pixplaze.api.web.data.db.tables.pojos.VoucherCode;
import com.pixplaze.api.web.data.dto.SignInRequest;
import com.pixplaze.api.web.data.dto.SignUpRequest;
import com.pixplaze.api.web.data.user.MinecraftPlayerPrincipal;
import com.pixplaze.api.web.data.user.MinecraftServerPrincipal;
import com.pixplaze.api.web.data.user.ApplicationClientPrincipal;
import com.pixplaze.api.web.data.voucher.VoucherCodeType;
import com.pixplaze.api.web.exception.auth.InvalidRefreshTokenException;
import com.pixplaze.api.web.service.MinecraftPlayerService;
import com.pixplaze.api.web.service.MinecraftServerService;
import com.pixplaze.api.web.service.ProfileService;
import com.pixplaze.api.web.service.VoucherCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthorizationService {
    private final ProfileService profileService;
    private final ProfileAccessTokenService profileAccessTokenService;
    private final MinecraftPlayerAccessTokenService minecraftPlayerAccessTokenService;
    private final MinecraftServerAccessTokenService minecraftServerAccessTokenService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final VoucherCodeService voucherCodeService;
    private final MinecraftServerService minecraftServerService;
    private final MinecraftPlayerService minecraftPlayerService;

    /**
     * Регистрация пользователя
     *
     * @param request данные пользователя
     * @return токен
     */
    @Transactional
    public AuthorizationTokenInfo signUp(SignUpRequest request) {

        VoucherCode voucherCode = voucherCodeService.load(request.inviteCode(), VoucherCodeType.INVITE);
        var profile = new Profile()
                .setName(request.username())
                .setEmail(request.email())
                .setPassword(passwordEncoder.encode(request.password()));
        var clientPrincipial = profileService.toApplicationClientPrincipal(profile);

        try {
            var id = profileService.create(profile).getId();
            clientPrincipial.setId(id);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage(), e);
        }

        voucherCodeService.activate(voucherCode, profile);

        var accessToken = profileAccessTokenService.issue(clientPrincipial);
        var refreshToken = refreshTokenService.issue(clientPrincipial);
        return new AuthorizationTokenInfo(accessToken, refreshToken);
    }

    /// Аутентификация пользователя
    ///
    /// @param request данные пользователя
    /// @return токен
    public AuthorizationTokenInfo signIn(SignInRequest request) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        var profile = profileService.getUserByUsername(request.username());
        var clientPrincipial = profileService.toApplicationClientPrincipal(profile);
        var accessToken = profileAccessTokenService.issue(clientPrincipial);
        var refreshToken = refreshTokenService.issue(clientPrincipial);
        return new AuthorizationTokenInfo(accessToken, refreshToken);
    }

    public AuthorizationTokenInfo refresh(String token) {
        final var rotation = refreshTokenService.rotate(token);
        // aud ≡ targets и переживает ротацию: восстанавливаем сохранённые targets из refresh-токена
        // (иначе host игрока, не хранимый на entity, терялся бы). Роли/источник — оттуда же. Identity — из БД.
        final var authorityBuilder = Authority.as(rotation.roles().toArray(new Authority.Role[0]))
                .from(rotation.source());
        final var authority = rotation.targets().isEmpty()
                ? authorityBuilder.unauthorized()
                : authorityBuilder.to(rotation.targets()).grant();

        final var accessToken = switch (rotation.subjectType()) {
            case PROFILE -> {
                final var profile = profileService.toApplicationClientPrincipal(profileService.getById(rotation.profileId()));
                profile.setAuthority(authority);
                yield profileAccessTokenService.issue(profile);
            }
            case MINECRAFT_SERVER -> {
                final var server = minecraftServerService.findById(rotation.serverId())
                        .orElseThrow(() -> new InvalidRefreshTokenException("server_not_found"));
                final var serverPrincipal = new MinecraftServerPrincipal();
                serverPrincipal.setServerId(server.getId());
                serverPrincipal.setName(server.getName());
                serverPrincipal.setHost(server.getHost());
                serverPrincipal.setAuthority(authority);
                yield minecraftServerAccessTokenService.issue(serverPrincipal);
            }
            case MINECRAFT_PLAYER, MINECRAFT_OPERATOR -> {
                final var player = minecraftPlayerService.findByUuid(rotation.playerUuid())
                        .orElseThrow(() -> new InvalidRefreshTokenException("player_not_found"));
                final var playerPrincipal = new MinecraftPlayerPrincipal();
                playerPrincipal.setUuid(player.getUuid());
                playerPrincipal.setUsername(player.getUsername());
                playerPrincipal.setProfileId(rotation.profileId());
                // host (= единственный target игрока) восстанавливаем, чтобы попал и в mc.host claim.
                playerPrincipal.setHost(rotation.targets().isEmpty() ? null : rotation.targets().get(0));
                playerPrincipal.setAuthority(authority);
                yield minecraftPlayerAccessTokenService.issue(playerPrincipal);
            }
        };
        return new AuthorizationTokenInfo(accessToken, rotation.refreshToken());
    }

    /**
     * Выход из профиля: отзыв refresh-токена(ов). При {@code fromAll} гасит всю цепочку токенов
     * профиля (выход со всех устройств), иначе — только предъявленный refresh-токен.
     */
    public void signOut(String refreshToken, ApplicationClientPrincipal principal, boolean fromAll) {
        if (fromAll && principal != null) {
            refreshTokenService.revokeAllForProfile(principal.getId());
        } else if (refreshToken != null) {
            refreshTokenService.revoke(refreshToken);
        }
    }

    public ResponseCookie createRefreshTokenCookie(String refreshToken, String path) {
        return ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true) // Защита от XSS (JS не сможет прочитать куку)
                .secure(true)   // Только через HTTPS
                .path(path)     // Кука будет отправляться только на эндпоинт обновления
                .maxAge(refreshTokenService.getExpirationTerm())
                .build();
    }

    /** Кука, удаляющая refresh-токен в браузере (maxAge=0). */
    public ResponseCookie createClearedRefreshTokenCookie(String path) {
        return ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .path(path)
                .maxAge(0)
                .build();
    }
}
