package com.pixplaze.api.web.util;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

/// Голова скина, присланная сервером: картинку присылает чужая сторона, поэтому мусор и крупные изображения
/// не сохраняем (голова 8×8 в PNG — сотни байт).
@Slf4j
public final class SkinHeads {

    /// Голова 8×8 в PNG — сотни байт; запас на непривычное сжатие.
    private static final int MAX_LENGTH = 4096;
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private SkinHeads() {
    }

    /// Голова, если похожа на голову: Base64 PNG небольшого размера; иначе {@code null}.
    public static String valid(UUID playerUuid, String head) {
        if (head == null) {
            return null;
        }
        if (head.length() > MAX_LENGTH) {
            log.debug("Skin head of player {} rejected: {} characters", playerUuid, head.length());
            return null;
        }
        try {
            final var bytes = Base64.getDecoder().decode(head);
            if (bytes.length < PNG_SIGNATURE.length
                    || !Arrays.equals(bytes, 0, PNG_SIGNATURE.length, PNG_SIGNATURE, 0, PNG_SIGNATURE.length)) {
                log.debug("Skin head of player {} rejected: not a PNG", playerUuid);
                return null;
            }
        } catch (IllegalArgumentException e) {
            log.debug("Skin head of player {} rejected: not Base64", playerUuid);
            return null;
        }
        return head;
    }
}
