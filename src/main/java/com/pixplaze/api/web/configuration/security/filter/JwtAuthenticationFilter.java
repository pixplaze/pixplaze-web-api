package com.pixplaze.api.web.configuration.security.filter;

import com.pixplaze.api.web.data.user.User;
import com.pixplaze.api.web.service.auth.AccessTokenService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String HEADER_NAME = "Authorization";
    private final AccessTokenService accessTokenService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        final var authHeader = request.getHeader(HEADER_NAME);

        if (!isAuthorizationHeaderPresent(authHeader)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Обрезаем префикс и получаем имя пользователя из токена
            final var token = authHeader.substring(BEARER_PREFIX.length());
            final var userDetails = loadUserDetails(token);

            if (!isAuthenticationRequired(userDetails.getUsername())) {
                filterChain.doFilter(request, response);
                return;
            }

            final var authentication = loadAuthentication(request, userDetails);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);

        } catch (SignatureException | DecodingException | MalformedJwtException | ExpiredJwtException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token is invalid or could not be trusted.", e.getCause());
        }
    }

    private User loadUserDetails(String token) {
        return accessTokenService.extractUser(token);
    }

    private static @Nonnull UsernamePasswordAuthenticationToken loadAuthentication(@Nonnull HttpServletRequest request, UserDetails userDetails) {
        final var authToken = UsernamePasswordAuthenticationToken.authenticated(userDetails, null, userDetails.getAuthorities());
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        return authToken;
    }

    private static boolean isAuthenticationRequired(String username) {
        return StringUtils.isNotEmpty(username) && SecurityContextHolder.getContext().getAuthentication() == null;
    }

    private static boolean isAuthorizationHeaderPresent(String authHeader) {
        return StringUtils.isNotEmpty(authHeader) && authHeader.startsWith(BEARER_PREFIX);
    }
}
