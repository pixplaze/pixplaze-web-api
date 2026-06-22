package com.pixplaze.api.web.service;

import com.pixplaze.api.web.data.db.tables.pojos.MinecraftPlayer;
import com.pixplaze.api.web.repository.MinecraftPlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MinecraftPlayerService {
    private final MinecraftPlayerRepository minecraftPlayerRepository;

    public MinecraftPlayer create(MinecraftPlayer minecraftPlayer) {
        return minecraftPlayerRepository.create(minecraftPlayer);
    }

    public MinecraftPlayer createIfNotExist(MinecraftPlayer minecraftPlayer) {
        return minecraftPlayerRepository.createIfNotExist(minecraftPlayer);
    }

    public boolean existByUuid(UUID uuid) {
        return minecraftPlayerRepository.existsByUuid(uuid);
    }

    public void linkWithProfile(Long profileId, UUID playerUuid) {
        minecraftPlayerRepository.linkWithProfile(profileId, playerUuid);
    }
}
