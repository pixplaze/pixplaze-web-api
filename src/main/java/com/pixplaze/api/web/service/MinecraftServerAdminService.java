package com.pixplaze.api.web.service;

import com.pixplaze.api.web.service.auth.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Административные операции над серверами. Вынесены отдельно, чтобы не вводить цикл
 * зависимостей {@link MinecraftServerService} ↔ {@link RefreshTokenService}.
 */
@Service
@RequiredArgsConstructor
public class MinecraftServerAdminService {
    private final MinecraftServerService minecraftServerService;
    private final RefreshTokenService refreshTokenService;

    /// Перманентный бан сервера по его identity: статус BANNED + отзыв всех refresh-токенов.
    /// Дальнейшая авторизация и ротация для этого id невозможны, с любого host/имени.
    @Transactional
    public void ban(Long serverId) {
        minecraftServerService.markBanned(serverId);
        refreshTokenService.revokeAllForServer(serverId);
    }
}
