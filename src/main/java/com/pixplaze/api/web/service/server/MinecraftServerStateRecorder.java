package com.pixplaze.api.web.service.server;

import com.pixplaze.api.web.data.server.ObservedState;
import com.pixplaze.api.web.repository.MinecraftServerStateRepository;
import com.pixplaze.api.web.service.server.model.MinecraftServerListing;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Запись состояния серверов из снапшота в БД: текущая строка {@code minecraft_server_state} (для
 * прогрева листинга) и ряд {@code minecraft_server_state_history} (для статистики). Пишется наблюдаемое
 * состояние — слияние части пинга и свежей части плагина — на момент пинга.
 * <ul>
 *   <li>{@link #recordAll()} — пачкой раз в интервал, только серверы с новым пингом с прошлой записи;</li>
 *   <li>{@link #record(long)} — внеочередной замер одного сервера при смене статуса.</li>
 * </ul>
 * Пишутся только пинги этого запуска: прогретое из БД состояние в ряд не попадает. Оба пути
 * синхронизированы, чтобы один пинг не попал в ряд дважды.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinecraftServerStateRecorder {

    private final MinecraftServerSnapshotStore store;
    private final MinecraftServerListingAssembler assembler;
    private final MinecraftServerStateRepository stateRepository;
    private final TransactionTemplate transactionTemplate;

    /// Момент последнего записанного пинга по серверу — чтобы не дублировать замер без нового пинга.
    private final Map<Long, Instant> recordedAt = new ConcurrentHashMap<>();

    @Scheduled(
            fixedDelayString = "${app.servers.state.record-millis:60000}",
            initialDelayString = "${app.servers.state.record-millis:60000}"
    )
    public synchronized void recordAll() {
        try {
            final var observations = store.findAll().stream()
                    .map(this::toObservationIfNew)
                    .flatMap(Optional::stream)
                    .toList();
            write(observations);
            log.debug("Server states recorded: {}", observations.size());
        } catch (Exception e) {
            log.warn("Server state recording failed: {}", e.toString());
        }
    }

    /// Внеочередной замер одного сервера (смена статуса). No-op, если сервера нет в снапшоте.
    public synchronized void record(long serverId) {
        try {
            write(store.find(serverId).flatMap(this::toObservationIfNew).stream().toList());
        } catch (Exception e) {
            log.warn("Server state recording failed for {}: {}", serverId, e.toString());
        }
    }

    /// Текущие строки и ряд — одной транзакцией; учёт записанного обновляется только после коммита.
    private void write(List<ObservedState> observations) {
        if (observations.isEmpty()) {
            return;
        }

        transactionTemplate.executeWithoutResult(status -> {
            stateRepository.upsertStates(observations);
            stateRepository.insertHistory(observations);
        });

        observations.forEach(observation -> recordedAt.put(observation.state().minecraftServerId(), observation.at()));
    }

    private Optional<ObservedState> toObservationIfNew(MinecraftServerListing listing) {
        if (!listing.pinged() || listing.ping() == null) {
            return Optional.empty();
        }

        final var pingedAt = listing.ping().at();
        if (!pingedAt.isAfter(recordedAt.getOrDefault(listing.id(), Instant.MIN))) {
            return Optional.empty();
        }

        return Optional.of(new ObservedState(assembler.toObservedState(listing), pingedAt));
    }
}
