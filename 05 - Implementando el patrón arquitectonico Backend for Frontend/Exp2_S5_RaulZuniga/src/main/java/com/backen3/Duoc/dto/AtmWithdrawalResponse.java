package com.backen3.Duoc.dto;

import java.math.BigDecimal;

public record AtmWithdrawalResponse(
        Long cuentaId,
        BigDecimal montoRetirado,
        BigDecimal saldoDisponible,
        String autorizacion,
        String mensaje) {
}
