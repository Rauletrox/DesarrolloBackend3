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

### Politicas personalizadas y tolerancia a fallos

Se incorporo configuracion `faultTolerant()` en los tres Steps principales de carga:

- `transaccionesStep`
- `interesesStep`
- `cuentasAnualesStep`

Cada Step utiliza:

- `politicaSaltoPersonalizada`: permite omitir registros con errores recuperables, como problemas de formato, fechas invalidas o valores numericos incorrectos.
- `politicaReintentoPersonalizada`: reintenta errores transitorios relacionados con la base de datos antes de marcar el Step como fallido.
- `politicaChunkPersonalizada`: define el tamano del chunk utilizado por los Steps.

La politica de salto tiene un limite de `20` registros omitidos:

```java
private static final int LIMITE_SALTOS = 20;
```

La politica de reintento usa un limite de `3` reintentos:

```java
private static final int LIMITE_REINTENTOS = 3;
```

Con esto, si un registro del CSV viene con datos incorrectos, el Job puede continuar procesando los demas registros mientras el error sea recuperable y no se supere el limite configurado.

### Politica de escalamiento

Se agrego un `ThreadPoolTaskExecutor` para ejecutar los Steps de carga con hilos paralelos.

La cantidad de hilos se configura con:

```java
private static final int HILOS_PARALELOS = 3;
```

El executor queda limitado a tres hilos:

```java
taskExecutor.setCorePoolSize(HILOS_PARALELOS);
taskExecutor.setMaxPoolSize(HILOS_PARALELOS);
```

Los Steps de carga usan este executor mediante:

```java
.taskExecutor(batchTaskExecutor)
```

Esto permite que el procesamiento de chunks se ejecute en paralelo utilizando hasta `3` hilos.

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
spring.datasource.password=Ceci16112010
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
```

Antes de ejecutar el proyecto, crear la base de datos:

```sql
CREATE DATABASE backendIII;
```

Si tu usuario o password de MySQL son distintos, modificar estas propiedades:

```properties
spring.datasource.username=root
spring.datasource.password=tu_password
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

Al ejecutar la aplicacion, se lanzan automaticamente los tres Jobs en este orden:

1. `reporteTransaccionesDiariasJob`
2. `calculoInteresesMensualesJob`
3. `estadosCuentaAnualesJob`

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
