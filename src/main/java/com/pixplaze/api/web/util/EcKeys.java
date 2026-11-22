package com.pixplaze.api.web.util;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class EcKeys {

    private EcKeys() {}

    private static final Base64.Decoder DECODER = Base64.getDecoder();
    private static final Base64.Encoder URL_ENCODER =
            Base64.getUrlEncoder().withoutPadding();

    private static KeyFactory keyFactory() {
        try {
            return KeyFactory.getInstance("EC");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("EC KeyFactory unavailable", e);
        }
    }

    private static byte[] decode(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Empty key");
        }

        try {
            return DECODER.decode(key.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid Base64 key", e);
        }
    }

    /** PKCS#8 DER -> EC PrivateKey */
    public static ECPrivateKey parsePrivateKey(String base64) {
        try {
            return (ECPrivateKey) keyFactory().generatePrivate(
                    new PKCS8EncodedKeySpec(decode(base64))
            );
        } catch (GeneralSecurityException | ClassCastException e) {
            throw new IllegalArgumentException("Invalid EC private key", e);
        }
    }

    /** X.509 SPKI DER -> EC PublicKey */
    public static ECPublicKey parsePublicKey(String base64) {
        try {
            return (ECPublicKey) keyFactory().generatePublic(
                    new X509EncodedKeySpec(decode(base64))
            );
        } catch (GeneralSecurityException | ClassCastException e) {
            throw new IllegalArgumentException("Invalid EC public key", e);
        }
    }

    /**
     * VAPID public key:
     * Uncompressed EC point (0x04 || X || Y), Base64URL without padding.
     */
    public static String toVapidPublicKey(ECPublicKey key) {
        byte[] point = new byte[65];
        point[0] = 0x04;

        putCoordinate(point, 1, key.getW().getAffineX());
        putCoordinate(point, 33, key.getW().getAffineY());

        return URL_ENCODER.encodeToString(point);
    }

    /**
     * VAPID private key: 32-byte unsigned big-endian EC scalar.
     */
    public static byte[] toVapidPrivateKey(ECPrivateKey key) {
        return toFixedLength(key.getS(), 32);
    }

    private static void putCoordinate(
            byte[] target,
            int offset,
            BigInteger value
    ) {
        byte[] bytes = toFixedLength(value, 32);
        System.arraycopy(bytes, 0, target, offset, 32);
    }

    private static byte[] toFixedLength(BigInteger value, int length) {
        byte[] bytes = value.toByteArray();

        // Убираем дополнительный нулевой байт знака BigInteger.
        int offset = bytes.length > length && bytes[0] == 0 ? 1 : 0;
        int actualLength = bytes.length - offset;

        if (actualLength > length) {
            throw new IllegalArgumentException("EC coordinate exceeds expected length");
        }

        byte[] result = new byte[length];

        System.arraycopy(
                bytes, offset,
                result, length - actualLength,
                actualLength
        );

        return result;
    }
}
