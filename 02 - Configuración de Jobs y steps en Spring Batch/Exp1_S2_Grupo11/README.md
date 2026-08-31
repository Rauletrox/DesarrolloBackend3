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

- Java 17
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
./mvnw clean package
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

Para ejecutar solo un Job sin modificar codigo, indicar su nombre al iniciar la aplicacion:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--batch.job.name=reporteTransaccionesDiariasJob"
./mvnw spring-boot:run -Dspring-boot.run.arguments="--batch.job.name=calculoInteresesMensualesJob"
./mvnw spring-boot:run -Dspring-boot.run.arguments="--batch.job.name=estadosCuentaAnualesJob"
```

## Procesamiento batch y manejo de errores

Los Steps de carga usan chunks de 5 registros y un `ThreadPoolTaskExecutor` limitado a 3 hilos. Los readers de CSV estan sincronizados para evitar lecturas duplicadas cuando los chunks se procesan en paralelo.

Los errores recuperables de lectura y conversion se omiten mediante una `SkipPolicy`, con un limite de 20 registros. Los errores transitorios de acceso a base de datos se reintentan hasta 3 veces mediante una `RetryPolicy`. Un error no incluido en esas politicas deja fallar el Step.

Los listeners registran el inicio y termino de cada Job, su estado, tiempo de ejecucion y los contadores reales `read`, `filter`, `write` y `skip` de cada Step. Un filtro del processor no es una omision: aumenta solo `filterCount`.

El Job anual limpia sus movimientos antes de volver a cargar el CSV, por lo que una nueva ejecucion con otros parametros no duplica la informacion usada por el informe.

Cada lanzamiento usa un `run.id` nuevo, por lo que corresponde a una nueva instancia y no a un reinicio de una ejecucion fallida. Los readers sincronizados tienen `saveState=false` para ser seguros con los 3 hilos; por ello no se debe presentar esa nueva instancia como un restart desde estado persistido.

## Pruebas

Las pruebas usan una base H2 en memoria, aislada de MySQL, y no modifican datos de desarrollo. Cubren processors, politicas de omision y reintento, los tres Jobs completos, persistencia, resultados conocidos de regresion y continuidad ante un CSV con una fila invalida.

Ejecutar las pruebas y la compilacion final con:

```bash
./mvnw test
./mvnw clean package
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
