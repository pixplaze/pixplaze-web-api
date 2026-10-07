package com.pixplaze.api.web.service.server;

import com.pixplaze.api.web.data.db.tables.pojos.MinecraftServer;
import com.pixplaze.api.web.data.server.ObservedState;
import com.pixplaze.api.web.data.server.ServerHosts;
import com.pixplaze.api.web.data.server.ServerRatingAggregate;
import com.pixplaze.api.web.service.server.model.MinecraftServerListing;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Хранилище материализованного снапшота листинга (read/write split): весь сетевой I/O пишет сюда
 * в фоне, а read-слой (`/servers`, `/servers/state`) только читает — быстро и без зависимости от
 * доступности серверов.
 *
 * <p>Абстракция намеренно тонкая, чтобы при росте (тысячи+ серверов, несколько инстансов) заменить
 * in-memory реализацию на распределённую (напр. Redis: часть пинга и часть плагина — отдельные записи,
 * у плагинной TTL) без правки read-слоя.
 *
 * <p>Модель обновления: {@link #replaceAll} задаёт базовый набор из БД, сохраняя собранное в памяти и не
 * откатывая более свежие описание и адреса; {@code put*} обновляют точечно; {@link #publish} атомарно
 * публикует новый неизменяемый вид для читателей (раз в цикл, а не на каждую запись).
 */
public interface MinecraftServerSnapshotStore {

    /** Точечный поиск по DB id — свежие данные (для `/servers/state`). */
    Optional<MinecraftServerListing> find(long serverId);

    /** Опубликованный неизменяемый вид всего листинга — для пагинации `/servers` (O(1) чтение). */
    List<MinecraftServerListing> findAll();

    /** Синхронизирует базовый набор с БД: обновление известных, добавление новых, удаление делистнутых. */
    void replaceAll(Collection<MinecraftServerListing> bases);

    /** Добавляет новый сервер (после регистрации), не дожидаясь base-sync. No-op, если он уже в наборе. */
    void putServer(MinecraftServerListing listing);

    /** Часть пинга (успех или OFFLINE). No-op, если сервер не в наборе. */
    void putPing(long serverId, ObservedState ping);

    /** Часть плагина из heartbeat. No-op, если сервер не в наборе. */
    void putPlugin(long serverId, ObservedState plugin);

    /** Описание, если оно не старше известного ({@code updated_at}). No-op, если сервер не в наборе. */
    void putBase(MinecraftServer base);

    /** Адреса, если они не старше известных. No-op, если сервер не в наборе. */
    void putHosts(long serverId, ServerHosts hosts);

    /** Обновляет агрегат рейтинга сервера (после голоса). No-op, если сервер не в наборе. */
    void putRating(long serverId, ServerRatingAggregate rating);

    /** Атомарно публикует текущее состояние как неизменяемый вид для читателей. */
    void publish();
}
