package com.pixplaze.api.web.repository;

import com.pixplaze.api.web.data.user.Role;
import com.pixplaze.api.web.data.user.User;
import com.pixplaze.api.web.exception.http.NotImplementedException;
import com.pixplaze.web.api.data.db.tables.Users;
import lombok.AllArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@AllArgsConstructor
public class UserRepository {
    private DSLContext dslContext;

    public User create(User user) {
        final var USERS = Users.USERS;
        final var id = dslContext.insertInto(USERS)
                .set(USERS.NAME, user.getName())
                .set(USERS.EMAIL, user.getEmail())
                .set(USERS.PASSWORD, user.getPassword())
                .returning(USERS.ID)
                .fetchOne(USERS.ID);
        user.setId(id);
        user.setRole(Role.ROLE_USER);
        return user;
    }

    public List<User> getUsers(Integer limit, Integer offset) {
        final var USERS = Users.USERS;
        return dslContext.select()
                .from(USERS)
                .limit(limit)
                .offset(offset)
                .fetchInto(User.class);
    }

    public User update(User user) {
        final var USERS = Users.USERS;
        final var updatedUser = dslContext.update(USERS)
                .set(USERS.EMAIL, user.getEmail())
                .where(USERS.ID.eq(user.getId()))
                .returning(USERS)
                .fetchOneInto(User.class);

        updatedUser.setRole(Role.ROLE_USER);

        return updatedUser;
    }

    public void delete(long id) {
        final var USERS = Users.USERS;
        dslContext.delete(USERS)
                .where(USERS.ID.eq(id))
                .execute();
    }

    public boolean existsByUsername(String username) {
        final var USERS = Users.USERS;
        return dslContext.fetchExists(
                dslContext.selectOne()
                        .from(USERS)
                        .where(USERS.NAME.eq(username))
        );
    }

    public boolean existsByEmail(String email) {
        throw new NotImplementedException();
    }

    public Optional<User> findByUsername(String username) {
        final var USERS = Users.USERS;
        return Optional.ofNullable(
                dslContext.select().from(USERS)
                        .where(USERS.NAME.eq(username))
                        .fetchOneInto(User.class)
        );
    }

    public List<User> getAllByUsernameStartingWith(String username) {
        final var USERS = Users.USERS;
        return dslContext.select().from(USERS)
                .where(USERS.NAME.like(username + "%"))
                .fetchInto(User.class);
    }
}
