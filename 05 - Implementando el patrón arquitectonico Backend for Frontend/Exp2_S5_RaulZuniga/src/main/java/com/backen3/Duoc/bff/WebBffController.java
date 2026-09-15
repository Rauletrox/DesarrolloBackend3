package com.backen3.Duoc.bff;

import com.backen3.Duoc.dto.WebAccountDetailResponse;
import com.backen3.Duoc.dto.WebAnnualReportResponse;
import com.backen3.Duoc.dto.WebDailySummaryResponse;
import com.backen3.Duoc.service.BffBankingService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bff/web")
public class WebBffController {

    private final BffBankingService bffBankingService;

    public WebBffController(BffBankingService bffBankingService) {
        this.bffBankingService = bffBankingService;
    }

    @GetMapping("/cuentas")
    public List<WebAccountDetailResponse> cuentas() {
        return bffBankingService.getWebAccounts();
    }

    @GetMapping("/resumen-transacciones")
    public List<WebDailySummaryResponse> resumenTransacciones() {
        return bffBankingService.getWebDailySummaries();
    }

    @GetMapping("/informe-anual")
    public List<WebAnnualReportResponse> informeAnual() {
        return bffBankingService.getWebAnnualReports();
    }
}
