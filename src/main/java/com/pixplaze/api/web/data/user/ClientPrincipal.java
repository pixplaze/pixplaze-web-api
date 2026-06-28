package com.pixplaze.api.web.data.user;

import com.pixplaze.api.ext.data.Authority;
import jakarta.annotation.Nonnull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;

/**
 * Базовый принципал любого субъекта запроса (профиль / игрок / сервер) и одновременно его
 * {@link Authentication} в {@code SecurityContext}. Несёт общие измерения: {@link Authority}
 * (роли/источник/таргеты/права), транспортный {@code ipAddress} и флаг {@code authenticated}.
 *
 * <p>{@code UserDetails} (пароль) реализует только {@link ApplicationClientPrincipal} — единственный
 * субъект парольного DAO-пути. Идентичность (profile id / player uuid / server id) задаётся
 * подклассами; имя субъекта — {@link #getName()}.
 *
 * <p>{@code authenticated} по умолчанию {@code false}: подтверждённым принципал делает только
 * reader/фильтр после верификации подписи. Провизорный субъект device-flow остаётся {@code false}.
 */
@Getter
@Setter
public abstract class ClientPrincipal implements Authentication {

    private Authority authority = Authority.as(Authority.Role.USER)
            .from(Authority.Source.APPLICATION_AUTHORIZED_DEVICE)
            .unauthorized();

    /// IP запрашивающей стороны (заполняется JWT-фильтром из запроса); не из токена, а транспортный.
    private String ipAddress;

    private boolean authenticated = false;

    @Override
    public @Nonnull Collection<? extends GrantedAuthority> getAuthorities() {
        return authority.describe()
                .stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }

    /// Токеновые принципалы беспарольные — учётные данные в Authentication не носим.
    @Override
    public Object getCredentials() {
        return null;
    }

    /// Токеновые принципалы не носят произвольных Details: сведения запроса на время device-flow
    /// живут в сессии авторизации, а identity после авторизации — в полях подклассов.
    @Override
    public Object getDetails() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return this;
    }
}
