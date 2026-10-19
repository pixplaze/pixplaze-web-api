package com.pixplaze.api.web.service;

import com.pixplaze.api.ext.data.auth.Authority;
import com.pixplaze.api.web.configuration.security.SecurityConfiguration;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftPlayer;
import com.pixplaze.api.web.data.db.tables.pojos.Profile;
import com.pixplaze.api.web.data.dto.ProfileInfo;
import com.pixplaze.api.web.data.dto.ProfileUpdateRequest;
import com.pixplaze.api.web.data.dto.UserInfo;
import com.pixplaze.api.web.data.user.ApplicationClientPrincipal;
import com.pixplaze.api.web.exception.http.BadRequestException;
import com.pixplaze.api.web.exception.http.ConflictException;
import com.pixplaze.api.web.mapper.MinecraftPlayerMapper;
import com.pixplaze.api.web.mapper.ProfileMapper;
import com.pixplaze.api.web.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final PasswordEncoder passwordEncoder;

    @Value("${app.url.api.gateway}")
    private String apiGateway;

    /**
     * Создание пользователя
     * @return созданный пользователь
     */
    public Profile create(Profile profile) {
        return profileRepository.create(profile);
    }

    /// Меняет переданные поля профиля, {@code null} — без изменений. Новый пароль принимается
    /// только вместе с верным текущим. Неверный текущий пароль — 400, а не 401: на 401 клиент
    /// обновляет токен и повторяет запрос.
    @Transactional
    public void update(long profileId, ProfileUpdateRequest request) {
        final var profile = getById(profileId);

        if (request.password() != null) {
            if (request.currentPassword() == null
                    || !passwordEncoder.matches(request.currentPassword(), profile.getPassword())) {
                throw new BadRequestException("Current password is incorrect");
            }
            profile.setPassword(passwordEncoder.encode(request.password()));
        }

        if (request.username() != null && !request.username().equals(profile.getName())) {
            if (existsByUsername(request.username())) {
                throw new ConflictException("Username '%s' is already taken!".formatted(request.username()));
            }
            profile.setName(request.username());
        }

        if (request.email() != null && !request.email().equals(profile.getEmail())) {
            // Смена регистра своего же адреса — не конфликт: existsByEmail без учёта регистра найдёт сам профиль.
            if (!request.email().equalsIgnoreCase(profile.getEmail()) && existsByEmail(request.email())) {
                throw new ConflictException("Email '%s' is already taken!".formatted(request.email()));
            }
            profile.setEmail(request.email());
        }

        if (request.isOnlineHidden() != null) {
            profile.setIsOnlineHidden(request.isOnlineHidden());
        }

        try {
            profileRepository.update(profile);
        } catch (DuplicateKeyException e) {
            // Гонка с регистрацией или другой правкой: проверки выше её не закрывают.
            throw new ConflictException("Username or email is already taken!");
        }
    }

    public boolean existsByUsername(String username) {
        return profileRepository.existsByUsername(username);
    }

    /// Без учёта регистра, в отличие от UNIQUE в БД.
    public boolean existsByEmail(String email) {
        return profileRepository.existsByEmail(email);
    }

    public List<ProfileInfo> getProfiles(Integer limit, Integer offset) {
        return profileRepository.getProfiles(limit, offset).stream()
                .map(profileMapper::toInfo)
                .toList();
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

    public UserInfo getUserInfo(Long profileId) {
        final var profile = profileMapper.toInfo(getById(profileId));
        final var profilePlayers = minecraftPlayerService.findLinkedByProfileId(profileId)
                .stream()
                .map(minecraftPlayerMapper::toInfo)
                .toList();
        final var profileFavoriteServers = minecraftServerService.getFavorite(profileId);

        return new UserInfo(profile, profilePlayers, profileFavoriteServers);
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

    /// Связанные MC-игроки профиля (uuid/имя/голова скина/флаг оператора) — данные об игроках
    /// для веб-приложения. У ещё не
    /// созданного профиля ({@code id == null}, путь регистрации) связей нет — пустой список.
    private List<MinecraftPlayer> loadLinkedPlayers(Long profileId) {
        if (profileId == null) {
            return List.of();
        }
        return minecraftPlayerService.findLinkedByProfileId(profileId);
    }

    /// Собирает authority профиля (source=AAD). Роли только системные: всегда {@code USER} плюс выданные
    /// вручную ({@code profile_role}, например {@code ADMIN}). Игровые роли в токен профиля не попадают:
    /// права на конкретном сервере выводятся отдельно и едут в токене доступа к плагину. Аудитория —
    /// только api-gateway: токен профиля не уходит ни в один плагин.
    private void applyAuthority(ApplicationClientPrincipal principal) {
        final var roles = new ArrayList<Authority.Role>();
        roles.add(Authority.Role.USER);
        if (principal.getId() != null) {
            profileRepository.findGrantedRoleCodes(principal.getId()).stream()
                    .map(Authority.Role::of)
                    .filter(role -> !roles.contains(role))
                    .forEach(roles::add);
        }

        principal.setAuthority(Authority.as(roles.toArray(new Authority.Role[0]))
                .from(Authority.Source.APPLICATION_AUTHORIZED_DEVICE)
                .to(apiGateway)
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
