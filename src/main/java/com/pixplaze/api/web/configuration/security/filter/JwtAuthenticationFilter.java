package com.pixplaze.api.web.configuration.security.filter;

import com.pixplaze.api.web.service.auth.ClientPrincipalReader;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String HEADER_NAME = "Authorization";

    private final ClientPrincipalReader clientPrincipalReader;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        final var authHeader = request.getHeader(HEADER_NAME);

        if (!isAuthorizationHeaderPresent(authHeader) || isAlreadyAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            final var token = authHeader.substring(BEARER_PREFIX.length());
            // reader верифицирует подпись и собирает нужный подкласс принципала по ролям токена.
            final var principal = clientPrincipalReader.read(token);
            // Транспортный IP запрашивающего — на принципал (аудит/сверка IP в device-flow и пр.).
            principal.setIpAddress(resolveClientIpAddress(request));
            // Принципал сам себе Authentication: подтверждаем (подпись проверена) и кладём в контекст.
            principal.setAuthenticated(true);
            SecurityContextHolder.getContext().setAuthentication(principal);
            filterChain.doFilter(request, response);

        } catch (SignatureException | DecodingException | MalformedJwtException | UnsupportedJwtException | ExpiredJwtException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token is invalid or could not be trusted.", e.getCause());
        }
    }

    private static boolean isAlreadyAuthenticated() {
        return SecurityContextHolder.getContext().getAuthentication() != null;
    }

    private static boolean isAuthorizationHeaderPresent(String authHeader) {
        return StringUtils.isNotEmpty(authHeader) && authHeader.startsWith(BEARER_PREFIX);
    }

    /**
     * IP клиента: первый хоп {@code X-Forwarded-For} (если за доверенным прокси), иначе
     * {@code remoteAddr}. Best-effort — за непрозрачным прокси без XFF вернёт адрес прокси.
     */
    private static String resolveClientIpAddress(HttpServletRequest request) {
        final var forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.isNotBlank(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
