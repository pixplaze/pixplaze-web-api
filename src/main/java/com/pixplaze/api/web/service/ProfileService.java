package com.pixplaze.api.web.service;

import com.pixplaze.api.ext.data.auth.Authority;
import com.pixplaze.api.ext.data.player.MinecraftPlayerInfo;
import com.pixplaze.api.web.configuration.security.SecurityConfiguration;
import com.pixplaze.api.ext.data.auth.MinecraftServerTargets;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftPlayer;
import com.pixplaze.api.web.data.db.tables.pojos.Profile;
import com.pixplaze.api.web.data.dto.ProfileInfo;
import com.pixplaze.api.web.data.user.ApplicationClientPrincipal;
import com.pixplaze.api.web.mapper.MinecraftPlayerMapper;
import com.pixplaze.api.web.mapper.MinecraftServerMapper;
import com.pixplaze.api.web.mapper.ProfileMapper;
import com.pixplaze.api.web.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileService {
    private final ProfileRepository profileRepository;
    private final ProfileMapper profileMapper;
    private final MinecraftPlayerService minecraftPlayerService;
    private final MinecraftServerService minecraftServerService;
    private final MinecraftPlayerMapper minecraftPlayerMapper;
    private final MinecraftServerMapper minecraftServerMapper;

    @Value("${app.url.api.gateway}")
    private String apiGateway;

    /**
     * Создание пользователя
     * @return созданный пользователь
     */
    public Profile create(Profile profile) {
        return profileRepository.create(profile);
    }

    public List<Profile> getProfiles(Integer limit, Integer offset) {
        return profileRepository.getUsers(limit, offset);
    }

    public List<Profile> getProfiles(String username) {
        return profileRepository.getAllByUsernameStartingWith(username);
    }

    /**
     * Получение пользователя по имени пользователя
     *
     * @return пользователь
     */
    public Profile getUserByUsername(String username) {
        return profileRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден"));
    }

    public Profile getById(Long id) {
        return profileRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("Профиль не найден: " + id));
    }

    public ProfileInfo getProfileInfo(Long profileId) {
        final var profile = getById(profileId).setPassword(null);
        final var profilePlayers = minecraftPlayerService.findLinkedByProfileId(profileId)
                .stream()
                .map(minecraftPlayerMapper::toInfo)
                .toList();
        final var profileFavoriteServers = minecraftServerService.getFavorite(profileId)
                .stream()
                .map(minecraftServerMapper::toInfo)
                .toList();

        return new ProfileInfo(profile, profilePlayers, profileFavoriteServers);
    }

    /**
     * Получение пользователя по имени пользователя
     * <p>
     * Нужен для Spring Security
     *
     * @return пользователь
     * @deprecated переписать на реализоцию интерфейса с маппингом
     */
    @Deprecated(forRemoval = true)
    public UserDetailsService getUserDetailsService() {
        return username -> ProfileService.this.profileRepository.findByUsername(username)
                .map(profileMapper::toApplicationClientPrincipal)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден"));
    }

    public ApplicationClientPrincipal toApplicationClientPrincipal(Profile profile) {
        final var principal = profileMapper.toApplicationClientPrincipal(profile);
        final var players = loadLinkedPlayers(principal.getId()).stream()
                .map(minecraftPlayerMapper::toInfo)
                .toList();
        principal.setPlayers(players);
        applyAuthority(principal);
        return principal;
    }

    public void linkMinecraftPlayer(Long profileId, UUID playerUuid) {
        minecraftPlayerService.linkProfile(playerUuid, profileId);
    }

    public void addMinecraftServerFavorite(Long profileId, Long serverId) {
        minecraftServerService.addFavorite(serverId, profileId);
    }

    /// Связанные MC-игроки профиля (uuid/имя/голова скина/флаг оператора) — источник ролей
    /// MINECRAFT_PLAYER/MINECRAFT_OPERATOR и данных об игроках для веб-приложения. У ещё не
    /// созданного профиля ({@code id == null}, путь регистрации) связей нет — пустой список.
    private List<MinecraftPlayer> loadLinkedPlayers(Long profileId) {
        if (profileId == null) {
            return List.of();
        }
        return minecraftPlayerService.findLinkedByProfileId(profileId);
    }

    /// Собирает authority профиля (source=AAD): роли и targets (≡ aud).
    /// Роли: всегда {@code USER}; есть связанный игрок ⇒ {@code +MINECRAFT_PLAYER}; есть оператор
    /// хотя бы на одном сервере ⇒ {@code +MINECRAFT_OPERATOR}; плюс выданные вручную ({@code profile_role},
    /// например {@code ADMIN}).
    /// Targets: всегда api-gateway (профиль ходит в BFF) + зоны серверов, где состоят его игроки
    /// (членство minecraft_server_player; MC валидирует токен профиля напрямую).
    private void applyAuthority(ApplicationClientPrincipal principal) {
        final var players = principal.getPlayers();

        final var roles = new ArrayList<Authority.Role>();
        roles.add(Authority.Role.USER);
        if (!players.isEmpty()) {
            roles.add(Authority.Role.MINECRAFT_PLAYER);
            if (players.stream().anyMatch(player -> Boolean.TRUE.equals(player.isOperator()))) {
                roles.add(Authority.Role.MINECRAFT_OPERATOR);
            }
        }

        if (principal.getId() != null) {
            profileRepository.findGrantedRoleCodes(principal.getId()).stream()
                    .map(Authority.Role::of)
                    .filter(role -> !roles.contains(role))
                    .forEach(roles::add);
        }

        final var targets = new ArrayList<String>();
        targets.add(apiGateway);
        if (principal.getId() != null) {
            minecraftPlayerService.findServerIdsByProfileId(principal.getId()).stream()
                    .map(MinecraftServerTargets::of)
                    .forEach(targets::add);
        }

        principal.setAuthority(Authority.as(roles.toArray(new Authority.Role[0]))
                .from(Authority.Source.APPLICATION_AUTHORIZED_DEVICE)
                .to(targets)
                .grant());
    }

    /**
     * Выдача прав администратора текущему пользователю
     * <p>
     * Нужен для демонстрации
     */
    @Deprecated
    public void getAdmin() {
        final var authority = Authority.as(Authority.Role.SYSTEM)
                .from(Authority.Source.APPLICATION_AUTHORIZED_DEVICE)
                .to("api.pixplaze.net")
                .grant();
        Objects.requireNonNull(SecurityConfiguration.currentUser()).setAuthority(authority);
    }
}
