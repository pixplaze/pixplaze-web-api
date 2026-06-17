package com.pixplaze.api.web.service;

import com.pixplaze.api.web.data.VoucherCode;
import com.pixplaze.api.web.data.user.Profile;
import com.pixplaze.api.web.exception.voucher.VoucherCodeValidationException;
import com.pixplaze.api.web.repository.VoucherCodeRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@RequiredArgsConstructor
@Service
public class VoucherCodeService {
    private final VoucherCodeRepository voucherCodeRepository;

    public Optional<VoucherCode> getByCode(String code) {
        return voucherCodeRepository.getByCode(code);
    }

    public void create(VoucherCode voucherCode) {
        voucherCodeRepository.create(voucherCode);
    }

    public void update(VoucherCode voucherCode) {
        voucherCodeRepository.update(voucherCode);
    }

    public void delete(VoucherCode voucherCode) {
        voucherCodeRepository.delete(voucherCode.id());
    }

    public void activate(VoucherCode voucherCode, Profile profile) {
        voucherCodeRepository.activate(voucherCode, profile);
    }

    /// Validates and loads voucher code DTO
    /// @return {@link VoucherCode} if validation successful,
    /// otherwise throws {@link VoucherCodeValidationException}
    public @NonNull VoucherCode load(String code) throws VoucherCodeValidationException {
        final var voucherCode = getByCode(validate(code))
                .orElseThrow(onVoucherCodeIsNotExistException(code));
        return validate(voucherCode, voucherCode.type());
    }

    public @NonNull VoucherCode load(String code, VoucherCode.Type type) throws VoucherCodeValidationException {
        final var voucherCode = getByCode(validate(code))
                .orElseThrow(onVoucherCodeIsNotExistException(code));
        return validate(voucherCode, type);
    }

    public String validate(String code) throws VoucherCodeValidationException {
        if (Objects.isNull(code)) {
            throw new VoucherCodeValidationException("Voucher code must not be null!");
        }

        if (!isVoucherCodeMatchPattern(code)) {
            throw new VoucherCodeValidationException("Voucher code '%s' does not match pattern!".formatted(code));
        }

        return code;
    }

    /// Validates provided {@code voucherCode}
    /// @return same {@link VoucherCode} object if voucher code is valid
    /// @throws VoucherCodeValidationException if voucher cod is invalid
    public VoucherCode validate(VoucherCode voucherCode) throws VoucherCodeValidationException {
        return validate(voucherCode, null);
    }

    /// Validates provided {@code voucherCode} with specific {@link VoucherCode.Type} type
    /// @return same {@link VoucherCode} object if voucher code is valid and {@code type} matches
    /// @throws VoucherCodeValidationException if voucher code is invalid or {@code type} not matches
    public VoucherCode validate(VoucherCode voucherCode, VoucherCode.Type type) {
        if (!isVoucherCodeNotNull(voucherCode)) {
            throw new VoucherCodeValidationException("Voucher code must not be null!");
        }

        if (!isVoucherCodeNotExpired(voucherCode)) {
            throw new VoucherCodeValidationException("Voucher code '%s' is expired!".formatted(voucherCode.code()));
        }

        if (!isVoucherCodeActivationsRemaining(voucherCode)) {
            throw new VoucherCodeValidationException("Voucher code '%s' exhausted and can not been activated!".formatted(voucherCode.code()));
        }

        if (!isVoucherCodeMatchType(voucherCode, type)) {
            throw new VoucherCodeValidationException("Voucher code '%s' is not acceptable as voucher of type '%s'!".formatted(voucherCode.code(), VoucherCode.Type.INVITE));
        }

        return voucherCode;
    }

    private boolean isVoucherCodeNotNull(VoucherCode voucherCode) {
        return Objects.nonNull(voucherCode);
    }

    private boolean isVoucherCodeMatchPattern(String code) {
        final var pattern = Pattern.compile("[A-Z0-9]{8}");
        return pattern.matcher(code).matches();
    }

    private boolean isVoucherCodeActivationsRemaining(VoucherCode voucherCode) {
        return voucherCode.activationsLimit() == -1 || voucherCode.activationsRemaining() > 0;
    }

    public boolean isVoucherCodeNotExpired(VoucherCode voucherCode) {
        return voucherCode.expires() == null || voucherCode.expires().isAfter(OffsetDateTime.now());
    }

    private boolean isVoucherCodeMatchType(VoucherCode voucherCode, VoucherCode.Type type) {
        return type == null || type.equals(voucherCode.type());
    }

    private Supplier<VoucherCodeValidationException> onVoucherCodeIsNotExistException(String code) {
        return () -> new VoucherCodeValidationException("Voucher code '%s' is not exist!".formatted(code));
    }
}
