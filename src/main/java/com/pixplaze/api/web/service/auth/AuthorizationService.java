package com.pixplaze.api.web.service.auth;

import com.pixplaze.api.ext.data.auth.Authority;
import com.pixplaze.api.ext.data.auth.AuthorizationToken;
import com.pixplaze.api.ext.data.auth.AuthorizationTokenInfo;
import com.pixplaze.api.ext.data.auth.VerifiableAuthorizationTokenInfo;
import com.pixplaze.api.web.data.auth.RefreshTokenSubjectType;
import com.pixplaze.api.ext.data.auth.MinecraftServerTargets;
import com.pixplaze.api.web.data.db.tables.pojos.Profile;
import com.pixplaze.api.web.data.db.tables.pojos.VoucherCode;
import com.pixplaze.api.web.data.dto.SignInRequest;
import com.pixplaze.api.web.data.dto.SignUpRequest;
import com.pixplaze.api.web.data.user.MinecraftPlayerPrincipal;
import com.pixplaze.api.web.data.user.MinecraftServerPrincipal;
import com.pixplaze.api.web.data.user.ApplicationClientPrincipal;
import com.pixplaze.api.web.data.voucher.VoucherCodeType;
import com.pixplaze.api.web.exception.auth.InvalidRefreshTokenException;
import com.pixplaze.api.web.exception.http.ConflictException;
import com.pixplaze.api.web.service.MinecraftPlayerService;
import com.pixplaze.api.web.service.MinecraftServerService;
import com.pixplaze.api.web.service.ProfileService;
import com.pixplaze.api.web.service.VoucherCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


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

    @Value("${app.url.api.gateway}")
    private String apiGateway;

    /**
     * Регистрация пользователя
     *
     * @param request данные пользователя
     * @return токен
     */
    @Transactional
    public AuthorizationTokenInfo signUp(SignUpRequest request) {

        VoucherCode voucherCode = voucherCodeService.load(request.inviteCode(), VoucherCodeType.INVITE);
        final var profile = new Profile()
                .setName(request.username())
                .setEmail(request.email())
                .setPassword(passwordEncoder.encode(request.password()));
        final var clientPrincipial = profileService.toApplicationClientPrincipal(profile);

        if (profileService.existsByUsername(request.username())) {
            throw new ConflictException("Username '%s' is already taken!".formatted(request.username()));
        }
        if (profileService.existsByEmail(request.email())) {
            throw new ConflictException("Email '%s' is already taken!".formatted(request.email()));
        }

        try {
            var id = profileService.create(profile).getId();
            clientPrincipial.setId(id);
        } catch (DuplicateKeyException e) {
            // Гонка двух регистраций с одним именем или email: проверка выше её не закрывает.
            throw new ConflictException("Username or email is already taken!");
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

    /// Ротация refresh-токена и перевыпуск access. Права всех субъектов заново выводятся из БД по
    /// {@code subject_type}, ничего не копируется из старого токена: снятые роли, отвязка игрока и бан
    /// сервера срабатывают на ближайшем refresh. Тип субъекта — ещё и верхняя граница scope: токен
    /// игрока всегда {@code MINECRAFT_PLAYER}, операторские права живут в игре и в токене доступа к плагину.
    ///
    /// Серверу вместе с парой снова отдаётся публичный ключ подписи ({@link VerifiableAuthorizationTokenInfo}) —
    /// так до плагина доходит и новый ключ после ротации.
    public AuthorizationToken refresh(String token) {
        final var rotation = refreshTokenService.rotate(token);

        final var accessToken = switch (rotation.subjectType()) {
            case PROFILE -> {
                // toApplicationClientPrincipal → applyAuthority: системные роли профиля, aud — шлюз.
                final var profile = profileService.toApplicationClientPrincipal(profileService.getById(rotation.profileId()));
                yield profileAccessTokenService.issue(profile);
            }
            case MINECRAFT_SERVER -> {
                final var server = minecraftServerService.findById(rotation.minecraftServerId())
                        .orElseThrow(() -> new InvalidRefreshTokenException("server_not_found"));
                final var serverPrincipal = new MinecraftServerPrincipal();
                serverPrincipal.setMinecraftServerId(server.getId());
                serverPrincipal.setName(server.getName());
                serverPrincipal.setAuthority(Authority.as(Authority.Role.MINECRAFT_SERVER)
                        .from(Authority.Source.MINECRAFT_AUTHORIZED_DEVICE)
                        .to(apiGateway, MinecraftServerTargets.of(server.getId()))
                        .grant());
                yield minecraftServerAccessTokenService.issue(serverPrincipal);
            }
            case MINECRAFT_PLAYER -> {
                final var serverId = rotation.minecraftServerId();
                final var playerUuid = rotation.playerUuid();
                final var player = minecraftPlayerService.findByUuid(playerUuid)
                        .orElseThrow(() -> new InvalidRefreshTokenException("player_not_found"));
                // Игрок отвязан от профиля или ушёл с сервера — токен больше не выдаётся.
                if (serverId == null) {
                    throw new InvalidRefreshTokenException("player_without_server");
                }
                if (!minecraftPlayerService.isProfileOwnsPlayer(rotation.profileId(), playerUuid)) {
                    throw new InvalidRefreshTokenException("player_unlinked");
                }
                if (!minecraftServerService.isPlayerServerMember(playerUuid, serverId)) {
                    throw new InvalidRefreshTokenException("player_not_on_server");
                }

                final var playerPrincipal = new MinecraftPlayerPrincipal();
                playerPrincipal.setUuid(player.getUuid());
                playerPrincipal.setUsername(player.getUsername());
                playerPrincipal.setProfileId(rotation.profileId());
                playerPrincipal.setMinecraftServerId(serverId);
                // aud — шлюз: токен игрока нужен плагину для запросов в web-api от имени игрока.
                playerPrincipal.setAuthority(Authority.as(Authority.Role.MINECRAFT_PLAYER)
                        .from(Authority.Source.MINECRAFT_AUTHORIZED_DEVICE)
                        .to(apiGateway)
                        .grant());
                yield minecraftPlayerAccessTokenService.issue(playerPrincipal);
            }
        };
        return rotation.subjectType() == RefreshTokenSubjectType.MINECRAFT_SERVER
                ? new VerifiableAuthorizationTokenInfo(accessToken, rotation.refreshToken(), minecraftServerAccessTokenService.getPublicKeyBase64())
                : new AuthorizationTokenInfo(accessToken, rotation.refreshToken());
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
}
