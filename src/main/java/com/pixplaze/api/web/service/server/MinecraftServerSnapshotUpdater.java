package com.pixplaze.api.web.service.server;

import com.pixplaze.api.web.data.server.ServerRatingAggregate;
import com.pixplaze.api.web.repository.MinecraftServerRepository;
import com.pixplaze.api.web.service.server.event.MinecraftServerChanged;
import com.pixplaze.api.web.service.server.event.MinecraftServerCreated;
import com.pixplaze.api.web.service.server.event.MinecraftServerHostsChanged;
import com.pixplaze.api.web.service.server.event.MinecraftServerRatingChanged;
import com.pixplaze.api.web.service.server.model.MinecraftServerListing;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Единственное место, где записи в БД доходят до {@link MinecraftServerSnapshotStore}: листинг видит
 * изменение с ближайшим publish, не дожидаясь base-sync. Срабатывает только после коммита, поэтому
 * откаченная транзакция стор не затрагивает; вне транзакции (пинг-поток) — сразу.
 *
 * <p>Части состояния, которые живут только в памяти (пинг, heartbeat плагина), пишутся в стор напрямую:
 * ждать им нечего.
 */
@Component
@RequiredArgsConstructor
public class MinecraftServerSnapshotUpdater {

    private final MinecraftServerSnapshotStore store;
    private final MinecraftServerRepository minecraftServerRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCreated(MinecraftServerCreated event) {
        final var server = event.server();
        store.putServer(MinecraftServerListing.of(
                server,
                minecraftServerRepository.findHosts(server.getId()),
                event.integration(),
                null,
                ServerRatingAggregate.EMPTY
        ));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onChanged(MinecraftServerChanged event) {
        store.putBase(event.server());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onHostsChanged(MinecraftServerHostsChanged event) {
        store.putHosts(event.minecraftServerId(), minecraftServerRepository.findHosts(event.minecraftServerId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRatingChanged(MinecraftServerRatingChanged event) {
        store.putRating(event.minecraftServerId(), minecraftServerRepository.ratingAggregate(event.minecraftServerId()));
    }
}
