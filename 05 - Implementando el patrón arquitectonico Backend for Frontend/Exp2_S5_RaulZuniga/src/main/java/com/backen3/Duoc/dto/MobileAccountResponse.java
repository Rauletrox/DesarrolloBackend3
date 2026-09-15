package com.backen3.Duoc.dto;

import java.math.BigDecimal;

public record MobileAccountResponse(
        Long cuentaId,
        String nombre,
        BigDecimal saldo,
        String tipo) {
}
