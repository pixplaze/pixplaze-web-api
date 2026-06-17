package com.pixplaze.api.web.service;

import com.pixplaze.api.web.data.user.Profile;
import com.pixplaze.api.web.data.user.Role;
import com.pixplaze.api.web.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    /**
     * Создание пользователя
     * @return созданный пользователь
     */
    public Profile create(Profile profile) {
        return userRepository.create(profile);
    }

    public List<Profile> getUsers(Integer limit, Integer offset) {
        return userRepository.getUsers(limit, offset);
    }

    public List<Profile> getUsers(String username) {
        return userRepository.getAllByUsernameStartingWith(username);
    }

    /**
     * Получение пользователя по имени пользователя
     *
     * @return пользователь
     */
    public Profile getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден"));
    }

    /**
     * Получение пользователя по имени пользователя
     * <p>
     * Нужен для Spring Security
     *
     * @return пользователь
     */
    public UserDetailsService getUserDetailsService() {
        return this::getUserByUsername;
    }

    /**
     * Получение текущего пользователя
     *
     * @return текущий пользователь
     */
    public Profile getCurrentUser() {
        // Получение имени пользователя из контекста Spring Security
        return (Profile) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }


    /**
     * Выдача прав администратора текущему пользователю
     * <p>
     * Нужен для демонстрации
     */
    @Deprecated
    public void getAdmin() {
        var user = getCurrentUser();
        user.setRole(Role.ROLE_ADMIN);
    }
}
