package com.pixplaze.api.web.service;

import com.pixplaze.api.ext.data.player.MinecraftPlayerInfo;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftPlayer;
import com.pixplaze.api.web.repository.MinecraftPlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MinecraftPlayerService {
    private final MinecraftPlayerRepository minecraftPlayerRepository;

    public MinecraftPlayer create(MinecraftPlayer minecraftPlayer) {
        return minecraftPlayerRepository.create(minecraftPlayer);
    }

    /// Пакетно создаёт игроков без N+1; существующие игнорируются.
    public void createAll(Collection<MinecraftPlayer> players) {
        minecraftPlayerRepository.createAll(players);
    }

    /// Привязка игрока к профилю: no-op если уже привязан к тому же профилю,
    /// исключение — если к другому.
    public void linkProfile(UUID playerUuid, Long profileId) {
        minecraftPlayerRepository.linkProfile(playerUuid, profileId);
    }

    public MinecraftPlayer createIfNotExist(MinecraftPlayer minecraftPlayer) {
        return minecraftPlayerRepository.createIfNotExist(minecraftPlayer);
    }

    /// Апсерт игрока на device-входе: создаёт или освежает username/голову скина.
    public MinecraftPlayer upsert(MinecraftPlayer minecraftPlayer) {
        return minecraftPlayerRepository.upsert(minecraftPlayer);
    }

    /// Связанные с профилем игроки (uuid/имя/голова скина/флаг оператора) для веб-приложения.
    public List<MinecraftPlayerInfo> findLinkedByProfileId(Long profileId) {
        return minecraftPlayerRepository.findLinkedByProfileId(profileId);
    }

    /// Хосты серверов, где состоят связанные с профилем игроки (для targets/aud токена профиля).
    public List<String> findServerHostsByProfileId(Long profileId) {
        return minecraftPlayerRepository.findServerHostsByProfileId(profileId);
    }

    public boolean existByUuid(UUID uuid) {
        return minecraftPlayerRepository.existsByUuid(uuid);
    }

    public Optional<MinecraftPlayer> findByUuid(UUID uuid) {
        return minecraftPlayerRepository.findByUuid(uuid);
    }

    /// Привязан ли игрок к какому-либо профилю.
    public boolean isProfileLinked(UUID uuid) {
        return minecraftPlayerRepository.isProfileLinked(uuid);
    }

    public Optional<UUID> findUuidByProfileId(Long profileId) {
        return minecraftPlayerRepository.findUuidByProfileId(profileId);
    }
}
