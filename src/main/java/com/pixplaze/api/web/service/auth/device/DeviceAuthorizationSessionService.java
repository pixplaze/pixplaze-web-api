package com.pixplaze.api.web.service.auth.device;

import com.pixplaze.api.ext.data.Authority;
import com.pixplaze.api.web.data.auth.DeviceAuthorizationSession;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class DeviceAuthorizationSessionService {
    private final Map<String, DeviceAuthorizationSession<?>> deviceCodeHashStore = new ConcurrentHashMap<>();
    private final Map<String, DeviceAuthorizationSession<?>> userCodeStore = new ConcurrentHashMap<>();
    private final DeviceAuthorizationStrategyFactory deviceAuthorizationStrategyFactory;
    private final SecureRandom secureRandom = new SecureRandom();
    private final @Getter Duration expiration;
    private final @Getter Duration interval;

    public DeviceAuthorizationSessionService(
            DeviceAuthorizationStrategyFactory deviceAuthorizationStrategyFactory,
            @Value("${app.security.auth.device.session.expiration.seconds}") Integer expirationInSeconds,
            @Value("${app.security.auth.device.session.interval.seconds}") Integer pollingIntervalInSeconds
    ) {
        this.deviceAuthorizationStrategyFactory = deviceAuthorizationStrategyFactory;
        this.expiration = Duration.ofSeconds(expirationInSeconds);
        this.interval = Duration.ofSeconds(pollingIntervalInSeconds);
    }

    @SuppressWarnings("unchecked")
    public <D> DeviceAuthorizationSession<D> createSession(
            String clientId,
            String deviceCodeHash,
            Authority authority,
            String authorizationDetails
    ) {
        final var userCode = generateUserCode();
        final var strategy = (DeviceAuthorizationStrategy<D, ?>) deviceAuthorizationStrategyFactory.of(authority);
        final var parsedAuthorizationDetails = Optional.ofNullable(authorizationDetails)
                .map(strategy::parseAuthorizationDetails)
                .orElse(null);
        final var session = new DeviceAuthorizationSession<>(clientId, userCode, deviceCodeHash, strategy, authority, parsedAuthorizationDetails, expiration);

        deviceCodeHashStore.put(deviceCodeHash, session);
        userCodeStore.put(userCode, session);

        return session;
    }

    private void removeSession(String deviceCodeHash) {
        final var session = deviceCodeHashStore.remove(deviceCodeHash);
        final var userCode = session.getState().userCode();
        userCodeStore.remove(userCode);
    }

    public void removeSession(DeviceAuthorizationSession<?> session) {
        if (session == null) {
            return;
        }

        removeSession(session.getState().deviceCodeHash());
    }

    @SuppressWarnings("unchecked")
    public <D> DeviceAuthorizationSession<D> getSessionByDeviceCode(String deviceCode) {
        return (DeviceAuthorizationSession<D>) deviceCodeHashStore.get(hashDeviceCode(deviceCode));
    }

    @SuppressWarnings("unchecked")
    public <D> DeviceAuthorizationSession<D> getSessionByUserCode(String userCode) {
        return (DeviceAuthorizationSession<D>) userCodeStore.get(userCode);
    }

    public String generateDeviceCode() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return HexFormat.of().formatHex(randomBytes);
    }

    private String generateUserCode() {
        char[] alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray(); // Исключены похожие O, 0, I, 1
        char[] result = new char[8];
        for (int i = 0; i < 8; i++) {
            result[i] = alphabet[secureRandom.nextInt(alphabet.length)];
        }
        return new String(result);
    }

    public String hashDeviceCode(String deviceCode) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(deviceCode.getBytes(StandardCharsets.UTF_8));
            // Преобразуем байты в Hex-строку (начиная с Java 17 можно использовать HexFormat)
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Критическая ошибка: алгоритм SHA-256 не найден", e);
        }
    }
}
