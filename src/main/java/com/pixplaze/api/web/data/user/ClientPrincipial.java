package com.pixplaze.api.web.data.user;

import com.pixplaze.api.ext.data.Authority;
import jakarta.annotation.Nonnull;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClientPrincipial implements UserDetails {
    private Long id;
    private String name;
    private String email;
    private String password;
    private Authority authority = Authority.as(Authority.Role.USER)
            .from(Authority.Source.APPLICATION_AUTHORIZED_DEVICE)
            .unauthorized();

    @Override
    public @Nonnull Collection<? extends GrantedAuthority> getAuthorities() {
        return authority.describe()
                .stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
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
