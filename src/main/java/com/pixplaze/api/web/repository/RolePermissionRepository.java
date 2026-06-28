package com.pixplaze.api.web.repository;

import lombok.AllArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

import static com.pixplaze.api.web.data.db.tables.RolePermissionTable.ROLE_PERMISSION;

@Repository
@AllArgsConstructor
public class RolePermissionRepository {
    private final DSLContext dslContext;

    /// Fine-grained permission codes granted to a single role.
    public List<String> findPermissionCodesByRoleCode(String roleCode) {
        return dslContext.select(ROLE_PERMISSION.PERMISSION_CODE)
                .from(ROLE_PERMISSION)
                .where(ROLE_PERMISSION.ROLE_CODE.eq(roleCode))
                .fetch(ROLE_PERMISSION.PERMISSION_CODE);
    }

    /// Distinct union of permission codes across the given roles. A subject with multiple
    /// roles inherits the union of their permissions; `selectDistinct` collapses shared ones.
    public List<String> findPermissionCodesByRoleCodes(Collection<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return List.of();
        }

        return dslContext.selectDistinct(ROLE_PERMISSION.PERMISSION_CODE)
                .from(ROLE_PERMISSION)
                .where(ROLE_PERMISSION.ROLE_CODE.in(roleCodes))
                .fetch(ROLE_PERMISSION.PERMISSION_CODE);
    }
}
