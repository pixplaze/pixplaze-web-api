package com.pixplaze.api.web.data.auth;

import com.pixplaze.api.ext.data.auth.Authority;
import com.pixplaze.api.ext.data.oauth.OAuthError;
import com.pixplaze.api.web.exception.auth.DeviceAuthorizationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScopesTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void emptyScopeIsApplicationUser(String scope) {
        final var authority = Scopes.toAuthority(scope);

        assertTrue(authority.is(Authority.Role.USER));
        assertTrue(authority.from(Authority.Source.APPLICATION_AUTHORIZED_DEVICE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MAD:MINECRAFT_SERVER", "MAD:RMCS", "mad:minecraft_player", "MAD:MINECRAFT_OPERATOR", "AAD:USER"})
    void acceptsSupportedScopes(String scope) {
        Scopes.toAuthority(scope);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "MAD", "MAD:", ":USER", "MAD:MINECRAFT_PLAYER:X",
            "XYZ:USER", "MAD:FOO",
            "MAD:USER", "AAD:ADMIN", "AAD:SYSTEM", "AAD:MINECRAFT_SERVER", "NAD:USER",
            "MAD:MINECRAFT_PLAYER AAD:USER"
    })
    void rejectsMalformedOrUnsupportedScopes(String scope) {
        final var exception = assertThrows(DeviceAuthorizationException.class, () -> Scopes.toAuthority(scope));

        assertEquals(OAuthError.INVALID_SCOPE, exception.getError());
    }
}
