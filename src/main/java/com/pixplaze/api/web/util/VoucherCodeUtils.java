package com.pixplaze.api.web.util;

import java.util.concurrent.ThreadLocalRandom;

public class VoucherCodeUtils {
    private static final char[] ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final int ALPHABET_LENGTH = ALPHABET.length;
    public static final int DEFAULT_CODE_LENGTH = 8;
    public static final int MINIMUM_CODE_LENGTH = 4;
    private VoucherCodeUtils() {}

    /// Генерирует случайную строку заданной длины из цифр и заглавных букв.
    ///
    /// @param length длина генерируемого кода
    /// @return случайный код
    public static String generate(int length) {
        if (length < MINIMUM_CODE_LENGTH) {
            throw new IllegalArgumentException("Length must be greater than %d!".formatted(MINIMUM_CODE_LENGTH));
        }

        char[] result = new char[length];
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < length; i++) {
            result[i] = ALPHABET[random.nextInt(ALPHABET_LENGTH)];
        }

        return new String(result);
    }

    public static String generate() {
        return generate(DEFAULT_CODE_LENGTH);
    }
}
