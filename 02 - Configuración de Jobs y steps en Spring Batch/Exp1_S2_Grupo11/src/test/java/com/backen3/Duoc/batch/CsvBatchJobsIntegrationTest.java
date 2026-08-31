package com.backen3.Duoc.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.backen3.Duoc.DuocApplication;
import com.backen3.Duoc.model.Transaccion;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest(classes = DuocApplication.class)
@ActiveProfiles("test")
@Import(CsvBatchJobsIntegrationTest.ConfiguracionExecutorDePrueba.class)
class CsvBatchJobsIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class ConfiguracionExecutorDePrueba {

        // Se usa un executor sincrono en H2 para mantener las pruebas deterministas y aisladas del paralelismo real.
        @Bean
        @Primary
        TaskExecutor executorDePrueba() {
            return new SyncTaskExecutor();
        }
    }

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    @Qualifier("reporteTransaccionesDiariasJob")
    private Job reporteTransaccionesDiariasJob;

    @Autowired
    @Qualifier("calculoInteresesMensualesJob")
    private Job calculoInteresesMensualesJob;

    @Autowired
    @Qualifier("estadosCuentaAnualesJob")
    private Job estadosCuentaAnualesJob;

    @Autowired
    private ItemProcessor<Transaccion, Transaccion> transaccionProcessor;

    @Autowired
    private JdbcBatchItemWriter<Transaccion> transaccionWriter;

    @Autowired
    private SkipPolicy politicaSaltoPersonalizada;

    // Se limpia el estado funcional para que cada prueba use una base aislada e independiente.
    @BeforeEach
    void limpiarTablas() {
        jdbcTemplate.update("DELETE FROM resumen_transacciones_diarias");
        jdbcTemplate.update("DELETE FROM informe_estados_cuenta_anuales");
        jdbcTemplate.update("DELETE FROM cuentas_anuales");
        jdbcTemplate.update("DELETE FROM intereses");
        jdbcTemplate.update("DELETE FROM transacciones");
    }

    // Se ejecuta el flujo CSV completo y se protegen sus conteos y resumen conocidos.
    @Test
    void debeProcesarYMantenerResultadoDeTransacciones() throws Exception {
        JobExecution ejecucion = ejecutar(reporteTransaccionesDiariasJob);
        StepExecution step = buscarStep(ejecucion, "transaccionesStep");

        assertThat(ejecucion.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(step.getReadCount()).isEqualTo(10);
        assertThat(step.getFilterCount()).isEqualTo(1);
        assertThat(step.getWriteCount()).isEqualTo(9);
        assertThat(step.getSkipCount()).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transacciones", Integer.class)).isEqualTo(9);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM resumen_transacciones_diarias", Integer.class)).isEqualTo(8);
        assertThat(jdbcTemplate.queryForObject("SELECT SUM(cantidad_anomalias) FROM resumen_transacciones_diarias", Integer.class))
                .isEqualTo(1);
    }

    // Se comprueba el calculo existente de ahorro y prestamo mediante el Job real y su persistencia.
    @Test
    void debeProcesarYMantenerResultadoDeIntereses() throws Exception {
        JobExecution ejecucion = ejecutar(calculoInteresesMensualesJob);
        StepExecution step = buscarStep(ejecucion, "interesesStep");

        assertThat(ejecucion.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(step.getReadCount()).isEqualTo(8);
        assertThat(step.getFilterCount()).isZero();
        assertThat(step.getWriteCount()).isEqualTo(8);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM intereses", Integer.class)).isEqualTo(8);
        assertThat(valorDecimal("SELECT saldo_final FROM intereses WHERE cuenta_id = 101"))
                .isEqualByComparingTo("5075.00");
        assertThat(valorDecimal("SELECT saldo_final FROM intereses WHERE cuenta_id = 102"))
                .isEqualByComparingTo("8200.00");
    }

    // Se verifica la consolidacion anual conocida y la correccion de signos antes de persistir.
    @Test
    void debeProcesarYMantenerResultadoDeEstadosAnuales() throws Exception {
        JobExecution ejecucion = ejecutar(estadosCuentaAnualesJob);
        StepExecution step = buscarStep(ejecucion, "cuentasAnualesStep");

        assertThat(ejecucion.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(step.getReadCount()).isEqualTo(9);
        assertThat(step.getFilterCount()).isEqualTo(1);
        assertThat(step.getWriteCount()).isEqualTo(8);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cuentas_anuales", Integer.class)).isEqualTo(8);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM informe_estados_cuenta_anuales", Integer.class))
                .isEqualTo(7);
        assertThat(valorDecimal("SELECT saldo_neto FROM informe_estados_cuenta_anuales WHERE cuenta_id = 101"))
                .isEqualByComparingTo("500.00");

        // Se reejecuta el Job para proteger que el archivo anual no acumule movimientos duplicados.
        JobExecution reejecucion = ejecutar(estadosCuentaAnualesJob);
        assertThat(reejecucion.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cuentas_anuales", Integer.class)).isEqualTo(8);
    }

    // Se usa un CSV pequeno con una fila invalida para comprobar que el Step continua mediante SkipPolicy.
    @Test
    void debeContinuarAnteErrorRecuperableDeLectura() throws Exception {
        var reader = new FlatFileItemReaderBuilder<Transaccion>()
                .name("transaccionReaderConError")
                .resource(new ClassPathResource("transacciones_con_error_test.csv"))
                .linesToSkip(1)
                .saveState(false)
                .delimited()
                .names("id", "fecha", "monto", "tipo")
                .fieldSetMapper(fieldSet -> {
                    Transaccion transaccion = new Transaccion();
                    transaccion.setId(fieldSet.readLong("id"));
                    transaccion.setFecha(LocalDate.parse(fieldSet.readString("fecha")));
                    transaccion.setMonto(new BigDecimal(fieldSet.readString("monto")));
                    transaccion.setTipo(fieldSet.readString("tipo"));
                    return transaccion;
                })
                .build();
        Step step = new StepBuilder("toleranciaTransaccionesStep" + System.nanoTime(), jobRepository)
                .<Transaccion, Transaccion>chunk(5, transactionManager)
                .reader(reader)
                .processor(transaccionProcessor)
                .writer(transaccionWriter)
                .faultTolerant()
                .skipPolicy(politicaSaltoPersonalizada)
                .build();
        Job job = new JobBuilder("toleranciaTransaccionesJob" + System.nanoTime(), jobRepository)
                .start(step)
                .build();

        JobExecution ejecucion = ejecutar(job);
        StepExecution ejecucionStep = ejecucion.getStepExecutions().iterator().next();

        assertThat(ejecucion.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(ejecucionStep.getReadCount()).isEqualTo(2);
        assertThat(ejecucionStep.getSkipCount()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM transacciones", Integer.class)).isEqualTo(2);
    }

    // Ejecuta cada Job con un parametro unico para mantener independientes las pruebas.
    private JobExecution ejecutar(Job job) throws Exception {
        return jobLauncher.run(job, new JobParametersBuilder()
                .addLong("run.id", System.nanoTime())
                .toJobParameters());
    }

    // Busca el Step solicitado para validar sus contadores y estado final.
    private StepExecution buscarStep(JobExecution ejecucion, String nombreStep) {
        return ejecucion.getStepExecutions().stream()
                .filter(step -> step.getStepName().equals(nombreStep))
                .findFirst()
                .orElseThrow();
    }

    // Obtiene un valor decimal persistido para comparar resultados conocidos.
    private BigDecimal valorDecimal(String consulta) {
        return jdbcTemplate.queryForObject(consulta, BigDecimal.class);
    }
}
