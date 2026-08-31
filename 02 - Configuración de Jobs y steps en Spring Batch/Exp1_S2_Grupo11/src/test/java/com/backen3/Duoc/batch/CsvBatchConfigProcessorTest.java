package com.backen3.Duoc.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.backen3.Duoc.model.CuentaAnual;
import com.backen3.Duoc.model.Interes;
import com.backen3.Duoc.model.Transaccion;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.step.skip.SkipLimitExceededException;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryPolicy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class CsvBatchConfigProcessorTest {

    private final CsvBatchConfig configuracion = new CsvBatchConfig();

    // Se valida la normalizacion actual para proteger el procesamiento de transacciones validas.
    @Test
    void debeProcesarTransaccionValida() throws Exception {
        Transaccion transaccion = new Transaccion();
        transaccion.setId(1L);
        transaccion.setFecha(LocalDate.of(2024, 1, 1));
        transaccion.setMonto(new BigDecimal("1000"));
        transaccion.setTipo(" Credito ");

        Transaccion resultado = configuracion.transaccionProcessor().process(transaccion);

        assertThat(resultado).isSameAs(transaccion);
        assertThat(resultado.getTipo()).isEqualTo("credito");
    }

    // Se mantiene el filtro actual para un movimiento sin efecto contable.
    @Test
    void debeFiltrarTransaccionConMontoCero() throws Exception {
        Transaccion transaccion = new Transaccion();
        transaccion.setId(2L);
        transaccion.setFecha(LocalDate.of(2024, 1, 1));
        transaccion.setMonto(BigDecimal.ZERO);
        transaccion.setTipo("debito");

        assertThat(configuracion.transaccionProcessor().process(transaccion)).isNull();
    }

    // Se valida la regla existente de cuentas de ahorro sin alterar su saldo base.
    @Test
    void debeProcesarInteresCuentaAhorro() throws Exception {
        Interes interes = new Interes();
        interes.setCuentaId(101L);
        interes.setNombre(" Ana ");
        interes.setSaldo(new BigDecimal("5000"));
        interes.setEdad(30);
        interes.setTipo(" Ahorro ");

        Interes resultado = configuracion.interesProcessor().process(interes);

        assertThat(resultado).isSameAs(interes);
        assertThat(resultado.getNombre()).isEqualTo("Ana");
        assertThat(resultado.getTipo()).isEqualTo("ahorro");
    }

    // Se protege el descarte de una edad fuera del rango permitido por la logica actual.
    @Test
    void debeFiltrarInteresConEdadInvalida() throws Exception {
        Interes interes = new Interes();
        interes.setCuentaId(102L);
        interes.setNombre("Luis");
        interes.setSaldo(new BigDecimal("100"));
        interes.setEdad(17);
        interes.setTipo("prestamo");

        assertThat(configuracion.interesProcessor().process(interes)).isNull();
    }

    // Se comprueba la correccion de signo usada para consolidar retiros anuales.
    @Test
    void debeNormalizarRetiroAnual() throws Exception {
        CuentaAnual cuenta = new CuentaAnual();
        cuenta.setCuentaId(101L);
        cuenta.setFecha(LocalDate.of(2024, 3, 15));
        cuenta.setTransaccion(" Retiro ");
        cuenta.setMonto(new BigDecimal("500"));
        cuenta.setDescripcion(" ");

        CuentaAnual resultado = configuracion.cuentaAnualProcessor().process(cuenta);

        assertThat(resultado.getTransaccion()).isEqualTo("retiro");
        assertThat(resultado.getMonto()).isEqualByComparingTo("-500");
        assertThat(resultado.getDescripcion()).isEqualTo("Sin descripcion");
    }

    // Se verifica que la politica omite solo errores de entrada recuperables y respeta su limite.
    @Test
    void debeAplicarPoliticaDeOmisiones() throws Exception {
        assertThat(configuracion.politicaSaltoPersonalizada().shouldSkip(new NumberFormatException("dato"), 0)).isTrue();
        assertThat(configuracion.politicaSaltoPersonalizada().shouldSkip(new IllegalStateException("grave"), 0)).isFalse();
        assertThatThrownBy(() -> configuracion.politicaSaltoPersonalizada()
                .shouldSkip(new NumberFormatException("dato"), 20))
                .isInstanceOf(SkipLimitExceededException.class);
    }

    // Se reintentan solo fallos temporales de acceso y se detienen al alcanzar el limite.
    @Test
    void debeAplicarPoliticaDeReintentos() {
        RetryPolicy politica = configuracion.politicaReintentoPersonalizada();
        RetryContext contexto = politica.open(null);

        politica.registerThrowable(contexto, new CannotAcquireLockException("temporal"));
        assertThat(politica.canRetry(contexto)).isTrue();
        politica.registerThrowable(contexto, new CannotAcquireLockException("temporal"));
        politica.registerThrowable(contexto, new CannotAcquireLockException("temporal"));
        assertThat(politica.canRetry(contexto)).isFalse();
        politica.close(contexto);
    }

    // Se protege la configuracion exigida de cinco registros por chunk y tres hilos limitados.
    @Test
    void debeMantenerChunkYCantidadDeHilosConfigurados() {
        ThreadPoolTaskExecutor executor = configuracion.batchTaskExecutor();

        try {
            assertThat(configuracion.politicaChunkPersonalizada().getChunkSize()).isEqualTo(5);
            assertThat(executor.getCorePoolSize()).isEqualTo(3);
            assertThat(executor.getMaxPoolSize()).isEqualTo(3);
            // Se valida la cola acotada usada para mantener controlada la ejecucion paralela.
            assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(3);
        } finally {
            executor.shutdown();
        }
    }
}
