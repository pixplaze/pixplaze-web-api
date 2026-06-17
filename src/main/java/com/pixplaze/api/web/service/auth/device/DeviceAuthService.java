package com.pixplaze.api.web.service.auth.device;

import com.pixplaze.api.ext.data.Authority;
import com.pixplaze.api.ext.data.auth.AuthorizationTokenInfo;
import com.pixplaze.api.ext.data.auth.DeviceAuthenticationStateInfo;
import com.pixplaze.api.ext.data.auth.DeviceResponseInfo;
import com.pixplaze.api.web.data.auth.DeviceAuthSession;
import com.pixplaze.api.web.data.user.Profile;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DeviceAuthService {

    private final Map<String, DeviceAuthSession> sessionStore = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    /// TTL 10 minutes
    private final Duration expiration = Duration.ofMinutes(10);

    /**
     * 1. ИНИЦИАЦИЯ (Вызывается при запросе /device/authorize от NAD)
     */
    public DeviceResponseInfo initializeAuthentication(String clientId, Authority role) {
        // Генерация криптографически стойкого raw device_code (32 случайных байта -> 64 символа)

        String deviceCode = generateDeviceCode();
        // Генерация короткого user_code для человека (пример: простая строка из 8 случайных букв)
        String userCode = generateUserCode();
        // Хешируем device_code перед сохранением в память
        String deviceCodeHash = hashDeviceCode(deviceCode);

        // Создаем сессию и кладем её в HashMap по СХЕШИРОВАННОМУ ключу
        DeviceAuthSession session = new DeviceAuthSession(userCode, clientId, expiration);
        sessionStore.put(deviceCodeHash, session);

        // Возвращаем NAD чистый (raw) код ОДИН раз. В памяти он не останется!
        return new DeviceResponseInfo(deviceCode, userCode, (int) expiration.toSeconds(), 5, null, null);
    }

    /**
     * 2. ПОЛЛИНГ (Вызывается при запросе /token от NAD)
     */
    public AuthorizationTokenInfo validatePolling(String deviceCode, String clientId) throws Exception {
        // Хешируем пришедший от NAD код, чтобы сравнить с тем, что в базе
        String deviceCodeHash = hashDeviceCode(deviceCode);

        // Ищем сессию по хешу
        final var session = sessionStore.get(deviceCodeHash);

        if (session == null || session.isExpired()) {
            if (session != null) {
                sessionStore.remove(deviceCodeHash); // Удаляем, если протух
            }
            throw new Exception("expired_token"); // Код не найден или истек срок действия
        }

        if (session.isExhausted()) {
            sessionStore.remove(deviceCodeHash); // Удаляем, если протух
            throw new Exception("access_denied");
        }

        // ЗАЩИТА: Проверяем, что токен опрашивает именно тот clientId, который его запрашивал
        if (!session.getClientId().equals(clientId)) {
            throw new Exception("invalid_grant"); // Попытка подмены контекста
        }

        // Проверяем статус авторизации
        if (DeviceAuthenticationStateInfo.Status.PENDING.equals(session.getState().status())) {
            throw new Exception("authorization_pending");
        }

        if (DeviceAuthenticationStateInfo.Status.DENIED.equals(session.getState().status())) {
            sessionStore.remove(deviceCodeHash); // Удаляем заблокированную сессию
            throw new Exception("access_denied");
        }

        if (DeviceAuthenticationStateInfo.Status.APPROVED.equals(session.getState().status())) {
            // Успех! Генерируем реальные OAuth-токены для пользователя session.getUserId()
            AuthorizationTokenInfo tokens = generateOAuthTokens(session.getState().profile());

            // ОДНОРАЗОВОСТЬ: Немедленно удаляем сессию из памяти после выдачи токенов
            sessionStore.remove(deviceCodeHash);

            return tokens;
        }

        throw new Exception("server_error");
    }

    private String generateUserCode() {
        // Упрощенная генерация для примера
        char[] alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray(); // Исключены похожие O, 0, I, 1
        char[] result = new char[8];
        for (int i = 0; i < 8; i++) {
            result[i] = alphabet[secureRandom.nextInt(alphabet.length)];
        }
        return new String(result);
    }

    private String generateDeviceCode() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return HexFormat.of().formatHex(randomBytes);
    }

    private String hashDeviceCode(String deviceCode) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(deviceCode.getBytes(StandardCharsets.UTF_8));
            // Преобразуем байты в Hex-строку (начиная с Java 17 можно использовать HexFormat)
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Критическая ошибка: алгоритм SHA-256 не найден", e);
        }
    }

    // Заглушки
    private AuthorizationTokenInfo generateOAuthTokens(Profile userId) { return new AuthorizationTokenInfo("access_xyz", "refresh_abc"); }
}
