package com.backen3.Duoc.batch;

import com.backen3.Duoc.model.CuentaAnual;
import com.backen3.Duoc.model.Interes;
import com.backen3.Duoc.model.Transaccion;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.BeanPropertyItemSqlParameterSourceProvider;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.batch.item.support.SynchronizedItemStreamReader;
import org.springframework.batch.item.support.builder.SynchronizedItemStreamReaderBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.batch.repeat.exception.SimpleLimitExceptionHandler;
import org.springframework.batch.repeat.policy.SimpleCompletionPolicy;
import org.springframework.batch.core.step.skip.SkipLimitExceededException;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.batch.repeat.exception.ExceptionHandler;
import org.springframework.batch.item.ParseException;
import org.springframework.batch.item.UnexpectedInputException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class CsvBatchConfig {

    private static final Logger logger = LoggerFactory.getLogger(CsvBatchConfig.class);
    private static final int CHUNK_SIZE = 5;
    private static final int HILOS_PARALELOS = 3;
    private static final int LIMITE_SALTOS = 20;
    private static final int LIMITE_REINTENTOS = 3;

    // Job 1: procesa transacciones diarias y luego genera un resumen para detectar anomalias.
    @Bean
    public Job reporteTransaccionesDiariasJob(JobRepository jobRepository,
            Step transaccionesStep,
            Step resumenTransaccionesDiariasStep) {
        return new JobBuilder("reporteTransaccionesDiariasJob", jobRepository)
                .start(transaccionesStep)
                .next(resumenTransaccionesDiariasStep)
                .build();
    }

    // Ejecuta los tres Jobs en orden cuando arranca la aplicacion.
    @Bean
    public CommandLineRunner ejecutarJobs(JobLauncher jobLauncher,
            Job reporteTransaccionesDiariasJob,
            Job calculoInteresesMensualesJob,
            Job estadosCuentaAnualesJob) {
        return args -> {
            // run.id cambia en cada ejecucion para que Spring Batch no reutilice una ejecucion anterior.
            long runId = System.currentTimeMillis();

            ejecutarJob(jobLauncher, reporteTransaccionesDiariasJob, runId);
            ejecutarJob(jobLauncher, calculoInteresesMensualesJob, runId + 1);
            ejecutarJob(jobLauncher, estadosCuentaAnualesJob, runId + 2);
        };
    }

    // Centraliza el lanzamiento de Jobs para mantener el runner principal y que sea facil de leer.
    private void ejecutarJob(JobLauncher jobLauncher, Job job, long runId) throws Exception {
        JobParameters parametros = new JobParametersBuilder()
                .addLong("run.id", runId)
                .toJobParameters();

        logger.info("Iniciando Job: {}", job.getName());
        jobLauncher.run(job, parametros);
    }

    // Politica de escalamiento: limita la carga CSV a tres hilos paralelos.
    @Bean(destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor batchTaskExecutor() {
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(HILOS_PARALELOS);
        taskExecutor.setMaxPoolSize(HILOS_PARALELOS);
        taskExecutor.setQueueCapacity(0);
        taskExecutor.setThreadNamePrefix("batch-csv-");
        taskExecutor.setWaitForTasksToCompleteOnShutdown(false);
        taskExecutor.setAwaitTerminationSeconds(5);
        taskExecutor.initialize();
        return taskExecutor;
    }

    // Politica personalizada: omite registros mal formados hasta un limite controlado.
    @Bean
    public SkipPolicy politicaSaltoPersonalizada() {
        return (Throwable throwable, long skipCount) -> {
            if (skipCount >= LIMITE_SALTOS) {
                throw new SkipLimitExceededException(LIMITE_SALTOS, throwable);
            }

            boolean errorRecuperable = throwable instanceof FlatFileParseException
                    || throwable instanceof ParseException
                    || throwable instanceof UnexpectedInputException
                    || throwable instanceof NumberFormatException
                    || throwable instanceof DateTimeParseException
                    || throwable instanceof IllegalArgumentException;

            if (errorRecuperable) {
                logger.warn("Registro omitido por error recuperable: {}", throwable.getMessage());
            }

            return errorRecuperable;
        };
    }

    // Politica personalizada: reintenta errores transitorios de base de datos antes de fallar el Step.
    @Bean
    public RetryPolicy politicaReintentoPersonalizada() {
        Map<Class<? extends Throwable>, Boolean> excepcionesReintentables = new HashMap<>();
        excepcionesReintentables.put(TransientDataAccessException.class, true);
        excepcionesReintentables.put(CannotAcquireLockException.class, true);
        return new SimpleRetryPolicy(LIMITE_REINTENTOS, excepcionesReintentables, true);
    }

    // Politica personalizada del RepeatTemplate usado internamente por los Steps.
    @Bean
    public ExceptionHandler politicaExcepcionesPersonalizada() {
        return new SimpleLimitExceptionHandler(LIMITE_SALTOS);
    }

    @Bean
    public SimpleCompletionPolicy politicaChunkPersonalizada() {
        return new SimpleCompletionPolicy(CHUNK_SIZE);
    }

    // Job 2: carga cuentas desde intereses.csv y calcula el saldo final mensual.
    @Bean
    public Job calculoInteresesMensualesJob(JobRepository jobRepository,
            Step interesesStep,
            Step calcularInteresesMensualesStep) {
        return new JobBuilder("calculoInteresesMensualesJob", jobRepository)
                .start(interesesStep)
                .next(calcularInteresesMensualesStep)
                .build();
    }

    // Job 3: carga movimientos anuales y crea un informe agrupado por cuenta.
    @Bean
    public Job estadosCuentaAnualesJob(JobRepository jobRepository,
            Step cuentasAnualesStep,
            Step informeEstadosCuentaAnualesStep) {
        return new JobBuilder("estadosCuentaAnualesJob", jobRepository)
                .start(cuentasAnualesStep)
                .next(informeEstadosCuentaAnualesStep)
                .build();
    }

    // Este Step genera un resumen diario desde la tabla transacciones ya cargada.
    @Bean
    public Step resumenTransaccionesDiariasStep(JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JdbcTemplate jdbcTemplate) {
        return new StepBuilder("resumenTransaccionesDiariasStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    // Limpiamos el resumen para que refleje la ultima ejecucion del proceso.
                    jdbcTemplate.update("DELETE FROM resumen_transacciones_diarias");

                    // Agrupamos por fecha y calculamos totales, cantidad de movimientos y posibles anomalias.
                    jdbcTemplate.update("""
                            INSERT INTO resumen_transacciones_diarias
                            (fecha, total_debitos, total_creditos, total_movimientos, cantidad_anomalias)
                            SELECT
                                fecha,
                                SUM(CASE WHEN tipo = 'debito' THEN monto ELSE 0 END),
                                SUM(CASE WHEN tipo = 'credito' THEN monto ELSE 0 END),
                                COUNT(*),
                                SUM(CASE WHEN ABS(monto) >= 3000 THEN 1 ELSE 0 END)
                            FROM transacciones
                            GROUP BY fecha
                            """);

                    logger.info("Resumen de transacciones diarias generado correctamente");
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    // Este Step aplica el interes mensual y actualiza saldo_final en la tabla intereses.
    @Bean
    public Step calcularInteresesMensualesStep(JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JdbcTemplate jdbcTemplate) {
        return new StepBuilder("calcularInteresesMensualesStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    // Si la tabla ya existia antes de este cambio, agregamos las columnas calculadas que falten.
                    asegurarColumna(jdbcTemplate, "intereses", "tasa_interes", "DECIMAL(8, 4) DEFAULT 0");
                    asegurarColumna(jdbcTemplate, "intereses", "interes_calculado", "DECIMAL(15, 2) DEFAULT 0");
                    asegurarColumna(jdbcTemplate, "intereses", "saldo_final", "DECIMAL(15, 2) DEFAULT 0");

                    // Para ahorro se suma 1.5%; para prestamos se descuenta/carga 2.5%; hipoteca usa 2.0%.
                    jdbcTemplate.update("""
                            UPDATE intereses
                            SET
                                tasa_interes = CASE
                                    WHEN tipo = 'ahorro' THEN 0.015
                                    WHEN tipo = 'prestamo' THEN 0.025
                                    WHEN tipo = 'hipoteca' THEN 0.020
                                    ELSE 0
                                END,
                                interes_calculado = ROUND(saldo * CASE
                                    WHEN tipo = 'ahorro' THEN 0.015
                                    WHEN tipo = 'prestamo' THEN 0.025
                                    WHEN tipo = 'hipoteca' THEN 0.020
                                    ELSE 0
                                END, 2),
                                saldo_final = ROUND(saldo + CASE
                                    WHEN tipo = 'ahorro' THEN saldo * 0.015
                                    WHEN tipo = 'prestamo' THEN saldo * 0.025
                                    WHEN tipo = 'hipoteca' THEN saldo * 0.020
                                    ELSE 0
                                END, 2)
                            """);

                    logger.info("Intereses mensuales calculados y saldos finales actualizados");
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    // Este Step compila los movimientos anuales por cuenta para dejar un informe de auditoria.
    @Bean
    public Step informeEstadosCuentaAnualesStep(JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JdbcTemplate jdbcTemplate) {
        return new StepBuilder("informeEstadosCuentaAnualesStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    // Limpiamos el informe anterior para generar una vista anual actualizada.
                    jdbcTemplate.update("DELETE FROM informe_estados_cuenta_anuales");

                    // Agrupamos por cuenta y calculamos ingresos, egresos, cantidad de movimientos y saldo neto anual.
                    jdbcTemplate.update("""
                            INSERT INTO informe_estados_cuenta_anuales
                            (cuenta_id, anio, total_depositos, total_retiros_compras, saldo_neto, cantidad_movimientos)
                            SELECT
                                cuenta_id,
                                YEAR(fecha),
                                SUM(CASE WHEN monto > 0 THEN monto ELSE 0 END),
                                SUM(CASE WHEN monto < 0 THEN ABS(monto) ELSE 0 END),
                                SUM(monto),
                                COUNT(*)
                            FROM cuentas_anuales
                            GROUP BY cuenta_id, YEAR(fecha)
                            """);

                    logger.info("Informe anual de estados de cuenta generado correctamente");
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }
    // paso 1: leer transacciones.csv, procesa cada fila y la escribe en MySQL.
    @Bean
    public Step transaccionesStep(JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ItemStreamReader<Transaccion> transaccionReader,
            ItemProcessor<Transaccion, Transaccion> transaccionProcessor,
            JdbcBatchItemWriter<Transaccion> transaccionWriter,
            TaskExecutor batchTaskExecutor,
            SkipPolicy politicaSaltoPersonalizada,
            RetryPolicy politicaReintentoPersonalizada,
            SimpleCompletionPolicy politicaChunkPersonalizada) {
        return new StepBuilder("transaccionesStep", jobRepository)
                .<Transaccion, Transaccion>chunk(politicaChunkPersonalizada, transactionManager)
                .reader(transaccionReader)
                .processor(transaccionProcessor)
                .writer(transaccionWriter)
                .faultTolerant()
                .skipPolicy(politicaSaltoPersonalizada)
                .retryPolicy(politicaReintentoPersonalizada)
                .taskExecutor(batchTaskExecutor)
                .build();
    }

    // Reader del archivo transacciones.csv: salta el encabezado y asigna columnas al modelo.
    @Bean
    public ItemStreamReader<Transaccion> transaccionReader() {
        return new SynchronizedItemStreamReaderBuilder<Transaccion>()
                .delegate(transaccionReaderDelegate())
                .build();
    }

    private FlatFileItemReader<Transaccion> transaccionReaderDelegate() {
        return new FlatFileItemReaderBuilder<Transaccion>()
                .name("transaccionReader")
                .resource(new ClassPathResource("input/transacciones.csv"))
                .linesToSkip(1)
                .saveState(false)
                .delimited()
                .names("id", "fecha", "monto", "tipo")
                .fieldSetMapper(fieldSet -> {
                    // Convertimos manualmente cada columna para controlar tipos como LocalDate y BigDecimal.
                    Transaccion transaccion = new Transaccion();
                    transaccion.setId(fieldSet.readLong("id"));
                    transaccion.setFecha(LocalDate.parse(fieldSet.readString("fecha")));
                    transaccion.setMonto(new BigDecimal(fieldSet.readString("monto")));
                    transaccion.setTipo(fieldSet.readString("tipo"));
                    return transaccion;
                })
                .build();
    }

    // Processor de transacciones: valida datos obligatorios, corrige textos y descarta registros incorrectos.
    @Bean
    public ItemProcessor<Transaccion, Transaccion> transaccionProcessor() {
        return transaccion -> {
            // Si el id o la fecha vienen vacios, no se puede guardar una transaccion confiable.
            if (transaccion.getId() == null || transaccion.getFecha() == null) {
                logger.warn("Transaccion descartada por id o fecha vacia: {}", transaccion.getId());
                return null;
            }

            // Si el monto viene vacio, no se puede calcular ni guardar correctamente.
            if (transaccion.getMonto() == null) {
                logger.warn("Transaccion {} descartada por monto vacio", transaccion.getId());
                return null;
            }

            // El monto cero no representa movimiento real, por eso se filtra.
            if (transaccion.getMonto().signum() == 0) {
                logger.warn("Transaccion {} descartada por monto cero", transaccion.getId());
                return null;
            }

            // Se normaliza el tipo para guardar valores consistentes en la base de datos.
            String tipo = normalizarTexto(transaccion.getTipo());
            if (!tipo.equals("debito") && !tipo.equals("credito")) {
                logger.warn("Transaccion {} descartada por tipo invalido: {}", transaccion.getId(), transaccion.getTipo());
                return null;
            }

            transaccion.setTipo(tipo);
            return transaccion;
        };
    }

    // Writer de transacciones: inserta cada item procesado en la tabla transacciones.
    @Bean
    public JdbcBatchItemWriter<Transaccion> transaccionWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<Transaccion>()
                .dataSource(dataSource)
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .sql("""
                        INSERT INTO transacciones (id, fecha, monto, tipo)
                        VALUES (:id, :fecha, :monto, :tipo)
                        ON DUPLICATE KEY UPDATE fecha = VALUES(fecha), monto = VALUES(monto), tipo = VALUES(tipo)
                        """)
                .build();
    }

    // Paso 2: lee intereses.csv, procesa cada fila y la escribe en MySQL.
    @Bean
    public Step interesesStep(JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ItemStreamReader<Interes> interesReader,
            ItemProcessor<Interes, Interes> interesProcessor,
            JdbcBatchItemWriter<Interes> interesWriter,
            TaskExecutor batchTaskExecutor,
            SkipPolicy politicaSaltoPersonalizada,
            RetryPolicy politicaReintentoPersonalizada,
            SimpleCompletionPolicy politicaChunkPersonalizada) {
        return new StepBuilder("interesesStep", jobRepository)
                .<Interes, Interes>chunk(politicaChunkPersonalizada, transactionManager)
                .reader(interesReader)
                .processor(interesProcessor)
                .writer(interesWriter)
                .faultTolerant()
                .skipPolicy(politicaSaltoPersonalizada)
                .retryPolicy(politicaReintentoPersonalizada)
                .taskExecutor(batchTaskExecutor)
                .build();
    }

    // Reader del archivo intereses.csv: mapea cada columna del CSV a la clase Interes.
    @Bean
    public ItemStreamReader<Interes> interesReader() {
        return new SynchronizedItemStreamReaderBuilder<Interes>()
                .delegate(interesReaderDelegate())
                .build();
    }

    private FlatFileItemReader<Interes> interesReaderDelegate() {
        return new FlatFileItemReaderBuilder<Interes>()
                .name("interesReader")
                .resource(new ClassPathResource("input/intereses.csv"))
                .linesToSkip(1)
                .saveState(false)
                .delimited()
                .names("cuentaId", "nombre", "saldo", "edad", "tipo")
                .fieldSetMapper(fieldSet -> {
                    // Convertimos manualmente cada columna para evitar errores de conversion automatica.
                    Interes interes = new Interes();
                    interes.setCuentaId(fieldSet.readLong("cuentaId"));
                    interes.setNombre(fieldSet.readString("nombre"));
                    interes.setSaldo(new BigDecimal(fieldSet.readString("saldo")));
                    interes.setEdad(fieldSet.readInt("edad"));
                    interes.setTipo(fieldSet.readString("tipo"));
                    return interes;
                })
                .build();
    }

    // Processor de intereses: limpia textos, valida saldos/edades y maneja tipos de cuenta permitidos.
    @Bean
    public ItemProcessor<Interes, Interes> interesProcessor() {
        return interes -> {
            // La cuenta es la clave del registro; si falta, no se puede identificar en MySQL.
            if (interes.getCuentaId() == null) {
                logger.warn("Interes descartado porque no tiene cuenta_id");
                return null;
            }

            // Un nombre vacio queda inutil para reportes, por eso se descarta.
            String nombre = limpiarTexto(interes.getNombre());
            if (nombre.isBlank()) {
                logger.warn("Interes de cuenta {} descartado por nombre vacio", interes.getCuentaId());
                return null;
            }

            // Si el saldo viene vacio, el registro queda incompleto para la tabla intereses.
            if (interes.getSaldo() == null) {
                logger.warn("Interes de cuenta {} descartado por saldo vacio", interes.getCuentaId());
                return null;
            }

            // Se descartan saldos negativos porque este archivo representa saldos base de cuentas.
            if (interes.getSaldo().signum() < 0) {
                logger.warn("Interes de cuenta {} descartado por saldo negativo", interes.getCuentaId());
                return null;
            }

            // Se valida la edad para evitar valores imposibles o registros incompletos.
            if (interes.getEdad() == null || interes.getEdad() < 18 || interes.getEdad() > 120) {
                logger.warn("Interes de cuenta {} descartado por edad invalida: {}", interes.getCuentaId(), interes.getEdad());
                return null;
            }

            // Se normaliza el tipo y se aceptan solo los tipos presentes en el negocio del CSV.
            String tipo = normalizarTexto(interes.getTipo());
            if (!tipo.equals("ahorro") && !tipo.equals("prestamo") && !tipo.equals("hipoteca")) {
                logger.warn("Interes de cuenta {} descartado por tipo invalido: {}", interes.getCuentaId(), interes.getTipo());
                return null;
            }

            interes.setNombre(nombre);
            interes.setTipo(tipo);
            return interes;
        };
    }

    // Writer de intereses: guarda los datos de cuentas/intereses en MySQL.
    @Bean
    public JdbcBatchItemWriter<Interes> interesWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<Interes>()
                .dataSource(dataSource)
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .sql("""
                        INSERT INTO intereses (cuenta_id, nombre, saldo, edad, tipo)
                        VALUES (:cuentaId, :nombre, :saldo, :edad, :tipo)
                        ON DUPLICATE KEY UPDATE nombre = VALUES(nombre), saldo = VALUES(saldo),
                        edad = VALUES(edad), tipo = VALUES(tipo)
                        """)
                .build();
    }

    // paso 3: lee cuentas_anuales.csv, procesa cada fila y la escribe en MySQL.
    @Bean
    public Step cuentasAnualesStep(JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ItemStreamReader<CuentaAnual> cuentaAnualReader,
            ItemProcessor<CuentaAnual, CuentaAnual> cuentaAnualProcessor,
            JdbcBatchItemWriter<CuentaAnual> cuentaAnualWriter,
            TaskExecutor batchTaskExecutor,
            SkipPolicy politicaSaltoPersonalizada,
            RetryPolicy politicaReintentoPersonalizada,
            SimpleCompletionPolicy politicaChunkPersonalizada) {
        return new StepBuilder("cuentasAnualesStep", jobRepository)
                .<CuentaAnual, CuentaAnual>chunk(politicaChunkPersonalizada, transactionManager)
                .reader(cuentaAnualReader)
                .processor(cuentaAnualProcessor)
                .writer(cuentaAnualWriter)
                .faultTolerant()
                .skipPolicy(politicaSaltoPersonalizada)
                .retryPolicy(politicaReintentoPersonalizada)
                .taskExecutor(batchTaskExecutor)
                .build();
    }

    // Reader del archivo cuentas_anuales.csv: transforma cada linea en un objeto CuentaAnual.
    @Bean
    public ItemStreamReader<CuentaAnual> cuentaAnualReader() {
        return new SynchronizedItemStreamReaderBuilder<CuentaAnual>()
                .delegate(cuentaAnualReaderDelegate())
                .build();
    }

    private FlatFileItemReader<CuentaAnual> cuentaAnualReaderDelegate() {
        return new FlatFileItemReaderBuilder<CuentaAnual>()
                .name("cuentaAnualReader")
                .resource(new ClassPathResource("input/cuentas_anuales.csv"))
                .linesToSkip(1)
                .saveState(false)
                .delimited()
                .names("cuentaId", "fecha", "transaccion", "monto", "descripcion")
                .fieldSetMapper(fieldSet -> {
                    // Convertimos manualmente cada linea del CSV al modelo que usara el processor.
                    CuentaAnual cuentaAnual = new CuentaAnual();
                    cuentaAnual.setCuentaId(fieldSet.readLong("cuentaId"));
                    cuentaAnual.setFecha(LocalDate.parse(fieldSet.readString("fecha")));
                    cuentaAnual.setTransaccion(fieldSet.readString("transaccion"));
                    cuentaAnual.setMonto(new BigDecimal(fieldSet.readString("monto")));
                    cuentaAnual.setDescripcion(fieldSet.readString("descripcion"));
                    return cuentaAnual;
                })
                .build();
    }

    // Processor de cuentas anuales: valida movimiento, corrige textos y filtra registros sin monto real.
    @Bean
    public ItemProcessor<CuentaAnual, CuentaAnual> cuentaAnualProcessor() {
        return cuentaAnual -> {
            // cuenta_id y fecha son campos minimos para relacionar el movimiento anual.
            if (cuentaAnual.getCuentaId() == null || cuentaAnual.getFecha() == null) {
                logger.warn("Cuenta anual descartada por cuenta_id o fecha vacia");
                return null;
            }

            // Si no hay monto, el movimiento queda incompleto y se filtra.
            if (cuentaAnual.getMonto() == null) {
                logger.warn("Cuenta anual de cuenta {} descartada por monto vacio", cuentaAnual.getCuentaId());
                return null;
            }

            // Los movimientos con monto cero se consideran registros sin efecto contable.
            if (cuentaAnual.getMonto().signum() == 0) {
                logger.warn("Cuenta anual de cuenta {} descartada por monto cero", cuentaAnual.getCuentaId());
                return null;
            }

            // Se normaliza la transaccion y se aceptan solo acciones conocidas.
            String transaccion = normalizarTexto(cuentaAnual.getTransaccion());
            if (!transaccion.equals("deposito") && !transaccion.equals("retiro") && !transaccion.equals("compra")) {
                logger.warn("Cuenta anual de cuenta {} descartada por transaccion invalida: {}",
                        cuentaAnual.getCuentaId(), cuentaAnual.getTransaccion());
                return null;
            }

            // Si no hay descripcion, se corrige con un texto por defecto para cumplir el NOT NULL de la tabla.
            String descripcion = limpiarTexto(cuentaAnual.getDescripcion());
            if (descripcion.isBlank()) {
                descripcion = "Sin descripcion";
            }

            // Se deja consistente el signo del monto segun el tipo de movimiento.
            if ((transaccion.equals("retiro") || transaccion.equals("compra")) && cuentaAnual.getMonto().signum() > 0) {
                cuentaAnual.setMonto(cuentaAnual.getMonto().negate());
            }
            if (transaccion.equals("deposito") && cuentaAnual.getMonto().signum() < 0) {
                cuentaAnual.setMonto(cuentaAnual.getMonto().abs());
            }

            cuentaAnual.setTransaccion(transaccion);
            cuentaAnual.setDescripcion(descripcion);
            return cuentaAnual;
        };
    }

    // Evita repetir trim/toLowerCase cada vez que validamos campos de texto.
    private String normalizarTexto(String texto) {
        return limpiarTexto(texto).toLowerCase();
    }

    // Convierte valores nulos en texto vacio y elimina espacios sobrantes.
    private String limpiarTexto(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.trim();
    }

    // Agrega una columna solo si no existe; esto evita errores cuando la aplicacion se ejecuta varias veces.
    private void asegurarColumna(JdbcTemplate jdbcTemplate, String tabla, String columna, String definicion) {
        Integer cantidad = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """, Integer.class, tabla, columna);

        if (cantidad != null && cantidad == 0) {
            jdbcTemplate.execute("ALTER TABLE " + tabla + " ADD COLUMN " + columna + " " + definicion);
        }
    }

    // Writer de cuentas anuales: inserta los movimientos anuales en MySQL.
    @Bean
    public JdbcBatchItemWriter<CuentaAnual> cuentaAnualWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<CuentaAnual>()
                .dataSource(dataSource)
                .itemSqlParameterSourceProvider(new BeanPropertyItemSqlParameterSourceProvider<>())
                .sql("""
                        INSERT INTO cuentas_anuales (cuenta_id, fecha, transaccion, monto, descripcion)
                        VALUES (:cuentaId, :fecha, :transaccion, :monto, :descripcion)
                        """)
                .build();
    }
}
