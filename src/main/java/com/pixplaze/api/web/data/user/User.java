package com.pixplaze.api.web.data.user;

import jakarta.annotation.Nonnull;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User implements UserDetails {
    private Long id;
    private String name;
    private String email;
    private String password;
    private Role role = Role.ROLE_USER;

    @Override
    public @Nonnull Collection<? extends GrantedAuthority> getAuthorities() {
        if (role == null) {
            return List.of();
        }

        return List.of(new SimpleGrantedAuthority(role.name()));
//        return List.of(new SimpleGrantedAuthority(Role.ROLE_ADMIN.name()));
    }

    @Override
    public @Nonnull String getUsername() {
        return name;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
