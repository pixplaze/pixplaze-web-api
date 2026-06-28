package com.pixplaze.api.web.controller;

import com.pixplaze.api.ext.data.auth.AuthorizationTokenInfo;
import com.pixplaze.api.web.data.dto.DeviceAuthorizationDecisionRequest;
import com.pixplaze.api.web.data.dto.DeviceAuthorizationInfo;
import com.pixplaze.api.web.data.dto.ErrorResponse;
import com.pixplaze.api.web.data.dto.SignInRequest;
import com.pixplaze.api.web.data.dto.SignUpRequest;
import com.pixplaze.api.web.data.user.ApplicationClientPrincipal;
import com.pixplaze.api.web.exception.auth.DeviceAuthorizationError;
import com.pixplaze.api.web.exception.auth.DeviceAuthorizationException;
import com.pixplaze.api.web.exception.auth.InvalidRefreshTokenException;
import com.pixplaze.api.web.exception.voucher.InvalidInviteCodeException;
import com.pixplaze.api.web.exception.voucher.VoucherCodeValidationException;
import com.pixplaze.api.web.mapper.DeviceResponseMapper;
import com.pixplaze.api.web.service.ExceptionHandlerService;
import com.pixplaze.api.web.service.auth.MinecraftServerAccessTokenService;
import com.pixplaze.api.web.service.auth.AuthorizationService;
import com.pixplaze.api.web.service.auth.device.DeviceAuthorizationService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.IncorrectClaimException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.MissingClaimException;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.security.SignatureException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Аутентификация")
@RequestMapping("/auth")
public class AuthorizationController {
    // Кука refresh-токена scoped на /auth, чтобы ходить и на /auth/refresh, и на /auth/sign-out.
    private static final String REFRESH_COOKIE_PATH = "/auth";

    private final ExceptionHandlerService exceptionHandlerService;
    private final AuthorizationService authorizationService;
    private final DeviceAuthorizationService deviceAuthorizationService;
    private final DeviceResponseMapper deviceResponseMapper;
    private final MinecraftServerAccessTokenService minecraftServerAccessTokenService;

    @Operation(summary = "Регистрация пользователя")
    @PostMapping("/sign-up")
    public ResponseEntity<AuthorizationTokenInfo> signUp(@RequestBody @Valid SignUpRequest requestInfo) {
        try {
            final var responseInfo = authorizationService.signUp(requestInfo);
            final var responseCookie = authorizationService.createRefreshTokenCookie(responseInfo.refreshToken(), REFRESH_COOKIE_PATH);
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, responseCookie.toString())
                    .body(responseInfo.safe());
        } catch (InvalidInviteCodeException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
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
        final var AuthorizationTokenInfo = authorizationService.refresh(refreshToken);
        final var responseCookie = authorizationService.createRefreshTokenCookie(AuthorizationTokenInfo.refreshToken(), REFRESH_COOKIE_PATH);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, responseCookie.toString())
                .body(AuthorizationTokenInfo.safe());
    }

    @PostMapping(
            value = "/oauth/authorize",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> authorize(
            @RequestParam("client_id") String clientId,
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "authorization_details", required = false) String authorizationDetails
    ) {
        try {
            final var deviceResponse = deviceAuthorizationService.authorize(clientId, scope, authorizationDetails);
            return ResponseEntity.ok(deviceResponseMapper.toDeviceResponse(deviceResponse));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exceptionHandlerService.handleException(e, null));
        }
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
    public ResponseEntity<?> token(
            @RequestParam("grant_type") String grantType,
            @RequestParam(value = "refresh_token", required = false) String refreshToken,
            @RequestParam(value = "client_id", required = false) String clientId,
            @RequestParam(value = "device_code", required = false) String deviceCode
    ) {
        return switch (grantType) {
            case "refresh_token" -> refreshTokenGrant(refreshToken);
            case "urn:ietf:params:oauth:grant-type:device_code" -> ResponseEntity.ok(
                    deviceResponseMapper.toTokenResponse(
                            deviceAuthorizationService.poll(clientId, deviceCode),
                            minecraftServerAccessTokenService.getExpiresInSeconds()));
            default -> throw new DeviceAuthorizationException(DeviceAuthorizationError.UNSUPPORTED_GRANT_TYPE);
        };
    }

    /** RFC 6749 §6: обмен сервисного refresh-токена на свежий access (с ротацией refresh). */
    private ResponseEntity<?> refreshTokenGrant(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new DeviceAuthorizationException(DeviceAuthorizationError.INVALID_REQUEST);
        }

        try {
            // В отличие от веб-refresh приходит параметром (не кукой)
            final var tokens = authorizationService.refresh(refreshToken);
            return ResponseEntity.ok(deviceResponseMapper.toTokenResponse(tokens, minecraftServerAccessTokenService.getExpiresInSeconds()));
        } catch (InvalidRefreshTokenException e) {
            throw new DeviceAuthorizationException(DeviceAuthorizationError.INVALID_GRANT);
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

    @ExceptionHandler({DeviceAuthorizationException.class})
    public ResponseEntity<Map<String, String>> handleDeviceAuthorizationException(DeviceAuthorizationException e) {
        final var status = e.getError() == DeviceAuthorizationError.SERVER_ERROR
                ? HttpStatus.INTERNAL_SERVER_ERROR
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(Map.of("error", e.getError().getCode()));
    }

    @ExceptionHandler({
            InvalidRefreshTokenException.class,
            SignatureException.class,
            DecodingException.class,
            MalformedJwtException.class,
            ExpiredJwtException.class,
            BadCredentialsException.class
    })
    public ResponseEntity<ErrorResponse> handleAuthenticationException(Exception exception, HttpServletRequest httpServletRequest) {
        final var errorResponseInfo = exceptionHandlerService.handleException(exception, httpServletRequest).withStatus(HttpStatus.UNAUTHORIZED);
        return ResponseEntity.status(errorResponseInfo.status()).body(errorResponseInfo);
    }

    @ExceptionHandler({VoucherCodeValidationException.class, IncorrectClaimException.class, MissingClaimException.class})
    public ResponseEntity<ErrorResponse> handleTokenValidationException(Exception exception, HttpServletRequest httpServletRequest) {
        final var errorResponseInfo = exceptionHandlerService.handleException(exception, httpServletRequest).withStatus(HttpStatus.FORBIDDEN);
        return ResponseEntity.status(errorResponseInfo.status()).body(errorResponseInfo);
    }
}

