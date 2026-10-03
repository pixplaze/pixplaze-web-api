package com.pixplaze.api.web.controller;

import com.pixplaze.api.web.data.db.tables.pojos.VoucherCode;
import com.pixplaze.api.web.data.voucher.VoucherCodeType;
import com.pixplaze.api.web.exception.voucher.VoucherCodeValidationException;
import com.pixplaze.api.web.service.VoucherCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/vouchers")
@RequiredArgsConstructor
public class VoucherCodeController {
    private final VoucherCodeService voucherCodeService;

    @PostMapping("/validate")
    public boolean isVoucherCodeValid(@RequestBody VoucherCode voucherCode) {
        try {
            voucherCodeService.validate(voucherCode);
            return true;
        } catch (VoucherCodeValidationException e) {
            return false;
        }
    }

    @PostMapping("/validate/{voucherCode}")
    public boolean isVoucherCodeValid(@PathVariable String voucherCode) {
        try {
            voucherCodeService.load(voucherCode);
            return true;
        } catch (VoucherCodeValidationException e) {
            return false;
        }
    }

    @PostMapping("/invite/validate/{voucherCode}")
    public boolean isInviteVoucherCodeValid(@PathVariable String voucherCode) {
        try {
            voucherCodeService.load(voucherCode, VoucherCodeType.INVITE);
            return true;
        } catch (VoucherCodeValidationException e) {
            return false;
        }
    }

    @GetMapping("/invite/message/{voucherCode}")
    public String getInviteCodeMessage(@PathVariable String voucherCode) {
        // VoucherCodeValidationException (→403) обрабатывается централизованно в ApiExceptionHandler.
        return voucherCodeService.load(voucherCode, VoucherCodeType.INVITE).getMessage();
    }
}
