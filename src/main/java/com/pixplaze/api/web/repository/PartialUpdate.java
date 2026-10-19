package com.pixplaze.api.web.repository;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Слияние частичных данных со строкой БД — общее правило для всех источников (пинг, heartbeat, будущие):
 * {@code null} значит «источник этого не сообщил» (контракт ext-api), поэтому хранимое значение не трогает.
 * Пишется только изменение: сравнение делает БД одним запросом, без гонок между экземплярами.
 *
 * <p>Очистить колонку так нельзя — для этого нужен явный UPDATE.
 *
 * <pre>{@code
 * PartialUpdate.of(dslContext, MINECRAFT_SERVER)
 *         .set(MINECRAFT_SERVER.MOTD, motd)
 *         .touch(MINECRAFT_SERVER.UPDATED_AT)
 *         .execute(MINECRAFT_SERVER.ID.eq(serverId), MinecraftServer.class);
 * }</pre>
 */
public final class PartialUpdate<R extends Record> {

    private final DSLContext dslContext;
    private final Table<R> table;
    private final Map<Field<?>, Object> values = new LinkedHashMap<>();
    private Field<OffsetDateTime> updatedAt;

    private PartialUpdate(DSLContext dslContext, Table<R> table) {
        this.dslContext = dslContext;
        this.table = table;
    }

    public static <R extends Record> PartialUpdate<R> of(DSLContext dslContext, Table<R> table) {
        return new PartialUpdate<>(dslContext, table);
    }

    /// Новое значение колонки; {@code null} пропускается — хранимое остаётся.
    public <T> PartialUpdate<R> set(Field<T> field, T value) {
        if (value != null) {
            values.put(field, value);
        }
        return this;
    }

    /// Колонка времени изменения: ставится в текущее время, только если строка действительно меняется.
    public PartialUpdate<R> touch(Field<OffsetDateTime> field) {
        this.updatedAt = field;
        return this;
    }

    /// Обновляет строку по {@code where}, если хоть одно непустое значение отличается от хранимого.
    /// Возвращает обновлённую строку или пусто, если менять нечего.
    public <P> Optional<P> execute(Condition where, Class<P> type) {
        if (values.isEmpty()) {
            return Optional.empty();
        }

        var update = dslContext.update(table).set(values);
        if (updatedAt != null) {
            update = update.set(updatedAt, DSL.currentOffsetDateTime());
        }
        return update.where(where)
                .and(DSL.or(values.entrySet().stream().map(PartialUpdate::isDistinct).toList()))
                .returning()
                .fetchOptionalInto(type);
    }

    @SuppressWarnings("unchecked")
    private static Condition isDistinct(Map.Entry<Field<?>, Object> entry) {
        return ((Field<Object>) entry.getKey()).isDistinctFrom(entry.getValue());
    }
}
