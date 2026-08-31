# Proyecto Spring Batch - Procesamiento de Archivos CSV

## Objetivo del proyecto

Este proyecto implementa una solucion con Spring Batch para leer, procesar y escribir datos desde archivos CSV hacia una base de datos MySQL.

La aplicacion procesa tres archivos ubicados en `src/main/resources/input`:

- `transacciones.csv`: contiene movimientos diarios.
- `intereses.csv`: contiene cuentas con saldo, edad y tipo de producto.
- `cuentas_anuales.csv`: contiene movimientos anuales por cuenta.

El flujo Batch esta dividido en tres Jobs principales:

- `reporteTransaccionesDiariasJob`: procesa transacciones diarias, detecta posibles anomalias y genera un resumen.
- `calculoInteresesMensualesJob`: calcula intereses mensuales para cuentas de ahorro, prestamos e hipotecas, y actualiza el saldo final.
- `estadosCuentaAnualesJob`: compila movimientos anuales por cuenta y genera un informe para auditoria.

## Tecnologias utilizadas

- Java 21
- Spring Boot 3.5.7
- Spring Batch
- Spring Data JPA
- MySQL
- Maven Wrapper

## Estructura del codigo

```text
src/main/java/com/backen3/Duoc
├── DuocApplication.java
├── batch
│   └── CsvBatchConfig.java
└── model
    ├── CuentaAnual.java
    ├── Interes.java
    └── Transaccion.java

src/main/resources
├── application.properties
├── schema.sql
└── input
    ├── cuentas_anuales.csv
    ├── intereses.csv
    └── transacciones.csv

Dockerfile
docker-compose.yml
.dockerignore
```

### `DuocApplication.java`

Clase principal del proyecto. Inicia la aplicacion Spring Boot.

### `CsvBatchConfig.java`

Contiene la configuracion de Spring Batch:

- Definicion de los tres Jobs.
- Definicion de los Steps.
- Readers para leer archivos CSV.
- Processors para validar, transformar y filtrar registros.
- Writers para insertar datos procesados en MySQL.
- Steps adicionales para generar resumenes e informes.
- Politicas personalizadas de tolerancia a fallos.
- Politica de escalamiento con ejecucion paralela.

## Avance de esta semana

Durante esta semana se agrego tolerancia a fallos y escalamiento a los Steps que procesan archivos CSV.

Tambien se reforzo la resiliencia operacional solicitada en la pauta:

- Re-ejecucion y recuperacion de Jobs con `saveState(true)` en los Readers.
- Parametro estable `fechaProceso` para poder reiniciar una instancia fallida con los mismos parametros.
- Idempotencia en `cuentas_anuales` mediante clave unica y `ON DUPLICATE KEY UPDATE`.
- Step `prepararCuentasAnualesStep` para eliminar duplicados previos y asegurar el indice unico antes de cargar movimientos anuales.
- Listeners de Job, Step y Skip para registrar estados, conteos y tiempos de ejecucion.
- Credenciales externas mediante variables de entorno.

### Politicas personalizadas y tolerancia a fallos

Se incorporo configuracion `faultTolerant()` en los tres Steps principales de carga:

- `transaccionesStep`
- `interesesStep`
- `cuentasAnualesStep`

Cada Step utiliza:

- `politicaSaltoPersonalizada`: permite omitir registros con errores recuperables, como problemas de formato, fechas invalidas o valores numericos incorrectos.
- `politicaReintentoPersonalizada`: reintenta errores transitorios relacionados con la base de datos antes de marcar el Step como fallido.
- `politicaChunkPersonalizada`: define el tamano del chunk utilizado por los Steps.
- `politicaExcepcionesPersonalizada`: limita excepciones repetidas dentro del procesamiento del Step.

La politica de salto tiene un limite de `20` registros omitidos:

```java
private static final int LIMITE_SALTOS = 20;
```

La politica de reintento usa un limite de `3` reintentos:

```java
private static final int LIMITE_REINTENTOS = 3;
```

Con esto, si un registro del CSV viene con datos incorrectos, el Job puede continuar procesando los demas registros mientras el error sea recuperable y no se supere el limite configurado.

### Re-ejecucion y recuperacion de Jobs

Los Readers usan `saveState(true)`, por lo que Spring Batch guarda su avance en las tablas internas del repositorio Batch. Si un Job falla despues de confirmar uno o mas chunks, una nueva ejecucion con los mismos parametros puede continuar desde el ultimo estado registrado.

La aplicacion ya no genera `run.id` con `System.currentTimeMillis()`. En su lugar usa un parametro de negocio estable:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--fechaProceso=2024-01"
```

Si un Job queda en estado `FAILED`, debe re-ejecutarse con el mismo valor de `fechaProceso` para demostrar el mecanismo de restart. Si se usa otro valor, Spring Batch lo considera una nueva instancia de Job.

### Idempotencia

La tabla `cuentas_anuales` define una clave unica sobre los datos que identifican un movimiento:

```sql
UNIQUE KEY uk_cuentas_anuales_movimiento (cuenta_id, fecha, transaccion, monto, descripcion)
```

El writer usa `ON DUPLICATE KEY UPDATE`, por lo que una segunda ejecucion no duplica movimientos anuales ni altera incorrectamente los totales del informe.

Antes de cargar el archivo anual se ejecuta `prepararCuentasAnualesStep`, que elimina duplicados existentes y crea el indice unico si la tabla venia de una version anterior del proyecto.

### Listeners y observabilidad

Se incorporaron listeners para centralizar el monitoreo:

- `JobExecutionListener`: registra inicio, termino, estado y duracion de cada Job.
- `StepExecutionListener`: registra lecturas, escrituras, filtros, skips, commits, rollbacks y duracion de cada Step.
- `SkipListener`: registra los registros omitidos en lectura, proceso o escritura.

Los logs permiten evaluar rendimiento con lineas como:

```text
STEP FINALIZADO | step=cuentasAnualesStep | estado=COMPLETED | leidos=9 | escritos=8 | filtrados=1 | omitidos=0 | commits=3 | rollbacks=0 | duracionMs=120
```

### Politica de escalamiento

Se agrego un `ThreadPoolTaskExecutor` para ejecutar los Steps de carga con hilos paralelos.

La cantidad de hilos se configura con:

```java
private static final int HILOS_PARALELOS = 3;
private static final int CAPACIDAD_COLA = 12;
```

El executor queda limitado a tres hilos:

```java
taskExecutor.setCorePoolSize(HILOS_PARALELOS);
taskExecutor.setMaxPoolSize(HILOS_PARALELOS);
taskExecutor.setQueueCapacity(CAPACIDAD_COLA);
taskExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
```

Los Steps de carga usan este executor mediante:

```java
.taskExecutor(batchTaskExecutor)
```

Esto permite que el procesamiento de chunks se ejecute en paralelo utilizando hasta `3` hilos. Para esta actividad se eligio multithreading en lugar de particiones porque los archivos CSV son simples, independientes y de tamano controlado; asi se mejora el rendimiento sin agregar complejidad extra de particionamiento por rangos o archivos separados. La cola de `12` chunks evita rechazos cuando la lectura produce trabajo mas rapido que la escritura en MySQL, y `CallerRunsPolicy` aplica una presion controlada si el executor se satura.

### Chunks de tamano 5

Tambien se configuro el tamano de chunk requerido:

```java
private static final int CHUNK_SIZE = 5;
```

Los Steps usan este valor mediante la politica:

```java
.<TipoEntrada, TipoSalida>chunk(politicaChunkPersonalizada, transactionManager)
```

Asi, cada transaccion de escritura se realiza en bloques de `5` registros.

### Carpeta `model`

Contiene las clases que representan cada fila de los archivos CSV:

- `Transaccion`: modelo para `transacciones.csv`.
- `Interes`: modelo para `intereses.csv`.
- `CuentaAnual`: modelo para `cuentas_anuales.csv`.

### `schema.sql`

Crea las tablas necesarias en MySQL:

- `transacciones`
- `intereses`
- `cuentas_anuales`
- `resumen_transacciones_diarias`
- `informe_estados_cuenta_anuales`
- Tablas internas de Spring Batch, creadas automaticamente por Spring.

## Configuracion de base de datos

La conexion a MySQL se configura en `src/main/resources/application.properties`.

Configuracion actual:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/backendIII?useSSL=false&serverTimezone=America/Santiago&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
```

Antes de ejecutar el proyecto, crear la base de datos:

```sql
CREATE DATABASE backendIII;
```

Si tu usuario o password de MySQL son distintos, usar variables de entorno:

```bash
export SPRING_DATASOURCE_USERNAME=root
export SPRING_DATASOURCE_PASSWORD=tu_password
```

La aplicacion tambien acepta variables de entorno para facilitar la ejecucion con Docker:

```properties
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
```

## Instrucciones para ejecutar

Desde la raiz del proyecto, compilar con:

```bash
./mvnw clean compile
```

Ejecutar la aplicacion con:

```bash
./mvnw spring-boot:run
```

Tambien se puede ejecutar en modo silencioso:

```bash
./mvnw -q spring-boot:run
```

## Ejecucion con Docker

Se agrego configuracion Docker para que otra persona pueda ejecutar el proyecto con la misma version de Java y una base de datos MySQL preparada.

Archivos agregados:

- `Dockerfile`: compila la aplicacion con Java 21 y genera una imagen ejecutable.
- `docker-compose.yml`: levanta MySQL 8.0 y la aplicacion Spring Batch.
- `.dockerignore`: evita copiar archivos innecesarios a la imagen.

Para levantar todo:

```bash
export MYSQL_ROOT_PASSWORD=tu_password
docker compose up --build
```

Este comando crea:

- Un contenedor MySQL llamado `duoc-mysql`.
- Una base de datos llamada `backendIII`.
- Un contenedor de aplicacion llamado `duoc-batch-app`.

La aplicacion dentro de Docker se conecta a MySQL usando:

```properties
SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/backendIII?useSSL=false&serverTimezone=America/Santiago&allowPublicKeyRetrieval=true
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=${MYSQL_ROOT_PASSWORD}
```

Para detener los contenedores:

```bash
docker compose down
```

Para detenerlos y borrar tambien los datos guardados en el volumen de MySQL:

```bash
docker compose down -v
```

Al ejecutar la aplicacion, se lanzan automaticamente los tres Jobs en este orden:

1. `reporteTransaccionesDiariasJob`
2. `calculoInteresesMensualesJob`
3. `estadosCuentaAnualesJob`

El Job anual ejecuta internamente:

1. `prepararCuentasAnualesStep`
2. `cuentasAnualesStep`
3. `informeEstadosCuentaAnualesStep`

## Evidencias de ejecucion

### Ejecucion completa de los tres Jobs

Comando ejecutado:

```bash
./mvnw -q spring-boot:run -Dspring-boot.run.arguments="--fechaProceso=2024-08"
```

Salida de consola:

```text
JOB INICIADO | job=reporteTransaccionesDiariasJob | parametros={'fechaProceso':'{value=2024-08, type=class java.lang.String, identifying=true}'}
STEP INICIADO | step=transaccionesStep
STEP FINALIZADO | step=transaccionesStep | estado=COMPLETED | leidos=10 | escritos=9 | filtrados=1 | omitidos=0 | commits=5 | rollbacks=0 | duracionMs=37
STEP INICIADO | step=resumenTransaccionesDiariasStep
STEP FINALIZADO | step=resumenTransaccionesDiariasStep | estado=COMPLETED | leidos=0 | escritos=0 | filtrados=0 | omitidos=0 | commits=1 | rollbacks=0 | duracionMs=8
JOB FINALIZADO | job=reporteTransaccionesDiariasJob | estado=COMPLETED | duracionMs=71

JOB INICIADO | job=calculoInteresesMensualesJob | parametros={'fechaProceso':'{value=2024-08, type=class java.lang.String, identifying=true}'}
STEP INICIADO | step=interesesStep
STEP FINALIZADO | step=interesesStep | estado=COMPLETED | leidos=8 | escritos=8 | filtrados=0 | omitidos=0 | commits=5 | rollbacks=0 | duracionMs=17
STEP INICIADO | step=calcularInteresesMensualesStep
STEP FINALIZADO | step=calcularInteresesMensualesStep | estado=COMPLETED | leidos=0 | escritos=0 | filtrados=0 | omitidos=0 | commits=1 | rollbacks=0 | duracionMs=15
JOB FINALIZADO | job=calculoInteresesMensualesJob | estado=COMPLETED | duracionMs=55

JOB INICIADO | job=estadosCuentaAnualesJob | parametros={'fechaProceso':'{value=2024-08, type=class java.lang.String, identifying=true}'}
STEP INICIADO | step=prepararCuentasAnualesStep
STEP FINALIZADO | step=prepararCuentasAnualesStep | estado=COMPLETED | leidos=0 | escritos=0 | filtrados=0 | omitidos=0 | commits=1 | rollbacks=0 | duracionMs=8
STEP INICIADO | step=cuentasAnualesStep
STEP FINALIZADO | step=cuentasAnualesStep | estado=COMPLETED | leidos=9 | escritos=8 | filtrados=2 | omitidos=0 | commits=5 | rollbacks=2 | duracionMs=16
STEP INICIADO | step=informeEstadosCuentaAnualesStep
STEP FINALIZADO | step=informeEstadosCuentaAnualesStep | estado=COMPLETED | leidos=0 | escritos=0 | filtrados=0 | omitidos=0 | commits=1 | rollbacks=0 | duracionMs=3
JOB FINALIZADO | job=estadosCuentaAnualesJob | estado=COMPLETED | duracionMs=49
```

### Evidencia de re-ejecucion con los mismos parametros

Comando ejecutado por segunda vez con el mismo `fechaProceso`:

```bash
./mvnw -q spring-boot:run -Dspring-boot.run.arguments="--fechaProceso=2024-08"
```

Salida de consola:

```text
Started DuocApplication in 1.015 seconds
Job omitido porque ya fue completado con los mismos parametros: reporteTransaccionesDiariasJob | fechaProceso=2024-08
Job omitido porque ya fue completado con los mismos parametros: calculoInteresesMensualesJob | fechaProceso=2024-08
Job omitido porque ya fue completado con los mismos parametros: estadosCuentaAnualesJob | fechaProceso=2024-08
```

Esta segunda ejecucion demuestra que Spring Batch reconoce que la instancia asociada al parametro `fechaProceso=2024-08` ya fue completada. Para probar recuperacion real, se debe provocar una falla, dejar el Job en estado `FAILED` y ejecutar nuevamente con el mismo parametro.

Enlace al repositorio GitHub:

```text
Pegar aqui el enlace del repositorio GitHub de la entrega.
```

## Resultado esperado

Al finalizar correctamente, los logs deben mostrar que los Jobs terminaron con estado `COMPLETED`.

Los datos procesados quedan almacenados en MySQL:

- Las transacciones validas se guardan en `transacciones`.
- El resumen diario se guarda en `resumen_transacciones_diarias`.
- Las cuentas con intereses calculados se guardan en `intereses`.
- Los movimientos anuales se guardan en `cuentas_anuales`.
- El informe anual se guarda en `informe_estados_cuenta_anuales`.

## Validaciones y transformaciones

El proyecto aplica validaciones mediante `ItemProcessor`.

### Transacciones diarias

- Descarta registros sin `id`, sin fecha o sin monto.
- Descarta movimientos con monto igual a `0`.
- Acepta solo tipos `debito` y `credito`.
- Normaliza textos eliminando espacios y convirtiendo a minusculas.

### Intereses mensuales

- Descarta registros sin `cuenta_id`.
- Descarta nombres vacios.
- Descarta saldos negativos.
- Valida edades entre 18 y 120.
- Acepta solo tipos `ahorro`, `prestamo` e `hipoteca`.
- Calcula tasa de interes, interes mensual y saldo final.

### Estados de cuenta anuales

- Descarta registros sin cuenta, sin fecha o con monto `0`.
- Acepta solo transacciones `deposito`, `retiro` y `compra`.
- Corrige el signo del monto segun el tipo de transaccion.
- Genera un informe agrupado por cuenta y anio.
