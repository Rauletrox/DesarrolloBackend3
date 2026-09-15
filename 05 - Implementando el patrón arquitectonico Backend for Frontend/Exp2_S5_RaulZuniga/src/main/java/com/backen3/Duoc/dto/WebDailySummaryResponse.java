package com.backen3.Duoc.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WebDailySummaryResponse(
        LocalDate fecha,
        BigDecimal totalDebitos,
        BigDecimal totalCreditos,
        Integer totalMovimientos,
        Integer cantidadAnomalias) {
}
