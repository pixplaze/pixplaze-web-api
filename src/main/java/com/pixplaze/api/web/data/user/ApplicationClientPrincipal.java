package com.pixplaze.api.web.data.user;

import com.pixplaze.api.ext.data.player.MinecraftPlayerInfo;
import jakarta.annotation.Nonnull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.List;

/**
 * Принципал пользователя приложения (source = {@code APPLICATION_AUTHORIZED_DEVICE}). Identity —
 * {@code id}; единственный субъект, который ещё и {@link UserDetails} (парольный DAO-путь). Имя
 * субъекта ({@link #getName()}) — {@code name}; через Lombok он же {@code getUsername()}.
 *
 * <p>Роли только системные: {@code USER} плюс выданные вручную ({@code profile_role}). Игровые роли
 * в токен профиля не попадают — права на сервере едут в токене доступа к плагину. Связанные игроки
 * лежат в {@link #players} как данные для веб-приложения.
 */
@Getter
@Setter
@NoArgsConstructor
public class ApplicationClientPrincipal extends ClientPrincipal implements UserDetails {
    private Long id;
    private String name;
    private String email;
    private String password;

    /// Связанные MC-игроки профиля. Заполняется при сборке принципала из БД (логин, refresh).
    private List<MinecraftPlayerInfo> players = new ArrayList<>();

    @Override
    public @Nonnull String getUsername() {
        return name;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
