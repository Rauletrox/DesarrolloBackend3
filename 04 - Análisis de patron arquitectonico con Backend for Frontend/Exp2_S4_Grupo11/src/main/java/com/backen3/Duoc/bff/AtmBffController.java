package com.backen3.Duoc.bff;

import com.backen3.Duoc.dto.AtmBalanceResponse;
import com.backen3.Duoc.dto.AtmWithdrawalRequest;
import com.backen3.Duoc.dto.AtmWithdrawalResponse;
import com.backen3.Duoc.service.BffBankingService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bff/atm")
public class AtmBffController {

    private final BffBankingService bffBankingService;

    public AtmBffController(BffBankingService bffBankingService) {
        this.bffBankingService = bffBankingService;
    }

    @GetMapping("/cuentas/{cuentaId}/saldo")
    public AtmBalanceResponse saldo(@PathVariable Long cuentaId) {
        return bffBankingService.getAtmBalance(cuentaId);
    }

    @PostMapping("/cuentas/{cuentaId}/retiro")
    public AtmWithdrawalResponse retiro(@PathVariable Long cuentaId, @RequestBody AtmWithdrawalRequest request) {
        return bffBankingService.withdrawFromAtm(cuentaId, request.monto());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleIllegalArgument(IllegalArgumentException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    public record ErrorResponse(String error) {
    }
}
