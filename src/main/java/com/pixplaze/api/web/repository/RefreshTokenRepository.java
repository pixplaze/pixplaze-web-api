package com.pixplaze.api.web.repository;

import com.pixplaze.api.web.data.db.tables.pojos.RefreshToken;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static com.pixplaze.api.web.data.db.Tables.REFRESH_TOKEN;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {
    private final DSLContext dslContext;

    public RefreshToken create(RefreshToken token) {
        return dslContext.insertInto(REFRESH_TOKEN)
                .set(REFRESH_TOKEN.TOKEN_HASH, token.getTokenHash())
                .set(REFRESH_TOKEN.SUBJECT_TYPE, token.getSubjectType())
                .set(REFRESH_TOKEN.PROFILE_ID, token.getProfileId())
                .set(REFRESH_TOKEN.MINECRAFT_SERVER_ID, token.getMinecraftServerId())
                .set(REFRESH_TOKEN.MINECRAFT_PLAYER_UUID, token.getMinecraftPlayerUuid())
                .set(REFRESH_TOKEN.AUTH_SOURCE, token.getAuthSource())
                .set(REFRESH_TOKEN.AUTH_ROLES, token.getAuthRoles())
                .set(REFRESH_TOKEN.EXPIRES_AT, token.getExpiresAt())
                .returning()
                .fetchOneInto(RefreshToken.class);
    }

    public Optional<RefreshToken> findByHash(String tokenHash) {
        return dslContext.select()
                .from(REFRESH_TOKEN)
                .where(REFRESH_TOKEN.TOKEN_HASH.eq(tokenHash))
                .fetchOptionalInto(RefreshToken.class);
    }

    public void markRotated(Long id, Long replacedById) {
        final var now = OffsetDateTime.now();
        dslContext.update(REFRESH_TOKEN)
                .set(REFRESH_TOKEN.REVOKED_AT, now)
                .set(REFRESH_TOKEN.LAST_USED_AT, now)
                .set(REFRESH_TOKEN.REPLACED_BY, replacedById)
                .where(REFRESH_TOKEN.ID.eq(id))
                .execute();
    }

    public void revokeByHash(String tokenHash) {
        dslContext.update(REFRESH_TOKEN)
                .set(REFRESH_TOKEN.REVOKED_AT, OffsetDateTime.now())
                .where(REFRESH_TOKEN.TOKEN_HASH.eq(tokenHash))
                .and(REFRESH_TOKEN.REVOKED_AT.isNull())
                .execute();
    }

    public void revokeAllForProfile(Long profileId) {
        dslContext.update(REFRESH_TOKEN)
                .set(REFRESH_TOKEN.REVOKED_AT, OffsetDateTime.now())
                .where(REFRESH_TOKEN.PROFILE_ID.eq(profileId))
                .and(REFRESH_TOKEN.REVOKED_AT.isNull())
                .execute();
    }

    public void revokeAllForServer(Long minecraftServerId) {
        dslContext.update(REFRESH_TOKEN)
                .set(REFRESH_TOKEN.REVOKED_AT, OffsetDateTime.now())
                .where(REFRESH_TOKEN.MINECRAFT_SERVER_ID.eq(minecraftServerId))
                .and(REFRESH_TOKEN.REVOKED_AT.isNull())
                .execute();
    }

    public void revokeAllForPlayer(UUID minecraftPlayerUuid) {
        dslContext.update(REFRESH_TOKEN)
                .set(REFRESH_TOKEN.REVOKED_AT, OffsetDateTime.now())
                .where(REFRESH_TOKEN.MINECRAFT_PLAYER_UUID.eq(minecraftPlayerUuid))
                .and(REFRESH_TOKEN.REVOKED_AT.isNull())
                .execute();
    }
}
