package com.pixplaze.api.web.repository;

import com.pixplaze.api.web.data.VoucherCode;
import com.pixplaze.api.web.data.user.Profile;
import com.pixplaze.api.web.exception.voucher.VoucherCodeValidationException;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.pixplaze.api.web.data.db.Tables.VOUCHER_CODE;
import static com.pixplaze.api.web.data.db.Tables.VOUCHER_CODE_ACTIVATION;
import static com.pixplaze.api.web.data.db.tables.ProfileTable.PROFILE;

@RequiredArgsConstructor
@Repository
public class VoucherCodeRepository {
    private final DSLContext dslContext;

    public Optional<VoucherCode> getByCode(String code) {
        return dslContext.select()
                .from(VOUCHER_CODE)
                .where(VOUCHER_CODE.CODE.eq(code))
                .fetchOptional()
                .map(vcr -> new VoucherCode(
                        vcr.get(VOUCHER_CODE.ID),
                        vcr.get(VOUCHER_CODE.CODE),
                        vcr.get(VOUCHER_CODE.MESSAGE),
                        vcr.get(VOUCHER_CODE.DESCRIPTION),
                        VoucherCode.Type.valueOf(vcr.get(VOUCHER_CODE.TYPE)),
                        vcr.get(VOUCHER_CODE.CREATED_AT),
                        vcr.get(VOUCHER_CODE.EXPIRES_AT),
                        vcr.get(VOUCHER_CODE.ACTIVATIONS_LIMIT),
                        vcr.get(VOUCHER_CODE.ACTIVATIONS_REMAINING)
                )).or(Optional::empty);
    }

    public void create(VoucherCode voucherCode) {
        dslContext.insertInto(VOUCHER_CODE)
                .set(VOUCHER_CODE.CODE, voucherCode.code())
                .set(VOUCHER_CODE.DESCRIPTION, voucherCode.description())
                .set(VOUCHER_CODE.TYPE, voucherCode.type().name())
                .set(VOUCHER_CODE.EXPIRES_AT, voucherCode.expires())
                .set(VOUCHER_CODE.ACTIVATIONS_LIMIT, voucherCode.activationsLimit())
                .set(VOUCHER_CODE.ACTIVATIONS_REMAINING, voucherCode.activationsLimit())
                .execute();
    }

    public void update(VoucherCode voucherCode) {
        final var activationsRemainingExpression = VOUCHER_CODE.ACTIVATIONS_REMAINING.plus(
                DSL.value(voucherCode.activationsLimit()).minus(VOUCHER_CODE.ACTIVATIONS_LIMIT)
        );
        dslContext.update(VOUCHER_CODE)
                .set(VOUCHER_CODE.CODE, voucherCode.code())
                .set(VOUCHER_CODE.DESCRIPTION, voucherCode.description())
                .set(VOUCHER_CODE.TYPE, voucherCode.type().name())
                .set(VOUCHER_CODE.EXPIRES_AT, voucherCode.expires())
                .set(VOUCHER_CODE.ACTIVATIONS_LIMIT, voucherCode.activationsLimit())
                .set(VOUCHER_CODE.ACTIVATIONS_REMAINING, activationsRemainingExpression)
                .where(VOUCHER_CODE.ID.eq(voucherCode.id()))
                .execute();
    }

    @Transactional
    public void activate(VoucherCode voucherCode, Profile profile) {
        final var voucherCodesRowsAffected = dslContext.update(VOUCHER_CODE)
                .set(VOUCHER_CODE.ACTIVATIONS_REMAINING, VOUCHER_CODE.ACTIVATIONS_REMAINING.minus(1))
                .where(VOUCHER_CODE.ID.equal(voucherCode.id()))
                .and(VOUCHER_CODE.ACTIVATIONS_REMAINING.greaterThan(0).or(VOUCHER_CODE.ACTIVATIONS_LIMIT.eq(-1)))
                .and(VOUCHER_CODE.EXPIRES_AT.isNull().or(VOUCHER_CODE.EXPIRES_AT.greaterOrEqual(DSL.currentOffsetDateTime())))
                .execute();

        if (voucherCodesRowsAffected == 0) {
            throw new VoucherCodeValidationException("Voucher code '%s' exhausted and can not be activated!".formatted(voucherCode.code()));
        }

        final var voucherCodesActivationsRowsAffected = dslContext.insertInto(VOUCHER_CODE_ACTIVATION)
                .set(VOUCHER_CODE_ACTIVATION.VOUCHER_CODE_ID, voucherCode.id())
                .set(VOUCHER_CODE_ACTIVATION.PROFILE_ID, profile.getId())
                .onConflictDoNothing()
                .execute();


        if (voucherCodesActivationsRowsAffected == 0) {
            throw new VoucherCodeValidationException("Voucher code has already been activated by user '%s'!".formatted(profile.getName()));
        }
    }

    public void delete(Long id) {
        dslContext.delete(VOUCHER_CODE)
                .where(VOUCHER_CODE.ID.eq(id))
                .execute();
    }

    public void addVoucherActivation(VoucherCode voucherCode, Profile profile) {
        dslContext.insertInto(VOUCHER_CODE_ACTIVATION)
                .set(VOUCHER_CODE_ACTIVATION.VOUCHER_CODE_ID, Objects.requireNonNull(voucherCode.id(), "Voucher code `id` field must not be null!"))
                .set(VOUCHER_CODE_ACTIVATION.PROFILE_ID, Objects.requireNonNull(profile.getId(), "User `id` field must not be null!"))
                .set(VOUCHER_CODE_ACTIVATION.ACTIVATED_AT, DSL.currentOffsetDateTime())
                .execute();
    }

    public List<Map<String, Object>> getVoucherCodesActivations() {
        return dslContext.select()
                .from(VOUCHER_CODE_ACTIVATION)
                .join(VOUCHER_CODE).on(VOUCHER_CODE_ACTIVATION.VOUCHER_CODE_ID.eq(VOUCHER_CODE.ID))
                .join(PROFILE).on(VOUCHER_CODE_ACTIVATION.PROFILE_ID.eq(PROFILE.ID))
                .fetchMaps();
    }
}
