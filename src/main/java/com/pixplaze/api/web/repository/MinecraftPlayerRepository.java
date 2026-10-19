package com.pixplaze.api.web.repository;

import com.pixplaze.api.ext.data.player.MinecraftPlayerInfo;
import com.pixplaze.api.web.data.db.tables.pojos.MinecraftPlayer;
import com.pixplaze.api.web.data.user.ApplicationClientPrincipal;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.util.*;

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

    /// Пакетная вставка игроков одним INSERT (без N+1). Существующие (по uuid) игнорируются.
    public void createAll(Collection<MinecraftPlayer> players) {
        if (players.isEmpty()) {
            return;
        }
        var insert = dslContext.insertInto(MINECRAFT_PLAYER, MINECRAFT_PLAYER.UUID, MINECRAFT_PLAYER.USERNAME);
        for (final var player : players) {
            insert = insert.values(player.getUuid(), player.getUsername());
        }
        insert.onConflict(MINECRAFT_PLAYER.UUID)
                .doNothing()
                .execute();
    }

    /// Апсерт игрока: создаёт или, если игрок уже есть (по uuid), обновляет username и голову скина.
    /// Используется на device-входе игрока — повторный вход освежает картинку.
    public MinecraftPlayer upsert(MinecraftPlayer player) {
        return dslContext.insertInto(MINECRAFT_PLAYER)
                .set(MINECRAFT_PLAYER.UUID, player.getUuid())
                .set(MINECRAFT_PLAYER.USERNAME, player.getUsername())
                .set(MINECRAFT_PLAYER.SKIN_HEAD, player.getSkinHead())
                .onConflict(MINECRAFT_PLAYER.UUID)
                .doUpdate()
                .set(MINECRAFT_PLAYER.USERNAME, player.getUsername())
                .set(MINECRAFT_PLAYER.SKIN_HEAD, player.getSkinHead())
                .returning(MINECRAFT_PLAYER.UUID, MINECRAFT_PLAYER.USERNAME, MINECRAFT_PLAYER.SKIN_HEAD, MINECRAFT_PLAYER.CREATED_AT)
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

    public Optional<ApplicationClientPrincipal> getPlayerProfile(UUID uuid) {
        return dslContext.select(PROFILE.ID, PROFILE.NAME, PROFILE.EMAIL, PROFILE.PASSWORD)
                .from(PROFILE)
                .join(MINECRAFT_PLAYER_PROFILE).on(MINECRAFT_PLAYER_PROFILE.PROFILE_ID.eq(PROFILE.ID))
                .where(MINECRAFT_PLAYER_PROFILE.MINECRAFT_PLAYER_UUID.eq(uuid))
                .fetchOptionalInto(ApplicationClientPrincipal.class);
    }

    /// Связанные с профилем игроки для веб-приложения: uuid, имя, голова скина и флаг оператора
    /// (true, если игрок — оператор хотя бы на одном сервере). Остальные поля {@link MinecraftPlayerInfo}
    /// не заполняются (в web не нужны).
    public List<MinecraftPlayer> findLinkedByProfileId(Long profileId) {
        final var isOperator = DSL.field(DSL.exists(
                dslContext.selectOne()
                        .from(MINECRAFT_SERVER_PLAYER)
                        .where(MINECRAFT_SERVER_PLAYER.MINECRAFT_PLAYER_UUID.eq(MINECRAFT_PLAYER.UUID))
                        .and(MINECRAFT_SERVER_PLAYER.IS_OPERATOR.eq(true))
        ));

        return dslContext.select(
                        MINECRAFT_PLAYER.UUID,
                        MINECRAFT_PLAYER.USERNAME,
                        MINECRAFT_PLAYER.SKIN_HEAD,
                        isOperator.as("is_operator"))
                .from(MINECRAFT_PLAYER)
                .join(MINECRAFT_PLAYER_PROFILE).on(MINECRAFT_PLAYER_PROFILE.MINECRAFT_PLAYER_UUID.eq(MINECRAFT_PLAYER.UUID))
                .where(MINECRAFT_PLAYER_PROFILE.PROFILE_ID.eq(profileId))
                .fetchInto(MinecraftPlayer.class);
    }

    /// Id серверов, на которых состоят связанные с профилем игроки (любое членство, не только
    /// операторское). Идут в targets (aud) access-токена профиля, чтобы MC-сервер принимал его при
    /// веб-взаимодействии. Членство наполняется при device-входе игрока и регистрации сервера.
    public List<Long> findServerIdsByProfileId(Long profileId) {
        return dslContext.selectDistinct(MINECRAFT_SERVER_PLAYER.MINECRAFT_SERVER_ID)
                .from(MINECRAFT_PLAYER_PROFILE)
                .join(MINECRAFT_SERVER_PLAYER).on(MINECRAFT_SERVER_PLAYER.MINECRAFT_PLAYER_UUID.eq(MINECRAFT_PLAYER_PROFILE.MINECRAFT_PLAYER_UUID))
                .where(MINECRAFT_PLAYER_PROFILE.PROFILE_ID.eq(profileId))
                .fetch(MINECRAFT_SERVER_PLAYER.MINECRAFT_SERVER_ID);
    }

    /// Время выхода из сети для игроков, привязанных к профилю: «был в сети N минут назад» для друзей.
    /// Непривязанные игроки не пишутся.
    public void markLastSeen(Collection<UUID> playerUuids) {
        if (playerUuids.isEmpty()) {
            return;
        }
        dslContext.update(MINECRAFT_PLAYER)
                .set(MINECRAFT_PLAYER.LAST_SEEN_AT, DSL.currentOffsetDateTime())
                .where(MINECRAFT_PLAYER.UUID.in(playerUuids))
                .and(DSL.exists(dslContext.selectOne()
                        .from(MINECRAFT_PLAYER_PROFILE)
                        .where(MINECRAFT_PLAYER_PROFILE.MINECRAFT_PLAYER_UUID.eq(MINECRAFT_PLAYER.UUID))))
                .execute();
    }

    /// Ник известного игрока из снимка heartbeat. Пишется только изменение; неизвестный игрок не создаётся.
    public void updateUsername(UUID playerUuid, String username) {
        PartialUpdate.of(dslContext, MINECRAFT_PLAYER)
                .set(MINECRAFT_PLAYER.USERNAME, username)
                .execute(MINECRAFT_PLAYER.UUID.eq(playerUuid), MinecraftPlayer.class);
    }

    /// Есть ли у профиля хотя бы один привязанный игрок.
    public boolean hasLinkedPlayer(Long profileId) {
        return dslContext.fetchExists(
                dslContext.selectOne()
                        .from(MINECRAFT_PLAYER_PROFILE)
                        .where(MINECRAFT_PLAYER_PROFILE.PROFILE_ID.eq(profileId))
        );
    }

    /** UUID игрока, привязанного к профилю. Если игроков несколько — берём последнего привязанного. */
    public Optional<UUID> findUuidByProfileId(Long profileId) {
        return dslContext.select(MINECRAFT_PLAYER_PROFILE.MINECRAFT_PLAYER_UUID)
                .from(MINECRAFT_PLAYER_PROFILE)
                .where(MINECRAFT_PLAYER_PROFILE.PROFILE_ID.eq(profileId))
                .orderBy(MINECRAFT_PLAYER_PROFILE.LINKED_AT.desc())
                .limit(1)
                .fetchOptional(MINECRAFT_PLAYER_PROFILE.MINECRAFT_PLAYER_UUID);
    }

    /// Привязан ли игрок именно к этому профилю.
    public boolean isProfileOwnsPlayer(Long profileId, UUID playerUuid) {
        return dslContext.fetchExists(
                dslContext.selectOne()
                        .from(MINECRAFT_PLAYER_PROFILE)
                        .where(MINECRAFT_PLAYER_PROFILE.MINECRAFT_PLAYER_UUID.eq(playerUuid))
                        .and(MINECRAFT_PLAYER_PROFILE.PROFILE_ID.eq(profileId))
        );
    }

    /// Привязан ли игрок хоть к какому-нибудь профилю (игрок не может быть привязан к нескольким).
    public boolean isProfileLinked(UUID playerUuid) {
        return dslContext.fetchExists(
                dslContext.selectOne()
                        .from(MINECRAFT_PLAYER_PROFILE)
                        .where(MINECRAFT_PLAYER_PROFILE.MINECRAFT_PLAYER_UUID.eq(playerUuid))
        );
    }

    /// Привязывает игрока к профилю идемпотентно: если игрок уже привязан к ТОМУ ЖЕ профилю —
    /// ничего не делает; если не привязан — создаёт связь. {@code false} — игрок уже привязан к
    /// ДРУГОМУ профилю, и связь не изменена.
    public boolean linkProfile(UUID playerUuid, Long profileId) {
        Objects.requireNonNull(playerUuid, "'playerUuid' must not be null!");
        Objects.requireNonNull(profileId, "'profileId' must not be null!");

        final var ownerProfileId = dslContext.insertInto(MINECRAFT_PLAYER_PROFILE)
                .set(MINECRAFT_PLAYER_PROFILE.PROFILE_ID, profileId)
                .set(MINECRAFT_PLAYER_PROFILE.MINECRAFT_PLAYER_UUID, playerUuid)
                .onConflict(MINECRAFT_PLAYER_PROFILE.MINECRAFT_PLAYER_UUID)
                .doUpdate()
                .set(MINECRAFT_PLAYER_PROFILE.PROFILE_ID, MINECRAFT_PLAYER_PROFILE.PROFILE_ID)
                .returning(MINECRAFT_PLAYER_PROFILE.PROFILE_ID)
                .fetchOne(MINECRAFT_PLAYER_PROFILE.PROFILE_ID);

        return profileId.equals(ownerProfileId);
    }
}
