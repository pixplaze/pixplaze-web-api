package com.pixplaze.api.web.repository;

import com.pixplaze.api.web.data.db.tables.pojos.MinecraftPlayer;
import com.pixplaze.api.web.data.user.ClientPrincipial;
import com.pixplaze.api.web.exception.MinecraftPlayerAlreadyOwnedException;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.pixplaze.api.web.data.db.Tables.*;

@Repository
@RequiredArgsConstructor
public class MinecraftPlayerRepository {
    private final DSLContext dslContext;

    public MinecraftPlayer create(MinecraftPlayer player) {
        return dslContext.insertInto(MINECRAFT_PLAYER)
                .set(MINECRAFT_PLAYER.UUID, player.getUuid())
                .set(MINECRAFT_PLAYER.USERNAME, player.getUsername())
                .returning(MINECRAFT_PLAYER.UUID, MINECRAFT_PLAYER.USERNAME, MINECRAFT_PLAYER.CREATED_AT)
                .fetchOneInto(MinecraftPlayer.class);
    }

    public MinecraftPlayer createIfNotExist(MinecraftPlayer player) {
        final var existingPlayer = findByUuid(player.getUuid()).orElse(null);

        if (existingPlayer != null) {
            return existingPlayer;
        }

        final var createdPlayer = dslContext.insertInto(MINECRAFT_PLAYER)
                .set(MINECRAFT_PLAYER.UUID, player.getUuid())
                .set(MINECRAFT_PLAYER.USERNAME, player.getUsername())
                .onConflict(MINECRAFT_PLAYER.UUID)
                .doNothing()
                .returning(MINECRAFT_PLAYER.UUID, MINECRAFT_PLAYER.USERNAME, MINECRAFT_PLAYER.CREATED_AT)
                .fetchOneInto(MinecraftPlayer.class);

        if (createdPlayer != null) {
            return createdPlayer;
        }

        // Проиграли гонку: строку вставил конкурентный поток между SELECT и INSERT
        return findByUuid(player.getUuid()).orElseThrow(IllegalStateException::new);
    }

    public Optional<MinecraftPlayer> findByUuid(UUID uuid) {
        return dslContext.select()
                .from(MINECRAFT_PLAYER)
                .where(MINECRAFT_PLAYER.UUID.eq(uuid))
                .fetchOptionalInto(MinecraftPlayer.class);
    }

    public List<MinecraftPlayer> findAll() {
        return dslContext.select()
                .from(MINECRAFT_PLAYER)
                .fetchInto(MinecraftPlayer.class);
    }

    public boolean existsByUuid(UUID uuid) {
        return dslContext.fetchExists(
                dslContext.selectOne()
                        .from(MINECRAFT_PLAYER)
                        .where(MINECRAFT_PLAYER.UUID.eq(uuid))
        );
    }

    public MinecraftPlayer update(MinecraftPlayer player) {
        return dslContext.update(MINECRAFT_PLAYER)
                .set(MINECRAFT_PLAYER.USERNAME, player.getUsername())
                .where(MINECRAFT_PLAYER.UUID.eq(player.getUuid()))
                .returning(MINECRAFT_PLAYER.UUID, MINECRAFT_PLAYER.USERNAME, MINECRAFT_PLAYER.CREATED_AT)
                .fetchOneInto(MinecraftPlayer.class);
    }

    public void delete(UUID uuid) {
        dslContext.delete(MINECRAFT_PLAYER)
                .where(MINECRAFT_PLAYER.UUID.eq(uuid))
                .execute();
    }

    public Optional<ClientPrincipial> getPlayerProfile(UUID uuid) {
        return dslContext.select(PROFILE.ID, PROFILE.NAME, PROFILE.EMAIL, PROFILE.PASSWORD)
                .from(PROFILE)
                .join(MINECRAFT_PLAYER_PROFILE).on(MINECRAFT_PLAYER_PROFILE.PROFILE_ID.eq(PROFILE.ID))
                .where(MINECRAFT_PLAYER_PROFILE.MINECRAFT_PLAYER_UUID.eq(uuid))
                .fetchOptionalInto(ClientPrincipial.class);
    }

    public void linkWithProfile(Long id, UUID uuid) {
        final var rows = dslContext.insertInto(MINECRAFT_PLAYER_PROFILE)
                .set(MINECRAFT_PLAYER_PROFILE.PROFILE_ID, Objects.requireNonNull(id, "Profile 'id' must not be null!"))
                .set(MINECRAFT_PLAYER_PROFILE.MINECRAFT_PLAYER_UUID, Objects.requireNonNull(uuid, "Player uuid must be not null!"))
                .onConflict(MINECRAFT_PLAYER_PROFILE.MINECRAFT_PLAYER_UUID)
                .doNothing()
                .execute();

        if (rows == 0) {
            throw new MinecraftPlayerAlreadyOwnedException(uuid);
        }
    }
}
