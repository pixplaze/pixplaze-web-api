package com.pixplaze.api.web.repository;

import com.pixplaze.api.web.data.server.ObservedState;
import com.pixplaze.api.web.util.NullUtils;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;
import java.util.Collection;

import static com.pixplaze.api.web.data.db.Tables.MINECRAFT_SERVER_STATE;
import static com.pixplaze.api.web.data.db.Tables.MINECRAFT_SERVER_STATE_HISTORY;

/// Запись состояния серверов: текущая строка (перезаписывается) и ряд замеров (только добавление).
/// Состояние — {@code MinecraftServerStateInfo} с {@code minecraftServerId}, время — момент наблюдения.
@Repository
@RequiredArgsConstructor
public class MinecraftServerStateRepository {

    private final DSLContext dslContext;

    /// Перезаписывает текущее состояние серверов.
    public void upsertStates(Collection<ObservedState> observations) {
        if (NullUtils.isNullOrEmpty(observations)) {
            return;
        }

        final var table = MINECRAFT_SERVER_STATE;
        final var queries = observations.stream()
                .map(observation -> {
                    final var state = observation.state();
                    final var players = state.players();
                    return dslContext.insertInto(table)
                            .set(table.MINECRAFT_SERVER_ID, state.minecraftServerId())
                            .set(table.STATUS, state.status())
                            .set(table.INTEGRATION_STATUS, state.integrationStatus())
                            .set(table.PING, state.ping())
                            .set(table.TPS, state.tps())
                            .set(table.UPTIME, state.uptime())
                            .set(table.DIFFICULTY, state.difficulty())
                            .set(table.PLAYERS_ONLINE, players != null ? players.online() : null)
                            .set(table.PLAYERS_MAX, players != null ? players.max() : null)
                            .set(table.UPDATED_AT, observation.at().atOffset(ZoneOffset.UTC))
                            .onConflict(table.MINECRAFT_SERVER_ID)
                            .doUpdate()
                            .set(table.STATUS, DSL.excluded(table.STATUS))
                            .set(table.INTEGRATION_STATUS, DSL.excluded(table.INTEGRATION_STATUS))
                            .set(table.PING, DSL.excluded(table.PING))
                            .set(table.TPS, DSL.excluded(table.TPS))
                            .set(table.UPTIME, DSL.excluded(table.UPTIME))
                            .set(table.DIFFICULTY, DSL.excluded(table.DIFFICULTY))
                            .set(table.PLAYERS_ONLINE, DSL.excluded(table.PLAYERS_ONLINE))
                            .set(table.PLAYERS_MAX, DSL.excluded(table.PLAYERS_MAX))
                            .set(table.UPDATED_AT, DSL.excluded(table.UPDATED_AT));
                })
                .toList();

        dslContext.batch(queries).execute();
    }

    /// Добавляет замеры в ряд для статистики.
    public void insertHistory(Collection<ObservedState> observations) {
        if (NullUtils.isNullOrEmpty(observations)) {
            return;
        }

        final var table = MINECRAFT_SERVER_STATE_HISTORY;
        final var queries = observations.stream()
                .map(observation -> {
                    final var state = observation.state();
                    final var players = state.players();
                    return dslContext.insertInto(table)
                            .set(table.MINECRAFT_SERVER_ID, state.minecraftServerId())
                            .set(table.SAMPLED_AT, observation.at().atOffset(ZoneOffset.UTC))
                            .set(table.STATUS, state.status())
                            .set(table.INTEGRATION_STATUS, state.integrationStatus())
                            .set(table.PING, state.ping())
                            .set(table.TPS, state.tps())
                            .set(table.UPTIME, state.uptime())
                            .set(table.DIFFICULTY, state.difficulty())
                            .set(table.PLAYERS_ONLINE, players != null ? players.online() : null)
                            .set(table.PLAYERS_MAX, players != null ? players.max() : null);
                })
                .toList();

        dslContext.batch(queries).execute();
    }
}
