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
 * <p>Роли ⊆ {@code {USER, MINECRAFT_PLAYER, MINECRAFT_OPERATOR}}: пока профиль ни с кем не связан —
 * только {@code USER}; после привязки игрока(ов) добавляются {@code MINECRAFT_PLAYER} и, если есть
 * оператор, {@code MINECRAFT_OPERATOR}, а сами игроки попадают в {@link #players}. Это даёт
 * профилю возможность действовать против Minecraft-сервера из веб-приложения.
 */
@Getter
@Setter
@NoArgsConstructor
public class ApplicationClientPrincipal extends ClientPrincipal implements UserDetails {
    private Long id;
    private String name;
    private String email;
    private String password;

    /// Связанные MC-игроки профиля — источник ролей MINECRAFT_PLAYER/MINECRAFT_OPERATOR.
    /// Заполняется при сборке принципала (логин/чтение токена); пусто ⇒ только USER.
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
