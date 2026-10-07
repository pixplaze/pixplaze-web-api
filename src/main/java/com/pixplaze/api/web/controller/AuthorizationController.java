package com.pixplaze.api.web.controller;

import com.pixplaze.api.ext.data.auth.AuthorizationTokenInfo;
import com.pixplaze.api.web.data.dto.DeviceAuthorizationDecisionRequest;
import com.pixplaze.api.web.data.dto.DeviceAuthorizationInfo;
import com.pixplaze.api.web.data.dto.SignInRequest;
import com.pixplaze.api.web.data.dto.SignUpRequest;
import com.pixplaze.api.web.data.user.ApplicationClientPrincipal;
import com.pixplaze.api.ext.data.oauth.DeviceAuthorizationResponse;
import com.pixplaze.api.ext.data.oauth.OAuthError;
import com.pixplaze.api.web.exception.auth.DeviceAuthorizationException;
import com.pixplaze.api.web.exception.auth.InvalidRefreshTokenException;
import com.pixplaze.api.web.mapper.DeviceResponseMapper;
import com.pixplaze.api.web.service.auth.MinecraftServerAccessTokenService;
import com.pixplaze.api.web.service.auth.AuthorizationService;
import com.pixplaze.api.web.service.auth.device.DeviceAuthorizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Аутентификация")
@RequestMapping("/auth")
public class AuthorizationController {
    // Кука refresh-токена scoped на /auth, чтобы ходить и на /auth/refresh, и на /auth/sign-out.
    private static final String REFRESH_COOKIE_PATH = "/auth";

    private final AuthorizationService authorizationService;
    private final DeviceAuthorizationService deviceAuthorizationService;
    private final DeviceResponseMapper deviceResponseMapper;
    private final MinecraftServerAccessTokenService minecraftServerAccessTokenService;

    @Operation(summary = "Регистрация пользователя")
    @PostMapping("/sign-up")
    public ResponseEntity<AuthorizationTokenInfo> signUp(@RequestBody @Valid SignUpRequest requestInfo) {
        final var responseInfo = authorizationService.signUp(requestInfo);
        final var responseCookie = authorizationService.createRefreshTokenCookie(responseInfo.refreshToken(), REFRESH_COOKIE_PATH);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, responseCookie.toString())
                .body(responseInfo.safe());
    }

    @Operation(summary = "Авторизация пользователя")
    @PostMapping("/sign-in")
    public ResponseEntity<AuthorizationTokenInfo> signIn(@RequestBody @Valid SignInRequest requestInfo) {
        final var responseInfo = authorizationService.signIn(requestInfo);
        final var responseCookie = authorizationService.createRefreshTokenCookie(responseInfo.refreshToken(), REFRESH_COOKIE_PATH);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, responseCookie.toString())
                .body(responseInfo.safe());
    }

    @Operation(summary = "Выйти из профиля")
    @PostMapping("/sign-out")
    public ResponseEntity<Void> signOut(
            @AuthenticationPrincipal ApplicationClientPrincipal principal,
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            @RequestParam(defaultValue = "false") Boolean fromAll
    ) {
        authorizationService.signOut(refreshToken, principal, fromAll);
        final var clearedCookie = authorizationService.createClearedRefreshTokenCookie(REFRESH_COOKIE_PATH);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearedCookie.toString())
                .build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthorizationTokenInfo> refresh(
            @CookieValue(name = "refreshToken") String refreshToken
    ) {
        final var tokens = authorizationService.refresh(refreshToken);
        final var responseCookie = authorizationService.createRefreshTokenCookie(tokens.refreshToken(), REFRESH_COOKIE_PATH);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, responseCookie.toString())
                .body(new AuthorizationTokenInfo(tokens.accessToken(), null));
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
            @RequestParam(value = "authorization_details", required = false) String authorizationDetails
    ) {
        // Ошибки device-authorize (DeviceAuthorizationException) → OAuth-формат в ApiExceptionHandler.
        return ResponseEntity.ok(deviceAuthorizationService.authorize(clientId, scope, authorizationDetails));
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
            throw new DeviceAuthorizationException(OAuthError.INVALID_REQUEST);
        }

        return switch (grantType) {
            case "refresh_token" -> refreshTokenGrant(refreshToken);
            case "urn:ietf:params:oauth:grant-type:device_code" ->
                    ResponseEntity.ok(deviceResponseMapper.toTokenResponse(deviceAuthorizationService.poll(clientId, deviceCode)));
            default -> throw new DeviceAuthorizationException(OAuthError.UNSUPPORTED_GRANT_TYPE);
        };
    }

    /** RFC 6749 §6: обмен сервисного refresh-токена на свежий access (с ротацией refresh). */
    private ResponseEntity<Map<String, Object>> refreshTokenGrant(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new DeviceAuthorizationException(OAuthError.INVALID_REQUEST);
        }

        try {
            return ResponseEntity.ok(deviceResponseMapper.toTokenResponse(authorizationService.refresh(refreshToken)));
        } catch (InvalidRefreshTokenException e) {
            throw new DeviceAuthorizationException(OAuthError.INVALID_GRANT);
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
}

