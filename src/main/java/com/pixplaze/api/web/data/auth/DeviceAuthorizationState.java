package com.pixplaze.api.web.data.auth;

import com.pixplaze.api.ext.data.Authority;
import com.pixplaze.api.web.data.user.ApplicationClientPrincipal;

import java.util.Optional;

/**
 * Immutable snapshot of a single OAuth 2.0 Device Authorization Flow (RFC 8628) session.
 * <p>
 * Each state transition produces a brand-new instance via a copy-with mutation (the record itself
 * never changes); the owning {@link DeviceAuthorizationSession} swaps its reference to the latest
 * snapshot. The {@code status} field tracks the position in the flow:
 * {@link Status#PENDING PENDING} → {@link Status#APPROVED APPROVED} / {@link Status#DENIED DENIED}.
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li><b>{@link #pending pending(...)}</b> — created when the device first requests authorization.
 *       {@code clientId}, {@code userCode}, {@code deviceCodeHash}, {@code authority} and the raw
 *       {@code authorizationDetails} are populated here; {@code profile} is empty and {@code status}
 *       is {@code PENDING}.</li>
 *   <li><b>{@link #approved approved(...)}</b> — the human approves the request from an already
 *       authenticated web device. Fills {@code profile} with the approving principal and flips
 *       {@code status} to {@code APPROVED}. The strategy later reads {@code authorizationDetails} +
 *       {@code profile} to resolve the subject and mint tokens.</li>
 *   <li><b>{@link #denied denied()}</b> — the human rejects the request. {@code profile} stays empty,
 *       {@code status} becomes {@code DENIED}.</li>
 * </ol>
 *
 * <p>The subject principal is intentionally <em>not</em> stored here: it only exists after a
 * successful authorization and is built fresh by the strategy in {@code authorize(...)}. During the
 * flow the session carries just the raw {@code authorizationDetails} request payload.
 *
 * @param <A>                  type of the raw authorization-details payload (server / player / none)
 * @param clientId             opaque identifier of the device requesting authorization; supplied at
 *                             {@link #pending creation} and matched on every poll to detect context swaps.
 * @param userCode             short human-readable code the user enters on the verification page; set at
 *                             creation, used to look the session up during approval.
 * @param deviceCodeHash       hash of the long device code the requesting device polls with; set at
 *                             creation (only the hash is stored, never the raw code).
 * @param authority            the <em>unauthorized</em> requested authority (source + role + empty grant)
 *                             parsed from the {@code scope}; fixed at creation and used by the strategy as
 *                             the anti-escalation template for the authority it finally grants.
 * @param authorizationDetails the raw request payload the requesting device supplied (server/player info,
 *                             voucher, …); set at creation, consumed by the strategy to resolve and mint the
 *                             subject. Empty when the request carried no parseable details.
 * @param profile              the already-authenticated profile that approves the request; empty until
 *                             {@link #approved approval}, never populated on the {@code PENDING}/{@code DENIED} paths.
 * @param status               current position in the flow; drives the polling response.
 */
public record DeviceAuthorizationState<A>(
        String clientId,
        String userCode,
        String deviceCodeHash,
        Authority authority,
        Optional<A> authorizationDetails,
        Optional<ApplicationClientPrincipal> profile,
        Status status
) {

    /** Position of the session in the device flow; see the class lifecycle. */
    public enum Status {
        PENDING,
        APPROVED,
        DENIED;

        public String code() {
            return name().toLowerCase();
        }
    }

    /**
     * Initial snapshot created when a device starts the flow. Populates the request-side fields
     * ({@code clientId}, {@code userCode}, {@code deviceCodeHash}, {@code authority}, {@code authorizationDetails}),
     * leaves {@code profile} empty and sets {@code status} to {@link Status#PENDING}.
     */
    public static <A> DeviceAuthorizationState<A> pending(
            String clientId,
            String userCode,
            String deviceCodeHash,
            Authority authority,
            A authorizationDetails
    ) {
        return new DeviceAuthorizationState<>(clientId, userCode, deviceCodeHash, authority, Optional.ofNullable(authorizationDetails), Optional.empty(), Status.PENDING);
    }

    /**
     * Transition to {@link Status#DENIED} after the user rejects the request. Keeps the request-side
     * fields, clears {@code profile} (no approver) — the session is terminal and will be removed.
     */
    public DeviceAuthorizationState<A> denied() {
        return new DeviceAuthorizationState<>(clientId, userCode, deviceCodeHash, authority, authorizationDetails, Optional.empty(), Status.DENIED);
    }

    /**
     * Transition to {@link Status#APPROVED} after the user accepts the request. Records the approving
     * {@code profile}; the strategy then combines {@code authorizationDetails} + {@code profile} to
     * resolve the subject and issue the access/refresh tokens.
     */
    public DeviceAuthorizationState<A> approved(ApplicationClientPrincipal profile) {
        return new DeviceAuthorizationState<>(clientId, userCode, deviceCodeHash, authority, authorizationDetails, Optional.of(profile), Status.APPROVED);
    }

    /**
     * Projection that strips the secret/correlation fields ({@code clientId}, {@code userCode},
     * {@code deviceCodeHash}, {@code status}) for safe exposure outside the session machinery,
     * retaining only {@code authority} and {@code authorizationDetails}.
     */
    public DeviceAuthorizationState<A> safe() {
        return new DeviceAuthorizationState<>(null, null, null, authority, authorizationDetails, null, null);
    }
}
