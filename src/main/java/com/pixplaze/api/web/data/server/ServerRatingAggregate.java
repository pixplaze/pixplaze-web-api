package com.pixplaze.api.web.data.server;

/**
 * Агрегат рейтинга сервера, посчитанный в БД: среднее по всем голосам и их число.
 * {@code count == 0} ⇒ у сервера ещё нет оценок (в листинге {@code rating} отдаётся как {@code null}).
 *
 * @param average среднее значение оценок (1..5); {@code 0.0}, если голосов нет
 * @param count   число голосов
 */
public record ServerRatingAggregate(double average, long count) {
    public static final ServerRatingAggregate EMPTY = new ServerRatingAggregate(0.0, 0L);
}
