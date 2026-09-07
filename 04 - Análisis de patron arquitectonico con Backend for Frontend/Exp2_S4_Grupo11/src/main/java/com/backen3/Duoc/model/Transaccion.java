package com.backen3.Duoc.model;

import java.math.BigDecimal;
import java.time.LocalDate;

// Representa una fila del archivo input/transacciones.csv.
public class Transaccion {

    private Long id;
    private LocalDate fecha;
    private BigDecimal monto;
    private String tipo;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }
}
