package com.pixplaze.api.web.configuration.security;

import com.pixplaze.api.web.configuration.properties.AccessTokenProperties;
import com.pixplaze.api.web.configuration.properties.CorsProperties;
import com.pixplaze.api.web.configuration.security.filter.JwtAuthenticationFilter;
import com.pixplaze.api.web.data.user.ClientPrincipal;
import com.pixplaze.api.web.service.ExceptionHandlerService;
import com.pixplaze.api.web.service.auth.ClientPrincipalReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity
@EnableConfigurationProperties({CorsProperties.class, AccessTokenProperties.class})
public class SecurityConfiguration {
    private final ClientPrincipalReader clientPrincipalReader;
    private final ExceptionHandlerService exceptionHandlerService;
    private final CorsProperties corsProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http.csrf(AbstractHttpConfigurer::disable);
        http.cors(this::configureCors);
        http.authorizeHttpRequests(this::configureHttpRequests);
        http.sessionManagement(this::configureSessionManagement);
        http.addFilterBefore(new JwtAuthenticationFilter(clientPrincipalReader, exceptionHandlerService), UsernamePasswordAuthenticationFilter.class);
        http.exceptionHandling(this::configureExceptionHandler);
        return http.build();
    }

    private void configureCors(CorsConfigurer<HttpSecurity> corsConfigurer) {
        final var corsConfiguration = new CorsConfiguration();

        corsConfiguration.setAllowedOriginPatterns(corsProperties.allowedOrigins());
        corsConfiguration.setAllowedMethods(corsProperties.allowedMethods());
        corsConfiguration.setAllowedHeaders(corsProperties.allowedHeaders());
        // ETag каталога прав (GET /auth/permissions) фронт сохраняет и шлёт в If-None-Match
        corsConfiguration.setExposedHeaders(List.of(HttpHeaders.ETAG));
        corsConfiguration.setAllowCredentials(corsProperties.allowCredentials());
        corsConfiguration.setAllowCredentials(true);

        final var corsConfigurationSource = new UrlBasedCorsConfigurationSource();
        corsConfigurationSource.registerCorsConfiguration("/**", corsConfiguration);

        corsConfigurer.configurationSource(corsConfigurationSource);
    }

    private void configureHttpRequests(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth.requestMatchers("/auth/device/confirm").authenticated();
        auth.requestMatchers("/auth/oauth/keys").hasAllAuthorities("SOURCE_MINECRAFT_AUTHORIZED_DEVICE", "ROLE_MINECRAFT_SERVER");
        auth.requestMatchers("/auth/**").permitAll();
        auth.requestMatchers("/vouchers/invite/**").permitAll();
        auth.requestMatchers("/error/**").permitAll();
        auth.requestMatchers(HttpMethod.GET, "/info/version").permitAll();
        auth.requestMatchers("/swagger-ui/**", "/swagger-resources/*", "/v3/api-docs/**").permitAll();
        auth.requestMatchers("/endpoint", "/admin/**").hasRole("ADMIN");
        // Публичный листинг серверов. Порядок важен: личное (избранное, свои заявки) защищаем ДО общего
        // /servers/*, чтобы GET /servers/* не сделал их анонимными.
        auth.requestMatchers(HttpMethod.GET, "/servers/favorite", "/servers/bids").authenticated();
        auth.requestMatchers(HttpMethod.GET, "/servers", "/servers/*").permitAll();
        auth.requestMatchers(HttpMethod.POST, "/servers/state").permitAll();
        auth.anyRequest().authenticated();
    }

    private void configureSessionManagement(SessionManagementConfigurer<HttpSecurity> manager) {
        manager.sessionCreationPolicy(STATELESS);
    }

    /// Отказы Spring Security (нет токена, {@code @PreAuthorize} не прошёл) — причина в лог на DEBUG, ответ общий.
    private void configureExceptionHandler(ExceptionHandlingConfigurer<HttpSecurity> e) {
        e.authenticationEntryPoint((request, response, exception) -> {
            log.debug("Request rejected with 401: {} [{} {}]", exception.getMessage(), request.getMethod(), request.getRequestURI());
            exceptionHandlerService.sendErrorResponseInfo(request, response, exception);
        });
        e.accessDeniedHandler((request, response, exception) -> {
            log.debug("Request rejected with 403: {} [{} {}] by {}", exception.getMessage(), request.getMethod(), request.getRequestURI(),
                    SecurityContextHolder.getContext().getAuthentication() instanceof ClientPrincipal principal
                            ? principal.getAuthority().describe() : "anonymous");
            exceptionHandlerService.sendErrorResponseInfo(request, response, exception);
        });
    }

    @Bean
    @RequestScope
    public static ClientPrincipal currentUser() {
        final var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }

        return (ClientPrincipal) authentication.getPrincipal();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /// Вход по паролю: {@code DaoAuthenticationProvider} Spring собирает сам из единственного
    /// {@code UserDetailsService} ({@link com.pixplaze.api.web.service.auth.ProfileUserDetailsService})
    /// и бина {@link PasswordEncoder}.
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) {
        return config.getAuthenticationManager();
    }
}
