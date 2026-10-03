package com.pixplaze.api.web.service.auth.device;

import com.pixplaze.api.web.util.CryptoUtils;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.HexFormat;

import static com.pixplaze.api.web.util.CryptoUtils.hash;

/**
 * Выпуск кодов device-flow (RFC 8628) и приведение device-кода к ключу хранения.
 *
 * <p>Вынесено отдельно от протокола по двум причинам. Первая: свойства кодов — длина, алфавит,
 * энтропия — самостоятельны и не зависят ни от сроков сессии, ни от её состояния. Вторая, более
 * важная: здесь сосредоточен инвариант безопасности — наружу и в хранилище уходит только
 * {@link #hashDeviceCode(String) хэш}, сырой device-код живёт лишь в ответе запрашивающему
 * устройству и в его последующих опросах.
 */
@Component
public class DeviceCodeGenerator {

    /// Алфавит user-кода без визуально похожих символов: исключены O, 0, I и 1 (RFC 8628 §6.1
    /// рекомендует учитывать, что код человек читает с экрана и вводит руками).
    private static final char[] USER_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int USER_CODE_LENGTH = 8;

    /// 32 байта энтропии: device-код не вводится человеком, поэтому ограничений на длину нет.
    private static final int DEVICE_CODE_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generateDeviceCode() {
        final var randomBytes = new byte[DEVICE_CODE_BYTES];
        secureRandom.nextBytes(randomBytes);

        return HexFormat.of().formatHex(randomBytes);
    }

    public String generateUserCode() {
        final var result = new char[USER_CODE_LENGTH];

        for (int i = 0; i < USER_CODE_LENGTH; i++) {
            result[i] = USER_CODE_ALPHABET[secureRandom.nextInt(USER_CODE_ALPHABET.length)];
        }

        return new String(result);
    }

    /// Ключ, под которым сессия лежит в хранилище. Сам device-код туда не попадает никогда —
    /// ни в память, ни, в будущем, в Redis.
    public String hashDeviceCode(String deviceCode) {
        return CryptoUtils.hash(deviceCode);
    }
}
