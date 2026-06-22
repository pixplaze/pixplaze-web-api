package com.pixplaze.api.web.controller;

import com.pixplaze.api.ext.data.auth.AuthorizationTokenInfo;
import com.pixplaze.api.web.data.dto.DeviceAuthorizationDecisionRequestInfo;
import com.pixplaze.api.web.data.dto.ErrorResponseInfo;
import com.pixplaze.api.web.data.dto.SignInRequestInfo;
import com.pixplaze.api.web.data.dto.SignUpRequestInfo;
import com.pixplaze.api.web.data.user.ClientPrincipial;
import com.pixplaze.api.web.exception.auth.DeviceAuthorizationException;
import com.pixplaze.api.web.exception.http.NotImplementedException;
import com.pixplaze.api.web.exception.voucher.InvalidInviteCodeException;
import com.pixplaze.api.web.exception.voucher.VoucherCodeValidationException;
import com.pixplaze.api.web.service.ExceptionHandlerService;
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
    private static final String REFRESH_URL = "/auth/refresh";

    private final ExceptionHandlerService exceptionHandlerService;
    private final AuthorizationService authorizationService;
    private final DeviceAuthorizationService deviceAuthorizationService;

    @Operation(summary = "Регистрация пользователя")
    @PostMapping("/sign-up")
    public ResponseEntity<AuthorizationTokenInfo> signUp(@RequestBody @Valid SignUpRequestInfo requestInfo) {
        try {
            final var responseInfo = authorizationService.signUp(requestInfo);
            final var responseCookie = authorizationService.createRefreshTokenCookie(responseInfo.refreshToken(), REFRESH_URL);
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, responseCookie.toString())
                    .body(responseInfo.safe());
        } catch (InvalidInviteCodeException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    @Operation(summary = "Авторизация пользователя")
    @PostMapping("/sign-in")
    public ResponseEntity<AuthorizationTokenInfo> signIn(@RequestBody @Valid SignInRequestInfo requestInfo) {
        final var responseInfo = authorizationService.signIn(requestInfo);
        final var responseCookie = authorizationService.createRefreshTokenCookie(responseInfo.refreshToken(), REFRESH_URL);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, responseCookie.toString())
                .body(responseInfo.safe());
    }

    @Operation(summary = "Выйти из профиля")
    @PostMapping("/sign-out")
    public AuthorizationTokenInfo signOut() {
        throw new NotImplementedException();
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthorizationTokenInfo> refresh(
            @CookieValue(name = "refreshToken") String refreshToken
    ) {
        final var AuthorizationTokenInfo = authorizationService.refresh(refreshToken);
        final var responseCookie = authorizationService.createRefreshTokenCookie(AuthorizationTokenInfo.refreshToken(), REFRESH_URL);

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
            return ResponseEntity.ok(deviceAuthorizationService.authorize(clientId, scope, authorizationDetails));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exceptionHandlerService.handleException(e, null));
        }
    }

    @PostMapping(
            value = "/oauth/authorize/token",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )

    public ResponseEntity<?> poll(
            @RequestParam("grant_type") String grantType,
            @RequestParam("client_id") String clientId,
            @RequestParam("device_code") String deviceCode
    ) {
        // Проверяем grant_type согласно спецификации RFC 8628 (Раздел 3.4)
        if (!"urn:ietf:params:oauth:grant-type:device_code".equals(grantType)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "unsupported_grant_type"));
        }

        return ResponseEntity.ok(deviceAuthorizationService.poll(clientId, deviceCode));
    }

    @PostMapping("/oauth/authorize/grant")
    public ResponseEntity<Boolean> approve(
            @AuthenticationPrincipal ClientPrincipial clientPrincipial,
            @RequestBody DeviceAuthorizationDecisionRequestInfo deviceAuthorizationDecisionRequestInfo
    ) {
        deviceAuthorizationService.approve(deviceAuthorizationDecisionRequestInfo, clientPrincipial);
        return ResponseEntity.ok(true);
    }

    @ExceptionHandler({DeviceAuthorizationException.class})
    public ResponseEntity<?> handleDeviceAuthorizationException(DeviceAuthorizationException e) {
        return switch (e.getMessage()) {
            case "authorization_pending" -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "authorization_pending"));
            case "slow_down" -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "slow_down"));
            case "access_denied" -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "access_denied"));
            case "expired_token" -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "expired_token"));
            default -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "invalid_grant"));
        };
    }

    @ExceptionHandler({SignatureException.class, DecodingException.class, MalformedJwtException.class, ExpiredJwtException.class, BadCredentialsException.class})
    public ResponseEntity<ErrorResponseInfo> handleAuthenticationException(Exception exception, HttpServletRequest httpServletRequest) {
        final var errorResponseInfo = exceptionHandlerService.handleException(exception, httpServletRequest).withStatus(HttpStatus.UNAUTHORIZED);
        return ResponseEntity.status(errorResponseInfo.status()).body(errorResponseInfo);
    }

    @ExceptionHandler({VoucherCodeValidationException.class, IncorrectClaimException.class, MissingClaimException.class})
    public ResponseEntity<ErrorResponseInfo> handleTokenValidationException(Exception exception, HttpServletRequest httpServletRequest) {
        final var errorResponseInfo = exceptionHandlerService.handleException(exception, httpServletRequest).withStatus(HttpStatus.FORBIDDEN);
        return ResponseEntity.status(errorResponseInfo.status()).body(errorResponseInfo);
    }
}

