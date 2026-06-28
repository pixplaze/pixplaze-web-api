package com.pixplaze.api.web.util;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * Разбор EC-ключей (P-256 / secp256r1) из строки без предварительной конвертации.
 *
 * <p>Принимает приватный ключ в форматах <b>PKCS#8</b> или <b>SEC1</b> (RFC 5915 —
 * то, что отдаёт {@code openssl genpkey}/{@code ecparam}), публичный — в <b>X.509
 * SubjectPublicKeyInfo</b>; в обоих случаях как Base64 от DER, так и PEM
 * (с {@code -----BEGIN-----} и переносами).
 *
 * <p><b>Ограничение:</b> только named-curve P-256. Ключи с explicit EC-параметрами
 * не поддерживаются JDK SunEC в принципе (для них понадобился бы BouncyCastle).
 */
public final class EcKeys {

    private static final String ALGORITHM = "EC";
    private static final String CURVE = "secp256r1"; // P-256

    private EcKeys() {}

    /** Разбирает приватный EC-ключ: сперва как PKCS#8, при неудаче — как SEC1. */
    public static PrivateKey parsePrivateKey(String key) {
        final var der = decode(key);
        try {
            return keyFactory().generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (InvalidKeySpecException pkcs8Failure) {
            try {
                // SEC1: восстанавливаем ключ из приватного скаляра и параметров P-256.
                return keyFactory().generatePrivate(new ECPrivateKeySpec(extractSec1Scalar(der), p256Params()));
            } catch (RuntimeException | InvalidKeySpecException sec1Failure) {
                final var error = new IllegalArgumentException(
                        "Unsupported EC private key: expected named-curve P-256 in PKCS#8 or SEC1 (Base64 DER or PEM)");
                error.addSuppressed(pkcs8Failure);
                error.addSuppressed(sec1Failure);
                throw error;
            }
        }
    }

    /** Разбирает публичный EC-ключ (X.509 SubjectPublicKeyInfo). */
    public static PublicKey parsePublicKey(String key) {
        try {
            return keyFactory().generatePublic(new X509EncodedKeySpec(decode(key)));
        } catch (InvalidKeySpecException e) {
            throw new IllegalArgumentException(
                    "Unsupported EC public key: expected named-curve P-256 X.509 (Base64 DER or PEM)", e);
        }
    }

    /**
     * Извлекает публичный ключ, <b>вложенный</b> в приватный ({@code [1] publicKey} в SEC1).
     * Работает для ключей, сгенерированных OpenSSL (и нашим {@code generate-keypair.sh}).
     *
     * <p>Это извлечение, а не вычисление {@code Q = d·G}: если в приватном ключе
     * публичная точка не вложена (например, минимальный PKCS#8 от JDK/Node), метод
     * бросит ошибку — настоящий вывод из скаляра требует EC point-mul (BouncyCastle).
     */
    public static PublicKey derivePublicKey(String privateKey) {
        final var pkcs8 = new DerReader(new DerReader(decode(privateKey)).readValue(0x30));
        pkcs8.readValue(0x02);                         // version
        pkcs8.readValue(0x30);                         // AlgorithmIdentifier (ecPublicKey + curve)
        final var sec1 = new DerReader(new DerReader(pkcs8.readValue(0x04)).readValue(0x30));
        sec1.readValue(0x02);                          // SEC1 version
        sec1.readValue(0x04);                          // приватный скаляр — пропускаем

        byte[] bitString = null;
        while (sec1.hasMore()) {
            final var element = sec1.next();
            if (element.tag() == 0xA1) {               // [1] publicKey
                bitString = new DerReader(element.value()).readValue(0x03); // BIT STRING
                break;
            }
            // 0xA0 = [0] parameters — пропускаем
        }
        if (bitString == null) {
            throw new IllegalArgumentException(
                    "Private key has no embedded public key; deriving from the scalar (Q = d*G) needs EC point multiplication (BouncyCastle)");
        }

        // BIT STRING: [байт неиспользуемых битов = 0x00][несжатая точка 0x04 || X || Y]
        final var point = Arrays.copyOfRange(bitString, 1, bitString.length);
        if (point.length != 65 || (point[0] & 0xff) != 0x04) {
            throw new IllegalArgumentException("Unexpected EC public point encoding");
        }
        final var x = new BigInteger(1, Arrays.copyOfRange(point, 1, 33));
        final var y = new BigInteger(1, Arrays.copyOfRange(point, 33, 65));
        try {
            return keyFactory().generatePublic(new ECPublicKeySpec(new ECPoint(x, y), p256Params()));
        } catch (InvalidKeySpecException e) {
            throw new IllegalArgumentException("Failed to build EC public key from embedded point", e);
        }
    }

    private static KeyFactory keyFactory() {
        try {
            return KeyFactory.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("EC KeyFactory unavailable", e);
        }
    }

    /** Base64 от DER либо PEM (срезаем {@code -----BEGIN/END-----} и пробелы). */
    private static byte[] decode(String key) {
        var normalized = key.strip();
        if (normalized.startsWith("-----")) {
            normalized = normalized.replaceAll("-----[^-]+-----", "").replaceAll("\\s", "");
        }
        return Base64.getDecoder().decode(normalized);
    }

    private static ECParameterSpec p256Params() {
        try {
            final var params = AlgorithmParameters.getInstance(ALGORITHM);
            params.init(new ECGenParameterSpec(CURVE));
            return params.getParameterSpec(ECParameterSpec.class);
        } catch (NoSuchAlgorithmException | InvalidParameterSpecException e) {
            throw new IllegalStateException("P-256 parameters unavailable", e);
        }
    }

    /**
     * Извлекает приватный скаляр из SEC1 {@code ECPrivateKey}
     * ({@code SEQUENCE { INTEGER version, OCTET STRING privateKey, ... }}).
     */
    private static BigInteger extractSec1Scalar(byte[] der) {
        final var body = new DerReader(new DerReader(der).readValue(0x30));
        body.readValue(0x02);                          // version INTEGER — пропускаем
        return new BigInteger(1, body.readValue(0x04)); // OCTET STRING — скаляр
    }

    /** Минимальный DER TLV-ридер. */
    private static final class DerReader {
        private final byte[] buffer;
        private int position;

        DerReader(byte[] buffer) {
            this.buffer = buffer;
        }

        boolean hasMore() {
            return position < buffer.length;
        }

        /** Читает следующий элемент (любой тег) как пару тег/содержимое. */
        Tlv next() {
            final int tag = buffer[position++] & 0xff;
            final int length = readLength();
            final var value = Arrays.copyOfRange(buffer, position, position + length);
            position += length;
            return new Tlv(tag, value);
        }

        /** Читает следующий элемент с ожидаемым тегом и возвращает его содержимое. */
        byte[] readValue(int expectedTag) {
            final var element = next();
            if (element.tag() != expectedTag) {
                throw new IllegalArgumentException(
                        "Unexpected DER tag 0x%02x (expected 0x%02x)".formatted(element.tag(), expectedTag));
            }
            return element.value();
        }

        private int readLength() {
            int length = buffer[position++] & 0xff;
            if (length >= 0x80) { // длинная форма длины
                final int lengthBytes = length & 0x7f;
                length = 0;
                for (int i = 0; i < lengthBytes; i++) {
                    length = (length << 8) | (buffer[position++] & 0xff);
                }
            }
            return length;
        }

        record Tlv(int tag, byte[] value) {}
    }
}
