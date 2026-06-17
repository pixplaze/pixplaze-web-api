package com.pixplaze.api.web.configuration.security;

import com.pixplaze.api.ext.data.Authority;
import com.pixplaze.api.web.configuration.properties.CorsProperties;
import com.pixplaze.api.web.configuration.security.filter.JwtAuthenticationFilter;
import com.pixplaze.api.web.data.user.Profile;
import com.pixplaze.api.web.service.ExceptionHandlerService;
import com.pixplaze.api.web.service.UserService;
import com.pixplaze.api.web.service.auth.AccessTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
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

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfiguration {
    private final AccessTokenService accessTokenService;
    private final UserService userService;
    private final ExceptionHandlerService exceptionHandlerService;
    private final CorsProperties corsProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http.csrf(AbstractHttpConfigurer::disable);
        http.cors(this::configureCors);
        http.authorizeHttpRequests(this::configureHttpRequests);
        http.sessionManagement(this::configureSessionManagement);
        http.addFilterBefore(new JwtAuthenticationFilter(accessTokenService), UsernamePasswordAuthenticationFilter.class);
        http.exceptionHandling(this::configureExceptionHandler);
        return http.build();
    }

    private void configureCors(CorsConfigurer<HttpSecurity> corsConfigurer) {
        final var corsConfiguration = new CorsConfiguration();

        corsConfiguration.setAllowedOriginPatterns(corsProperties.allowedOrigins());
        corsConfiguration.setAllowedMethods(corsProperties.allowedMethods());
        corsConfiguration.setAllowedHeaders(corsProperties.allowedHeaders());
        corsConfiguration.setAllowCredentials(corsProperties.allowCredentials());
        corsConfiguration.setAllowCredentials(true);

        final var corsConfigurationSource = new UrlBasedCorsConfigurationSource();
        corsConfigurationSource.registerCorsConfiguration("/**", corsConfiguration);

        corsConfigurer.configurationSource(corsConfigurationSource);
    }

    private void configureHttpRequests(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth.requestMatchers("/auth/device/confirm").authenticated();
        auth.requestMatchers("/auth/**").permitAll();
        auth.requestMatchers("/vouchers/invite/**").permitAll();
        auth.requestMatchers("/error/**").permitAll();
        auth.requestMatchers("/swagger-ui/**", "/swagger-resources/*", "/v3/api-docs/**").permitAll();
        auth.requestMatchers("/endpoint", "/admin/**").hasRole("ADMIN");
        auth.anyRequest().authenticated();
    }

    private void configureSessionManagement(SessionManagementConfigurer<HttpSecurity> manager) {
        manager.sessionCreationPolicy(STATELESS);
    }

    private void configureExceptionHandler(ExceptionHandlingConfigurer<HttpSecurity> e) {
        e.authenticationEntryPoint(exceptionHandlerService::sendErrorResponseInfo);
        e.accessDeniedHandler((exceptionHandlerService::sendErrorResponseInfo));
    }

    @Bean
    @RequestScope
    public Profile currentUser() {
        final var authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }

        return (Profile) authentication.getPrincipal();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userService.getUserDetailsService());
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) {
        return config.getAuthenticationManager();
    }
}
