package com.pixplaze.api.web.data.user;

public enum Role {
    ROLE_USER,
    ROLE_ADMIN;

    public static Role of(String name) {
        if (name == null) {
            return ROLE_USER;
        }

        return Role.valueOf(name);
    }
}
