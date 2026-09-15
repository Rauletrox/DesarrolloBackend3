package com.backen3.Duoc.bff;

import com.backen3.Duoc.dto.MobileAccountResponse;
import com.backen3.Duoc.dto.MobileMovementResponse;
import com.backen3.Duoc.service.BffBankingService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bff/mobile")
public class MobileBffController {

    private final BffBankingService bffBankingService;

    public MobileBffController(BffBankingService bffBankingService) {
        this.bffBankingService = bffBankingService;
    }

    @GetMapping("/cuentas")
    public List<MobileAccountResponse> cuentas() {
        return bffBankingService.getMobileAccounts();
    }

    @GetMapping("/cuentas/{cuentaId}/movimientos")
    public List<MobileMovementResponse> movimientos(@PathVariable Long cuentaId) {
        return bffBankingService.getMobileLatestMovements(cuentaId);
    }
}
