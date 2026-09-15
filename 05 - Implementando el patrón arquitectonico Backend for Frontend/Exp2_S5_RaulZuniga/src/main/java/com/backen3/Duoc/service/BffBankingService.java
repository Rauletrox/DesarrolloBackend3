package com.backen3.Duoc.service;

import com.backen3.Duoc.dto.AtmBalanceResponse;
import com.backen3.Duoc.dto.AtmWithdrawalResponse;
import com.backen3.Duoc.dto.MobileAccountResponse;
import com.backen3.Duoc.dto.MobileMovementResponse;
import com.backen3.Duoc.dto.WebAccountDetailResponse;
import com.backen3.Duoc.dto.WebAnnualReportResponse;
import com.backen3.Duoc.dto.WebDailySummaryResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BffBankingService {

    private final JdbcClient jdbcClient;

    public BffBankingService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<WebAccountDetailResponse> getWebAccounts() {
        return jdbcClient.sql("""
                        SELECT cuenta_id, nombre, edad, tipo, saldo, tasa_interes, interes_calculado, saldo_final
                        FROM intereses
                        ORDER BY cuenta_id
                        """)
                .query((rs, rowNum) -> new WebAccountDetailResponse(
                        rs.getLong("cuenta_id"),
                        rs.getString("nombre"),
                        rs.getInt("edad"),
                        rs.getString("tipo"),
                        rs.getBigDecimal("saldo"),
                        rs.getBigDecimal("tasa_interes"),
                        rs.getBigDecimal("interes_calculado"),
                        rs.getBigDecimal("saldo_final")))
                .list();
    }

    public List<WebDailySummaryResponse> getWebDailySummaries() {
        return jdbcClient.sql("""
                        SELECT fecha, total_debitos, total_creditos, total_movimientos, cantidad_anomalias
                        FROM resumen_transacciones_diarias
                        ORDER BY fecha
                        """)
                .query((rs, rowNum) -> new WebDailySummaryResponse(
                        rs.getObject("fecha", LocalDate.class),
                        rs.getBigDecimal("total_debitos"),
                        rs.getBigDecimal("total_creditos"),
                        rs.getInt("total_movimientos"),
                        rs.getInt("cantidad_anomalias")))
                .list();
    }

    public List<WebAnnualReportResponse> getWebAnnualReports() {
        return jdbcClient.sql("""
                        SELECT cuenta_id, anio, total_depositos, total_retiros_compras, saldo_neto, cantidad_movimientos
                        FROM informe_estados_cuenta_anuales
                        ORDER BY cuenta_id, anio
                        """)
                .query((rs, rowNum) -> new WebAnnualReportResponse(
                        rs.getLong("cuenta_id"),
                        rs.getInt("anio"),
                        rs.getBigDecimal("total_depositos"),
                        rs.getBigDecimal("total_retiros_compras"),
                        rs.getBigDecimal("saldo_neto"),
                        rs.getInt("cantidad_movimientos")))
                .list();
    }

    public List<MobileAccountResponse> getMobileAccounts() {
        return jdbcClient.sql("""
                        SELECT cuenta_id, nombre, saldo_final, tipo
                        FROM intereses
                        ORDER BY cuenta_id
                        """)
                .query((rs, rowNum) -> new MobileAccountResponse(
                        rs.getLong("cuenta_id"),
                        rs.getString("nombre"),
                        rs.getBigDecimal("saldo_final"),
                        rs.getString("tipo")))
                .list();
    }

    public List<MobileMovementResponse> getMobileLatestMovements(Long cuentaId) {
        return jdbcClient.sql("""
                        SELECT fecha, monto, transaccion
                        FROM cuentas_anuales
                        WHERE cuenta_id = :cuentaId
                        ORDER BY fecha DESC
                        LIMIT 5
                        """)
                .param("cuentaId", cuentaId)
                .query((rs, rowNum) -> new MobileMovementResponse(
                        rs.getObject("fecha", LocalDate.class),
                        rs.getBigDecimal("monto"),
                        rs.getString("transaccion")))
                .list();
    }

    public AtmBalanceResponse getAtmBalance(Long cuentaId) {
        BigDecimal balance = findBalance(cuentaId);
        return new AtmBalanceResponse(cuentaId, balance, "OPERATIVA", "ATM");
    }

    @Transactional
    public AtmWithdrawalResponse withdrawFromAtm(Long cuentaId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a cero");
        }
        if (amount.remainder(new BigDecimal("10000")).signum() != 0) {
            throw new IllegalArgumentException("El cajero solo permite retiros en multiplos de 10000");
        }

        BigDecimal currentBalance = findBalance(cuentaId);
        if (currentBalance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Saldo insuficiente");
        }

        BigDecimal newBalance = currentBalance.subtract(amount);
        jdbcClient.sql("""
                        UPDATE intereses
                        SET saldo_final = :saldoFinal, saldo = :saldoFinal
                        WHERE cuenta_id = :cuentaId
                        """)
                .param("saldoFinal", newBalance)
                .param("cuentaId", cuentaId)
                .update();

        return new AtmWithdrawalResponse(
                cuentaId,
                amount,
                newBalance,
                UUID.randomUUID().toString(),
                "Retiro aprobado");
    }

    private BigDecimal findBalance(Long cuentaId) {
        return jdbcClient.sql("""
                        SELECT saldo_final
                        FROM intereses
                        WHERE cuenta_id = :cuentaId
                        """)
                .param("cuentaId", cuentaId)
                .query(BigDecimal.class)
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("Cuenta no encontrada"));
    }
}
