package com.pixplaze.api.web.controller;

import com.pixplaze.api.web.data.VoucherCode;
import com.pixplaze.api.web.data.dto.ErrorResponseInfo;
import com.pixplaze.api.web.exception.voucher.VoucherCodeValidationException;
import com.pixplaze.api.web.service.ExceptionHandlerService;
import com.pixplaze.api.web.service.VoucherCodeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/vouchers")
@RequiredArgsConstructor
public class VoucherCodeController {
    private final VoucherCodeService voucherCodeService;
    private final ExceptionHandlerService exceptionHandlerService;

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
            voucherCodeService.load(voucherCode, VoucherCode.Type.INVITE);
            return true;
        } catch (VoucherCodeValidationException e) {
            return false;
        }
    }

    @GetMapping("/invite/message/{voucherCode}")
    public String getInviteCodeMessage(@PathVariable String voucherCode) {
        return voucherCodeService.load(voucherCode, VoucherCode.Type.INVITE).message();
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponseInfo> handleVoucherCodeValidationException(RuntimeException exception, HttpServletRequest httpServletRequest) {
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
                .body(exceptionHandlerService.handleException(exception, httpServletRequest));
    }
}
