package com.pixplaze.api.web.repository;

import com.pixplaze.api.web.data.db.tables.pojos.Profile;
import com.pixplaze.api.web.exception.http.NotImplementedException;
import com.pixplaze.api.web.repository.ProfileFriendRepository.ProfileRef;
import lombok.AllArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Records;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static com.pixplaze.api.web.data.db.tables.MinecraftPlayerProfileTable.MINECRAFT_PLAYER_PROFILE;
import static com.pixplaze.api.web.data.db.tables.MinecraftPlayerTable.MINECRAFT_PLAYER;
import static com.pixplaze.api.web.data.db.tables.ProfileRoleTable.PROFILE_ROLE;
import static com.pixplaze.api.web.data.db.tables.ProfileTable.PROFILE;
import static org.jooq.impl.DSL.lower;
import static org.jooq.impl.DSL.select;

@Repository
@AllArgsConstructor
public class ProfileRepository {
    private DSLContext dslContext;

    public Profile create(Profile profile) {
        final var id = dslContext.insertInto(PROFILE)
                .set(PROFILE.NAME, profile.getName())
                .set(PROFILE.EMAIL, profile.getEmail())
                .set(PROFILE.PASSWORD, profile.getPassword())
                .returning(PROFILE.ID)
                .fetchOne(PROFILE.ID);
        profile.setId(id);
        return profile;
    }

    public List<Profile> getProfiles(Integer limit, Integer offset) {
        return dslContext.select()
                .from(PROFILE)
                .limit(limit)
                .offset(offset)
                .fetchInto(Profile.class);
    }

    public Profile update(Profile profile) {
        return dslContext.update(PROFILE)
                .set(PROFILE.NAME, profile.getName())
                .set(PROFILE.EMAIL, profile.getEmail())
                .set(PROFILE.PASSWORD, profile.getPassword())
                .set(PROFILE.IS_ONLINE_HIDDEN, profile.getIsOnlineHidden())
                .where(PROFILE.ID.eq(profile.getId()))
                .returning(PROFILE)
                .fetchOneInto(Profile.class);
    }

    public void delete(long id) {
        dslContext.delete(PROFILE)
                .where(PROFILE.ID.eq(id))
                .execute();
    }

    public boolean existsByUsername(String username) {
        return dslContext.fetchExists(
                dslContext.selectOne()
                        .from(PROFILE)
                        .where(PROFILE.NAME.eq(username))
        );
    }

    public boolean existsByEmail(String email) {
        return dslContext.fetchExists(PROFILE, PROFILE.EMAIL.equalIgnoreCase(email));
    }

    public Optional<Profile> findByUsername(String username) {
        return Optional.ofNullable(
                dslContext.select().from(PROFILE)
                        .where(PROFILE.NAME.eq(username))
                        .fetchOneInto(Profile.class)
        );
    }

    /// Выданные профилю роли сверх выводимых ({@code profile_role}), коды {@link com.pixplaze.api.ext.data.auth.Authority.Role}.
    public List<String> findGrantedRoleCodes(Long profileId) {
        return dslContext.select(PROFILE_ROLE.ROLE_CODE)
                .from(PROFILE_ROLE)
                .where(PROFILE_ROLE.PROFILE_ID.eq(profileId))
                .fetch(PROFILE_ROLE.ROLE_CODE);
    }

    public Optional<Profile> findById(Long id) {
        return dslContext.select().from(PROFILE)
                .where(PROFILE.ID.eq(id))
                .fetchOptionalInto(Profile.class);
    }

    /// Профили, у которых имя или ник привязанного игрока начинается с `query` без учёта регистра, кроме
    /// `excludedId`; по имени, не больше `limit`. Каждый источник — отдельный подзапрос по своему индексу
    /// (`lower(...) text_pattern_ops`), объединение — через UNION: `OR` поверх соединения индексы не использует.
    public List<ProfileRef> search(String query, Long excludedId, int limit) {
        final var pattern = likePrefix(query);
        final var byName = select(PROFILE.ID).from(PROFILE)
                .where(lower(PROFILE.NAME).like(pattern, LIKE_ESCAPE));
        final var byPlayer = select(MINECRAFT_PLAYER_PROFILE.PROFILE_ID).from(MINECRAFT_PLAYER)
                .join(MINECRAFT_PLAYER_PROFILE).on(MINECRAFT_PLAYER_PROFILE.MINECRAFT_PLAYER_UUID.eq(MINECRAFT_PLAYER.UUID))
                .where(lower(MINECRAFT_PLAYER.USERNAME).like(pattern, LIKE_ESCAPE));
        return dslContext.select(PROFILE.ID, PROFILE.NAME).from(PROFILE)
                .where(PROFILE.ID.in(byName.union(byPlayer)))
                .and(PROFILE.ID.ne(excludedId))
                .orderBy(PROFILE.NAME)
                .limit(limit)
                .fetch(Records.mapping(ProfileRef::new));
    }

    private static final char LIKE_ESCAPE = '!';

    /// Шаблон LIKE «начинается с» в нижнем регистре; `%` и `_` (бывает в никах) ищутся буквально.
    static String likePrefix(String query) {
        final var escaped = query.toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return escaped + "%";
    }
}
