package com.pixplaze.api.web.data.auth;

import com.pixplaze.api.ext.data.Authority;
import com.pixplaze.api.web.data.user.ClientPrincipial;

import java.util.Optional;

public record DeviceAuthorizationState<D>(
        String clientId,
        String userCode,
        String deviceCodeHash,
        Authority authority,
        Optional<D> details,
        Optional<ClientPrincipial> profile,
        Status status
) {

    public enum Status {
        PENDING,
        APPROVED,
        DENIED;

        public String code() {
            return name().toLowerCase();
        }
    }

    public static <D> DeviceAuthorizationState<D> pending(
            String clientId,
            String userCode,
            String deviceCodeHash,
            Authority authority,
            D details
    ) {
        return new DeviceAuthorizationState<>(clientId, userCode, deviceCodeHash, authority, Optional.ofNullable(details), Optional.empty(),  Status.PENDING);
    }

    public DeviceAuthorizationState<D> denied() {
        return new DeviceAuthorizationState<>(clientId, userCode, deviceCodeHash, authority, details, Optional.empty(), Status.DENIED);
    }

    public DeviceAuthorizationState<D> approved(ClientPrincipial clientPrincipial) {
        return new DeviceAuthorizationState<>(clientId, userCode, deviceCodeHash, authority, details, Optional.of(clientPrincipial), Status.APPROVED);
    }
}
