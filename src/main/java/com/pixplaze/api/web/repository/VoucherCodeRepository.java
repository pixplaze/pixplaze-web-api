package com.pixplaze.api.web.repository;

import com.pixplaze.api.web.data.VoucherCode;
import com.pixplaze.api.web.data.user.User;
import com.pixplaze.api.web.exception.voucher.VoucherCodeValidationException;
import com.pixplaze.web.api.data.db.tables.Users;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.pixplaze.web.api.data.db.Tables.VOUCHER_CODES;
import static com.pixplaze.web.api.data.db.Tables.VOUCHER_CODES_ACTIVATIONS;

@RequiredArgsConstructor
@Repository
public class VoucherCodeRepository {
    private final DSLContext dslContext;

    public Optional<VoucherCode> getByCode(String code) {
        return dslContext.select()
                .from(VOUCHER_CODES)
                .where(VOUCHER_CODES.CODE.eq(code))
                .fetchOptional()
                .map(vcr -> new VoucherCode(
                        vcr.get(VOUCHER_CODES.ID),
                        vcr.get(VOUCHER_CODES.CODE),
                        vcr.get(VOUCHER_CODES.DESCRIPTION),
                        VoucherCode.Type.valueOf(vcr.get(VOUCHER_CODES.TYPE)),
                        vcr.get(VOUCHER_CODES.CREATED_AT),
                        vcr.get(VOUCHER_CODES.EXPIRES_AT),
                        vcr.get(VOUCHER_CODES.ACTIVATIONS_LIMIT),
                        vcr.get(VOUCHER_CODES.ACTIVATIONS_REMAINING)
                )).or(Optional::empty);
    }

    public void create(VoucherCode voucherCode) {
        dslContext.insertInto(VOUCHER_CODES)
                .set(VOUCHER_CODES.CODE, voucherCode.code())
                .set(VOUCHER_CODES.DESCRIPTION, voucherCode.description())
                .set(VOUCHER_CODES.TYPE, voucherCode.type().name())
                .set(VOUCHER_CODES.EXPIRES_AT, voucherCode.expires())
                .set(VOUCHER_CODES.ACTIVATIONS_LIMIT, voucherCode.activationsLimit())
                .set(VOUCHER_CODES.ACTIVATIONS_REMAINING, voucherCode.activationsLimit())
                .execute();
    }

    public void update(VoucherCode voucherCode) {
        final var activationsRemainingExpression = VOUCHER_CODES.ACTIVATIONS_REMAINING.plus(
                DSL.value(voucherCode.activationsLimit()).minus(VOUCHER_CODES.ACTIVATIONS_LIMIT)
        );
        dslContext.update(VOUCHER_CODES)
                .set(VOUCHER_CODES.CODE, voucherCode.code())
                .set(VOUCHER_CODES.DESCRIPTION, voucherCode.description())
                .set(VOUCHER_CODES.TYPE, voucherCode.type().name())
                .set(VOUCHER_CODES.EXPIRES_AT, voucherCode.expires())
                .set(VOUCHER_CODES.ACTIVATIONS_LIMIT, voucherCode.activationsLimit())
                .set(VOUCHER_CODES.ACTIVATIONS_REMAINING, activationsRemainingExpression)
                .where(VOUCHER_CODES.ID.eq(voucherCode.id()))
                .execute();
    }

    @Transactional
    public void activate(VoucherCode voucherCode, User user) {
        final var voucherCodesRowsAffected = dslContext.update(VOUCHER_CODES)
                .set(VOUCHER_CODES.ACTIVATIONS_REMAINING, VOUCHER_CODES.ACTIVATIONS_REMAINING.minus(1))
                .where(VOUCHER_CODES.ID.equal(voucherCode.id()))
                .and(VOUCHER_CODES.ACTIVATIONS_REMAINING.greaterThan(0).or(VOUCHER_CODES.ACTIVATIONS_LIMIT.eq(-1)))
                .and(VOUCHER_CODES.EXPIRES_AT.isNull().or(VOUCHER_CODES.EXPIRES_AT.greaterOrEqual(DSL.currentOffsetDateTime())))
                .execute();

        if (voucherCodesRowsAffected == 0) {
            throw new VoucherCodeValidationException("Voucher code '%s' exhausted and can not be activated!".formatted(voucherCode.code()));
        }

        final var voucherCodesActivationsRowsAffected = dslContext.insertInto(VOUCHER_CODES_ACTIVATIONS)
                .set(VOUCHER_CODES_ACTIVATIONS.VOUCHER_CODE_ID, voucherCode.id())
                .set(VOUCHER_CODES_ACTIVATIONS.USER_ID, user.getId())
                .onConflictDoNothing()
                .execute();


        if (voucherCodesActivationsRowsAffected == 0) {
            throw new VoucherCodeValidationException("Voucher code has already been activated by user '%s'!".formatted(user.getName()));
        }
    }

    public void delete(Long id) {
        dslContext.delete(VOUCHER_CODES)
                .where(VOUCHER_CODES.ID.eq(id))
                .execute();
    }

    public void addVoucherActivation(VoucherCode voucherCode, User user) {
        dslContext.insertInto(VOUCHER_CODES_ACTIVATIONS)
                .set(VOUCHER_CODES_ACTIVATIONS.VOUCHER_CODE_ID, Objects.requireNonNull(voucherCode.id(), "Voucher code `id` field must not be null!"))
                .set(VOUCHER_CODES_ACTIVATIONS.USER_ID, Objects.requireNonNull(user.getId(), "User `id` field must not be null!"))
                .set(VOUCHER_CODES_ACTIVATIONS.ACTIVATED_AT, DSL.currentOffsetDateTime())
                .execute();
    }

    public List<Map<String, Object>> getVoucherCodesActivations() {
        final var USERS = Users.USERS;
        return dslContext.select()
                .from(VOUCHER_CODES_ACTIVATIONS)
                .join(VOUCHER_CODES).on(VOUCHER_CODES_ACTIVATIONS.VOUCHER_CODE_ID.eq(VOUCHER_CODES.ID))
                .join(USERS).on(VOUCHER_CODES_ACTIVATIONS.USER_ID.eq(USERS.ID))
                .fetchMaps();
    }
}
