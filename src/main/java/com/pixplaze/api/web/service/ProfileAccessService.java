package com.pixplaze.api.web.service;

import com.pixplaze.api.web.data.user.ApplicationClientPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Проверки доступа профиля для {@code @PreAuthorize}: {@code @profileAccess.hasLinkedPlayer(principal)}.
 * Данные для проверок — из БД, а не из токена: в токене профиля только системные роли.
 */
@Slf4j
@Service("profileAccess")
@RequiredArgsConstructor
public class ProfileAccessService {

    private final MinecraftPlayerService minecraftPlayerService;

    /// {@code true}, если принципал — профиль, к которому привязан хотя бы один игрок.
    public boolean hasLinkedPlayer(Object principal) {
        final var linked = principal instanceof ApplicationClientPrincipal profile
                && profile.getId() != null
                && minecraftPlayerService.hasLinkedPlayer(profile.getId());
        if (!linked) {
            log.debug("Access refused: {} has no linked player", principal instanceof ApplicationClientPrincipal profile
                    ? "profile #" + profile.getId() : "not a profile principal");
        }
        return linked;
    }
}
