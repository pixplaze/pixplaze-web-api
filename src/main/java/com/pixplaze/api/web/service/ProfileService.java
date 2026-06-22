package com.pixplaze.api.web.service;

import com.pixplaze.api.ext.data.Authority;
import com.pixplaze.api.web.configuration.security.SecurityConfiguration;
import com.pixplaze.api.web.data.db.tables.pojos.Profile;
import com.pixplaze.api.web.data.user.ClientPrincipial;
import com.pixplaze.api.web.mapper.ProfileMapper;
import com.pixplaze.api.web.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProfileService {
    private final ProfileRepository profileRepository;
    private final ProfileMapper profileMapper;

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
                .map(profileMapper::toClientPrincipial)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден"));
    }

    public ClientPrincipial toClientPrincipial(Profile profile) {
        return profileMapper.toClientPrincipial(profile);
    }

    /**
     * Выдача прав администратора текущему пользователю
     * <p>
     * Нужен для демонстрации
     */
    @Deprecated
    public void getAdmin() {
        final var authority = Authority.as(Authority.Role.ADMINISTRATOR)
                .from(Authority.Source.APPLICATION_AUTHORIZED_DEVICE)
                .to(Authority.Target.PIXPLAZE)
                .grant();
        Objects.requireNonNull(SecurityConfiguration.currentUser()).setAuthority(authority);
    }
}
