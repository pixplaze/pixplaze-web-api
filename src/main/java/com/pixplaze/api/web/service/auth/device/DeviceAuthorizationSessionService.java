package com.pixplaze.api.web.service.auth.device;

import com.pixplaze.api.ext.data.Authority;
import com.pixplaze.api.web.data.auth.DeviceAuthorizationSession;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static com.pixplaze.api.web.util.CryptoUtils.hash;

@Slf4j
@Service
public class DeviceAuthorizationSessionService {
    private final Map<String, DeviceAuthorizationSession<?>> deviceCodeHashStore = new ConcurrentHashMap<>();
    private final Map<String, DeviceAuthorizationSession<?>> userCodeStore = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    private final @Getter Duration expiration;
    private final @Getter Duration interval;
    /// Срок жизни сессии после одобрения (grant): одобренная сессия должна погаснуть в узком окне,
    /// чтобы bearer-QR обратного логина нельзя было погасить позже. По умолчанию 15с.
    private final @Getter Duration grantExpiration;

    public DeviceAuthorizationSessionService(
            @Value("${app.security.auth.device.session.expiration.seconds}") Integer expirationInSeconds,
            @Value("${app.security.auth.device.session.interval.seconds}") Integer pollingIntervalInSeconds,
            @Value("${app.security.auth.device.session.grant-expiration.seconds:15}") Integer grantExpirationInSeconds
    ) {
        this.expiration = Duration.ofSeconds(expirationInSeconds);
        this.interval = Duration.ofSeconds(pollingIntervalInSeconds);
        this.grantExpiration = Duration.ofSeconds(grantExpirationInSeconds);
    }

    @SuppressWarnings("unchecked")
    public <A> DeviceAuthorizationSession<A> createSession(
            String clientId,
            String userCode,
            String deviceCode,
            Authority authority,
            DeviceAuthorizationStrategy<?, ?> strategy,
            @Nullable A authorizationDetails
    ) {
        final var deviceCodeHash = hash(deviceCode);

        return new DeviceAuthorizationSession<>(
                clientId,
                userCode,
                deviceCodeHash,
                (DeviceAuthorizationStrategy<A, ?>) strategy,
                authority,
                authorizationDetails,
                expiration,
                interval
        );
    }

    public <A> void add(DeviceAuthorizationSession<A> session) {
        deviceCodeHashStore.put(session.getState().deviceCodeHash(), session);
        userCodeStore.put(session.getState().userCode(), session);
    }

    private void remove(String deviceCodeHash) {
        final var session = deviceCodeHashStore.remove(deviceCodeHash);
        final var userCode = session.getState().userCode();
        userCodeStore.remove(userCode);
    }

    public void remove(DeviceAuthorizationSession<?> session) {
        if (session == null) {
            return;
        }

        remove(session.getState().deviceCodeHash());
    }

    @SuppressWarnings("unchecked")
    public <A> Optional<DeviceAuthorizationSession<A>> getSessionByDeviceCode(String deviceCode) {
        return Optional.ofNullable((DeviceAuthorizationSession<A>) deviceCodeHashStore.get(hash(deviceCode)));
    }

    @SuppressWarnings("unchecked")
    public <A> Optional<DeviceAuthorizationSession<A>> getSessionByUserCode(String userCode) {
        return Optional.ofNullable((DeviceAuthorizationSession<A>) userCodeStore.get(userCode));
    }

    public String generateDeviceCode() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return HexFormat.of().formatHex(randomBytes);
    }

    public String generateUserCode() {
        char[] alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray(); // Исключены похожие O, 0, I, 1
        char[] result = new char[8];
        for (int i = 0; i < 8; i++) {
            result[i] = alphabet[secureRandom.nextInt(alphabet.length)];
        }
        return new String(result);
    }
}
