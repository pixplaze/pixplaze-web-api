package com.pixplaze.api.web.service.server;

import com.pixplaze.api.web.data.server.OnlineSnapshot;
import com.pixplaze.api.web.data.server.PluginSnapshot;
import com.pixplaze.api.web.data.server.MinecraftServerListingInfo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Хранилище материализованного снапшота листинга (read/write split): весь сетевой I/O пишет сюда
 * в фоне, а read-слой (`/servers`, `/servers/state`) только читает — быстро и без зависимости от
 * доступности серверов.
 *
 * <p>Абстракция намеренно тонкая, чтобы при росте (тысячи+ серверов, несколько инстансов) заменить
 * in-memory реализацию на распределённую (напр. Redis) без правки read-слоя.
 *
 * <p>Модель обновления: {@link #replaceAll} задаёт базовый набор из БД (сохраняя online/plugin),
 * {@link #putOnline}/{@link #putPlugin} обновляют тиры точечно, {@link #publish} атомарно
 * публикует новый неизменяемый вид для читателей (раз в цикл, а не на каждую запись).
 */
public interface ServerSnapshotStore {

    /** Точечный поиск по DB id — свежие данные (для `/servers/state`). */
    Optional<MinecraftServerListingInfo> find(long serverId);

    /** Опубликованный неизменяемый вид всего листинга — для пагинации `/servers` (O(1) чтение). */
    List<MinecraftServerListingInfo> findAll();

    /** Синхронизирует базовый набор с БД: upsert базы (сохраняя online/plugin), удаление делистнутых. */
    void replaceAll(Collection<MinecraftServerListingInfo> bases);

    /** Обновляет Tier-2 сервера ({@code null} ⇒ помечен offline). No-op, если сервер не в наборе. */
    void putOnline(long serverId, OnlineSnapshot online);

    /** Обновляет Tier-3 сервера. No-op, если сервер не в наборе. */
    void putPlugin(long serverId, PluginSnapshot plugin);

    /** Атомарно публикует текущее состояние как неизменяемый вид для читателей. */
    void publish();
}
