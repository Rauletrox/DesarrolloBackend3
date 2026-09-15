package com.backen3.Duoc.dto;

import java.math.BigDecimal;

public record WebAnnualReportResponse(
        Long cuentaId,
        Integer anio,
        BigDecimal totalDepositos,
        BigDecimal totalRetirosCompras,
        BigDecimal saldoNeto,
        Integer cantidadMovimientos) {
}
