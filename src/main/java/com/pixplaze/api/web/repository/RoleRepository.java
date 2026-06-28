package com.pixplaze.api.web.repository;

import com.pixplaze.api.web.data.db.tables.pojos.Role;
import lombok.AllArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static com.pixplaze.api.web.data.db.tables.RoleTable.ROLE;

@Repository
@AllArgsConstructor
public class RoleRepository {
    private final DSLContext dslContext;

    public List<Role> findAll() {
        return dslContext.select()
                .from(ROLE)
                .fetchInto(Role.class);
    }

    public Optional<Role> findByCode(String code) {
        return dslContext.select()
                .from(ROLE)
                .where(ROLE.CODE.eq(code))
                .fetchOptionalInto(Role.class);
    }

    public boolean existsByCode(String code) {
        return dslContext.fetchExists(
                dslContext.selectOne()
                        .from(ROLE)
                        .where(ROLE.CODE.eq(code))
        );
    }
}
