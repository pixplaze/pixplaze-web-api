package com.pixplaze.api.web.service.auth;

import com.pixplaze.api.web.mapper.ProfileMapper;
import com.pixplaze.api.web.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/// Загрузка профиля по имени для проверки пароля при входе ({@code DaoAuthenticationProvider}).
/// Authority здесь не нужна: токены выпускаются уже по профилю из БД в {@link AuthorizationService#signIn}.
@Service
@RequiredArgsConstructor
public class ProfileUserDetailsService implements UserDetailsService {
    private final ProfileRepository profileRepository;
    private final ProfileMapper profileMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return profileRepository.findByUsername(username)
                .map(profileMapper::toApplicationClientPrincipal)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден"));
    }
}
