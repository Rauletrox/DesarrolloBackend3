package com.backen3.Duoc.dto;

import java.math.BigDecimal;

public record AtmBalanceResponse(
        Long cuentaId,
        BigDecimal saldoDisponible,
        String estado,
        String canal) {
}
