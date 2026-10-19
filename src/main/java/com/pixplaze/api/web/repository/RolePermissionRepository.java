package com.pixplaze.api.web.repository;

import lombok.AllArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

import static com.pixplaze.api.web.data.db.tables.RolePermissionTable.ROLE_PERMISSION;

@Repository
@AllArgsConstructor
public class RolePermissionRepository {
    private final DSLContext dslContext;

    /// Whole role → permission mapping: role code → permission codes. The table is small reference
    /// data ({@code R__insert_values_role_permission.sql}), read once into memory.
    public Map<String, List<String>> findAll() {
        return dslContext.select(ROLE_PERMISSION.ROLE_CODE, ROLE_PERMISSION.PERMISSION_CODE)
                .from(ROLE_PERMISSION)
                .fetchGroups(ROLE_PERMISSION.ROLE_CODE, ROLE_PERMISSION.PERMISSION_CODE);
    }
}
