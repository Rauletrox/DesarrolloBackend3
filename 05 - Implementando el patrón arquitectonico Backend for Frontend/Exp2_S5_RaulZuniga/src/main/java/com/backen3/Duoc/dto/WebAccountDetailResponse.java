package com.backen3.Duoc.dto;

import java.math.BigDecimal;

public record WebAccountDetailResponse(
        Long cuentaId,
        String nombre,
        Integer edad,
        String tipo,
        BigDecimal saldoInicial,
        BigDecimal tasaInteres,
        BigDecimal interesCalculado,
        BigDecimal saldoFinal) {
}
