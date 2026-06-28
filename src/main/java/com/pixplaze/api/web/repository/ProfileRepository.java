package com.pixplaze.api.web.repository;

import com.pixplaze.api.web.data.db.tables.pojos.Profile;
import com.pixplaze.api.web.exception.http.NotImplementedException;
import lombok.AllArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.pixplaze.api.web.data.db.tables.ProfileTable.PROFILE;

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

    public List<Profile> getUsers(Integer limit, Integer offset) {
        return dslContext.select()
                .from(PROFILE)
                .limit(limit)
                .offset(offset)
                .fetchInto(Profile.class);
    }

    public Profile update(Profile profile) {
        return dslContext.update(PROFILE)
                .set(PROFILE.EMAIL, profile.getEmail())
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
        throw new NotImplementedException();
    }

    public Optional<Profile> findByUsername(String username) {
        return Optional.ofNullable(
                dslContext.select().from(PROFILE)
                        .where(PROFILE.NAME.eq(username))
                        .fetchOneInto(Profile.class)
        );
    }

    public Optional<Profile> findById(Long id) {
        return dslContext.select().from(PROFILE)
                .where(PROFILE.ID.eq(id))
                .fetchOptionalInto(Profile.class);
    }

    public List<Profile> getAllByUsernameStartingWith(String username) {
        return dslContext.select().from(PROFILE)
                .where(PROFILE.NAME.like(username + "%"))
                .fetchInto(Profile.class);
    }
}
