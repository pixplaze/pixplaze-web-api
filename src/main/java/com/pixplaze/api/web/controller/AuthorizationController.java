package com.pixplaze.api.web.controller;

import com.pixplaze.api.web.data.dto.DeviceAuthorizationDecisionRequest;
import com.pixplaze.api.web.data.dto.DeviceAuthorizationInfo;
import com.pixplaze.api.web.data.dto.PermissionsHint;
import com.pixplaze.api.web.data.dto.ProfileTokenResponse;
import com.pixplaze.api.web.data.dto.SignInRequest;
import com.pixplaze.api.web.data.dto.SignUpRequest;
import com.pixplaze.api.web.data.user.ApplicationClientPrincipal;
import com.pixplaze.api.ext.data.oauth.DeviceAuthorizationResponse;
import com.pixplaze.api.ext.data.oauth.OAuthError;
import com.pixplaze.api.web.exception.auth.DeviceAuthorizationException;
import com.pixplaze.api.web.exception.auth.InvalidRefreshTokenException;
import com.pixplaze.api.web.mapper.DeviceResponseMapper;
import com.pixplaze.api.web.service.auth.MinecraftServerAccessTokenService;
import com.pixplaze.api.web.service.ServerAccessService;
import com.pixplaze.api.web.service.auth.AuthorizationService;
import com.pixplaze.api.web.service.auth.ClientPrincipalReader;
import com.pixplaze.api.web.service.auth.PermissionCatalog;
import com.pixplaze.api.web.service.auth.device.DeviceAuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import com.pixplaze.api.web.util.AddressUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Аутентификация")
@RequestMapping("/auth")
public class AuthorizationController {
    private final AuthorizationService authorizationService;
    private final DeviceAuthorizationService deviceAuthorizationService;
    private final DeviceResponseMapper deviceResponseMapper;
    private final MinecraftServerAccessTokenService minecraftServerAccessTokenService;
    private final RefreshTokenCookies refreshTokenCookies;
    private final PermissionCatalog permissionCatalog;
    private final ClientPrincipalReader clientPrincipalReader;
    private final ServerAccessService serverAccessService;

    @Operation(summary = "Регистрация пользователя")
    @PostMapping("/sign-up")
    public ResponseEntity<ProfileTokenResponse> signUp(@RequestBody @Valid SignUpRequest requestInfo) {
        final var responseInfo = authorizationService.signUp(requestInfo);
        final var responseCookie = refreshTokenCookies.issue(responseInfo.refreshToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, responseCookie.toString())
                .body(profileTokenResponse(responseInfo.accessToken()));
    }

    @Operation(summary = "Авторизация пользователя")
    @PostMapping("/sign-in")
    public ResponseEntity<ProfileTokenResponse> signIn(@RequestBody @Valid SignInRequest requestInfo) {
        final var responseInfo = authorizationService.signIn(requestInfo);
        final var responseCookie = refreshTokenCookies.issue(responseInfo.refreshToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, responseCookie.toString())
                .body(profileTokenResponse(responseInfo.accessToken()));
    }

    @Operation(summary = "Выйти из профиля")
    @PostMapping("/sign-out")
    public ResponseEntity<Void> signOut(
            @AuthenticationPrincipal ApplicationClientPrincipal principal,
            @CookieValue(name = RefreshTokenCookies.NAME, required = false) String refreshToken,
            @RequestParam(defaultValue = "false") Boolean fromAll
    ) {
        authorizationService.signOut(refreshToken, principal, fromAll);
        final var clearedCookie = refreshTokenCookies.clear();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearedCookie.toString())
                .build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<ProfileTokenResponse> refresh(
            @CookieValue(name = RefreshTokenCookies.NAME) String refreshToken
    ) {
        final var tokens = authorizationService.refresh(refreshToken);
        final var responseCookie = refreshTokenCookies.issue(tokens.refreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, responseCookie.toString())
                .body(profileTokenResponse(tokens.accessToken()));
    }

    /// Access-токен профиля и подсказка с правами для интерфейса. Права считаются по только что
    /// выпущенному токену: системные — из его ролей, игровые — из БД. Cookie-рефреш бывает только
    /// у профиля; для другого субъекта подсказка пустая.
    private ProfileTokenResponse profileTokenResponse(String accessToken) {
        final var permissions = clientPrincipalReader.read(accessToken) instanceof ApplicationClientPrincipal profile
                ? serverAccessService.permissionsHint(profile)
                : PermissionsHint.EMPTY;
        return new ProfileTokenResponse(accessToken, permissions);
    }

    @PostMapping(
            value = "/oauth/authorize",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<DeviceAuthorizationResponse> authorize(
            // Обязательность проверяет сервис: отсутствие — invalid_request в OAuth-формате, а не ErrorResponse Spring.
            @RequestParam(value = "client_id", required = false) String clientId,
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "authorization_details", required = false) String authorizationDetails,
            HttpServletRequest request
    ) {
        // Ошибки device-authorize (DeviceAuthorizationException) → OAuth-формат в ApiExceptionHandler.
        return ResponseEntity.ok(deviceAuthorizationService.authorize(clientId, scope, authorizationDetails, AddressUtils.clientIp(request)));
    }

    /**
     * Единый OAuth2 token endpoint. По {@code grant_type} обслуживает оба не-браузерных
     * потока: обмен device_code на токены (RFC 8628 §3.4) и обновление сервисного
     * refresh-токена (RFC 6749 §6). Браузерный cookie-рефреш живёт отдельно — {@code /auth/refresh}.
     */
    @Operation(summary = "Token endpoint (RFC 6749 §6 / RFC 8628 §3.4): device_code и refresh_token grant")
    @PostMapping(
            value = "/oauth/token",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, Object>> token(
            @RequestParam(value = "grant_type", required = false) String grantType,
            @RequestParam(value = "refresh_token", required = false) String refreshToken,
            @RequestParam(value = "client_id", required = false) String clientId,
            @RequestParam(value = "device_code", required = false) String deviceCode
    ) {
        if (grantType == null || grantType.isBlank()) {
            throw new DeviceAuthorizationException(OAuthError.INVALID_REQUEST, "grant_type is missing");
        }

        return switch (grantType) {
            case "refresh_token" -> refreshTokenGrant(refreshToken);
            case "urn:ietf:params:oauth:grant-type:device_code" -> deviceCodeGrant(clientId, deviceCode);
            default -> throw new DeviceAuthorizationException(OAuthError.UNSUPPORTED_GRANT_TYPE, "grant_type '%s' is not supported".formatted(grantType));
        };
    }

    /// RFC 8628 §3.4: опрос device_code. Вход профиля через device-flow — это браузер: refresh-токен профиля
    /// кладём и в куку, как при входе по паролю, иначе {@code /auth/refresh} без куки отвечает 400 и
    /// сессия кончается вместе с access-токеном. Сервер и игрок обновляются через тело ответа.
    private ResponseEntity<Map<String, Object>> deviceCodeGrant(String clientId, String deviceCode) {
        final var tokens = deviceAuthorizationService.poll(clientId, deviceCode);
        final var response = ResponseEntity.ok();
        if (tokens.refreshToken() != null
                && clientPrincipalReader.read(tokens.accessToken()) instanceof ApplicationClientPrincipal) {
            response.header(HttpHeaders.SET_COOKIE, refreshTokenCookies.issue(tokens.refreshToken()).toString());
        }
        return response.body(deviceResponseMapper.toTokenResponse(tokens));
    }

    /** RFC 6749 §6: обмен сервисного refresh-токена на свежий access (с ротацией refresh). */
    private ResponseEntity<Map<String, Object>> refreshTokenGrant(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new DeviceAuthorizationException(OAuthError.INVALID_REQUEST, "refresh_token is missing");
        }

        try {
            return ResponseEntity.ok(deviceResponseMapper.toTokenResponse(authorizationService.refresh(refreshToken)));
        } catch (InvalidRefreshTokenException e) {
            // Причина (unknown, expired, reuse_detected, server_banned, player_unlinked, …) — в исходном исключении
            throw new DeviceAuthorizationException(OAuthError.INVALID_GRANT, "refresh token rejected", e);
        }
    }

    @Operation(summary = "Информация о подтверждаемой device-авторизации (для подтверждающего устройства)")
    @GetMapping("/oauth/grant")
    public ResponseEntity<DeviceAuthorizationInfo> grantInfo(
            @AuthenticationPrincipal ApplicationClientPrincipal approver,
            @RequestParam("user_code") String userCode
    ) {
        return ResponseEntity.ok(deviceAuthorizationService.getAuthorizationInfo(userCode, approver));
    }

    @PreAuthorize("isFullyAuthenticated()")
    @PostMapping("/oauth/grant")
    public ResponseEntity<Boolean> approve(
            @AuthenticationPrincipal ApplicationClientPrincipal clientPrincipial,
            @RequestBody DeviceAuthorizationDecisionRequest deviceAuthorizationDecisionRequest
    ) {
        deviceAuthorizationService.approve(deviceAuthorizationDecisionRequest, clientPrincipial);
        return ResponseEntity.ok(true);
    }

    @Operation(summary = "JWKS — публичные ключи для проверки сервисных (ES256) токенов")
    @GetMapping("/oauth/keys")
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok(minecraftServerAccessTokenService.getJwks());
    }

    @Operation(summary = "Каталог прав: имя константы → код права. ETag — версия каталога, If-None-Match → 304")
    @GetMapping("/permissions")
    public ResponseEntity<Map<String, String>> permissions(WebRequest request) {
        final var version = permissionCatalog.version();
        if (request.checkNotModified(version)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(version).cacheControl(CacheControl.noCache()).build();
        }
        return ResponseEntity.ok().eTag(version).cacheControl(CacheControl.noCache()).body(permissionCatalog.permissions());
    }
}

