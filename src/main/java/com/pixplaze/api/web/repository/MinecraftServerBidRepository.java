package com.pixplaze.api.web.repository;

import com.pixplaze.api.ext.data.server.MinecraftServerHostInfo;
import com.pixplaze.api.web.data.server.MinecraftServerRegistrationStatus;
import com.pixplaze.api.web.data.server.ServerBid;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.pixplaze.api.web.data.db.Tables.MINECRAFT_SERVER;
import static com.pixplaze.api.web.data.db.Tables.MINECRAFT_SERVER_BID;
import static com.pixplaze.api.web.data.db.Tables.MINECRAFT_SERVER_HOST;
import static com.pixplaze.api.web.data.db.Tables.PROFILE;
import static com.pixplaze.api.web.data.db.Tables.VOUCHER_CODE;

/// Коды регистрации серверов ({@code minecraft_server_bid}: сервер → ваучер) и чтение заявок — серверов с их кодом.
@Repository
@RequiredArgsConstructor
public class MinecraftServerBidRepository {
    private final DSLContext dslContext;

    /// Выдаёт серверу код регистрации.
    public void create(Long serverId, Long voucherCodeId) {
        dslContext.insertInto(MINECRAFT_SERVER_BID)
                .set(MINECRAFT_SERVER_BID.MINECRAFT_SERVER_ID, serverId)
                .set(MINECRAFT_SERVER_BID.VOUCHER_CODE_ID, voucherCodeId)
                .execute();
    }

    /// Заявка по выданному коду (любого статуса: регистрация решает, что с ним делать).
    public Optional<ServerBid> findByVoucherCodeId(Long voucherCodeId) {
        return select(MINECRAFT_SERVER_BID.VOUCHER_CODE_ID.eq(voucherCodeId)).stream().findFirst();
    }

    /// Заявка сервера с последним выданным кодом.
    public Optional<ServerBid> findLatest(Long serverId) {
        return select(MINECRAFT_SERVER_BID.MINECRAFT_SERVER_ID.eq(serverId).and(isLatest())).stream().findFirst();
    }

    /// Открытые заявки с последним кодом, старые первыми.
    public List<ServerBid> findPending() {
        return select(MINECRAFT_SERVER.STATUS.eq(MinecraftServerRegistrationStatus.PENDING).and(isLatest()));
    }

    /// Заявки с последним кодом, новые первыми; {@code null} в фильтре — без ограничения.
    public List<ServerBid> findLatest(Long ownerProfileId, MinecraftServerRegistrationStatus status) {
        var condition = isLatest();
        if (ownerProfileId != null) {
            condition = condition.and(MINECRAFT_SERVER.OWNER_PROFILE_ID.eq(ownerProfileId));
        }
        if (status != null) {
            condition = condition.and(MINECRAFT_SERVER.STATUS.eq(status));
        }
        final var bids = new ArrayList<>(select(condition));
        Collections.reverse(bids);
        return bids;
    }

    /// Закрывает как EXPIRED открытые заявки, чей последний код выдан раньше {@code issuedBefore}; возвращает
    /// их id — адреса им освобождает вызывающий.
    public List<Long> expirePendingIssuedBefore(OffsetDateTime issuedBefore) {
        return dslContext.update(MINECRAFT_SERVER)
                .set(MINECRAFT_SERVER.STATUS, MinecraftServerRegistrationStatus.EXPIRED)
                .set(MINECRAFT_SERVER.UPDATED_AT, DSL.currentOffsetDateTime())
                .where(MINECRAFT_SERVER.STATUS.eq(MinecraftServerRegistrationStatus.PENDING))
                .andNotExists(DSL.selectOne()
                        .from(MINECRAFT_SERVER_BID)
                        .where(MINECRAFT_SERVER_BID.MINECRAFT_SERVER_ID.eq(MINECRAFT_SERVER.ID))
                        .and(MINECRAFT_SERVER_BID.CREATED_AT.ge(issuedBefore)))
                .returning(MINECRAFT_SERVER.ID)
                .fetch(MINECRAFT_SERVER.ID);
    }

    /// Строка связи — последний код своего сервера.
    private static Condition isLatest() {
        final var other = MINECRAFT_SERVER_BID.as("other_bid");
        return DSL.notExists(DSL.selectOne()
                .from(other)
                .where(other.MINECRAFT_SERVER_ID.eq(MINECRAFT_SERVER_BID.MINECRAFT_SERVER_ID))
                .and(other.CREATED_AT.gt(MINECRAFT_SERVER_BID.CREATED_AT)));
    }

    private List<ServerBid> select(Condition condition) {
        return dslContext.select(
                        MINECRAFT_SERVER.ID,
                        MINECRAFT_SERVER.NAME,
                        MINECRAFT_SERVER.STATUS,
                        MINECRAFT_SERVER.OWNER_PROFILE_ID,
                        PROFILE.NAME,
                        MINECRAFT_SERVER_HOST.ADDRESS,
                        MINECRAFT_SERVER_HOST.PORT,
                        VOUCHER_CODE.ID,
                        VOUCHER_CODE.CODE,
                        MINECRAFT_SERVER_BID.CREATED_AT)
                .from(MINECRAFT_SERVER_BID)
                .join(MINECRAFT_SERVER).on(MINECRAFT_SERVER.ID.eq(MINECRAFT_SERVER_BID.MINECRAFT_SERVER_ID))
                .join(VOUCHER_CODE).on(VOUCHER_CODE.ID.eq(MINECRAFT_SERVER_BID.VOUCHER_CODE_ID))
                .leftJoin(PROFILE).on(PROFILE.ID.eq(MINECRAFT_SERVER.OWNER_PROFILE_ID))
                .leftJoin(MINECRAFT_SERVER_HOST).on(MINECRAFT_SERVER_HOST.MINECRAFT_SERVER_ID.eq(MINECRAFT_SERVER.ID)
                        .and(MINECRAFT_SERVER_HOST.TYPE.eq(MinecraftServerHostInfo.Type.HOST)))
                .where(condition)
                .orderBy(MINECRAFT_SERVER_BID.CREATED_AT, MINECRAFT_SERVER.ID)
                .fetch(record -> new ServerBid(
                        record.value1(), record.value2(), record.value3(), record.value4(), record.value5(),
                        record.value6(), record.value7(), record.value8(), record.value9(), record.value10()));
    }
}
