package com.backen3.Duoc.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MobileMovementResponse(
        LocalDate fecha,
        BigDecimal monto,
        String tipo) {
}
