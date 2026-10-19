package com.pixplaze.api.web.service;

import com.pixplaze.api.web.data.dto.ProfilePlayerInfo;
import com.pixplaze.api.web.exception.http.NotFoundException;
import com.pixplaze.api.web.repository.MinecraftServerRepository;
import com.pixplaze.api.web.repository.ProfilePlayerRepository;
import com.pixplaze.api.web.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Игроки профиля в настройках: список с серверами, переключатель «основной» и отвязка.
 */
@Service
@RequiredArgsConstructor
public class ProfilePlayerService {

    private final ProfilePlayerRepository profilePlayerRepository;
    private final MinecraftServerRepository minecraftServerRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public List<ProfilePlayerInfo> findPlayers(Long profileId) {
        return profilePlayerRepository.findWithServers(profileId);
    }

    /// Делает игрока основным для профиля на сервере.
    /// @throws NotFoundException игрок не принадлежит профилю или не состоит на сервере
    public void setPrimary(Long profileId, UUID playerUuid, Long serverId) {
        if (!minecraftServerRepository.setPrimaryPlayer(profileId, serverId, playerUuid)) {
            throw new NotFoundException("Player %s of the profile is not on server %d".formatted(playerUuid, serverId));
        }
    }

    /// Отвязывает игрока: связь с профилем удаляется, refresh-токены игрока отзываются (в игре он
    /// заморозится на ближайшем refresh), основной игрок профиля на его серверах назначается заново.
    /// @throws NotFoundException игрок не принадлежит профилю
    @Transactional
    public void unlink(Long profileId, UUID playerUuid) {
        final var serverIds = profilePlayerRepository.findServerIds(playerUuid);
        if (!profilePlayerRepository.unlink(profileId, playerUuid)) {
            throw new NotFoundException("Player %s is not linked to the profile".formatted(playerUuid));
        }
        refreshTokenRepository.revokeAllForPlayer(playerUuid);
        serverIds.forEach(serverId -> minecraftServerRepository.ensurePrimaryPlayer(profileId, serverId));
    }
}
