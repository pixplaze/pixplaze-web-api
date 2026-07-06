package com.pixplaze.api.web.util;

public class PagingUtils {

    /**
     * Нормализованные параметры пагинации для потоков: {@code from} — сколько элементов пропустить
     * ({@code Stream.skip}), {@code size} — сколько взять ({@code Stream.limit}). Именно count, а не
     * абсолютный конечный индекс, — чтобы {@code skip(from).limit(size)} возвращал ровно страницу.
     */
    public record Paging(int from, int size) {}

    /// offset &lt; 0 → 0; limit клампится в диапазон [1..100].
    public static Paging of(int limit, int offset) {
        final var from = Math.max(0, offset);
        final var size = Math.min(Math.max(limit, 1), 100);
        return new Paging(from, size);
    }
}
