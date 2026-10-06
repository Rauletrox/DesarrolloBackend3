# Semana 3

## 1. Objetivo del proyecto

Proyecto individual de Semana 3 de Desarrollo Backend III. Moderniza procesos batch legacy del Banco XYZ mediante Spring Batch y continúa el trabajo de las semanas anteriores. Procesa transacciones diarias, intereses mensuales y movimientos anuales, incorporando escalamiento multi-thread y tolerancia a fallos.

## 2. Tecnologías utilizadas

- Java 17
- Spring Boot
- Spring Batch
- Spring JDBC y `JdbcTemplate`
- PostgreSQL
- Maven
- Docker Compose
- SLF4J

## 3. Arquitectura de la solución

```text
CSV -> ItemReader -> ItemProcessor -> ItemWriter -> PostgreSQL
```

Cada `Job` organiza uno o más `Step`. Los `ItemReader` leen los CSV, los `ItemProcessor` validan y transforman los registros, y los `ItemWriter` persisten los datos mediante JDBC. El `JobRepository` usa PostgreSQL y registra Jobs, Steps y ejecuciones en las tablas `BATCH_*`.

## 4. Estructura del código

```text
proyecto/
├── .mvn/wrapper/
├── output/
│   └── estados_cuenta_anuales.csv
├── src/main/
│   ├── java/cl/duoc/bancoxyz/
│   │   ├── config/
│   │   ├── exception/
│   │   ├── model/
│   │   ├── processor/
│   │   └── BankBatchApplication.java
│   └── resources/
│       ├── input/
│       │   ├── transacciones.csv
│       │   ├── intereses.csv
│       │   └── cuentas_anuales.csv
│       ├── application.properties
│       └── schema.sql
├── .dockerignore
├── Dockerfile
├── docker-compose.yml
├── mvnw
├── mvnw.cmd
└── pom.xml
```

`config` contiene los Jobs, Steps, políticas y listeners; `processor` concentra las validaciones y transformaciones; `model` representa los registros CSV; y `resources` contiene configuración, esquema y datos de entrada.

## 5. Procesos Batch implementados

### dailyTransactionsJob

Lee `input/transacciones.csv` mediante `dailyTransactionsStep`. Valida campos, fecha, monto, tipo y duplicados; persiste las transacciones válidas en `transacciones_procesadas` y registra el resumen en `resumen_transacciones_diarias`.

### monthlyInterestJob

Lee `input/intereses.csv` mediante `monthlyInterestStep`. Valida cuentas, saldo, edad y tipo, calcula el interés de ahorro o préstamo y persiste el resultado en `intereses_mensuales`.

### annualStatementsJob

Lee `input/cuentas_anuales.csv` mediante `annualMovementsStep`, valida los movimientos y los persiste en `movimientos_anuales`. Luego `annualStatementReportStep` genera `output/estados_cuenta_anuales.csv`.

## 6. Procesamiento paralelo y escalamiento

El proyecto usa `ThreadPoolTaskExecutor` para ejecutar chunks en paralelo con hilos `Batch-Thread-`. La configuración utilizada en las ejecuciones principales es:

```properties
bank.batch.chunk-size=5
bank.batch.core-pool-size=3
bank.batch.max-pool-size=3
bank.batch.queue-capacity=25
```

| Configuración | Chunk | Threads | Duración |
| --- | ---: | ---: | ---: |
| A | 2 | 2 | 169 ms |
| B | 5 | 3 | 185 ms |
| C | 5 | 4 | 165 ms |

Las tres configuraciones mantuvieron el mismo resultado funcional: 10 registros leídos, 7 escritos, 3 omitidos y estado `COMPLETED`.

La configuración C fue la **mejor configuración observada para el conjunto de datos de prueba**.

## 7. Manejo de errores y tolerancia a fallos

`BankDataSkipPolicy` omite registros con `InvalidDataException` o `FlatFileParseException`, y `BatchSkipListener` registra esos skips. Durante las ejecuciones finales hubo skips reales por registros inválidos.

La `RetryPolicy` reintenta únicamente `TransientDataAccessException`, con límite de 3 intentos. `FixedBackOffPolicy` espera 100 ms entre reintentos.

No se produjo un error transitorio real de PostgreSQL durante la ejecución final, por lo que la política quedó configurada pero no fue activada.

## 8. Requisitos de ejecución

- Java 17
- Docker Desktop con integración habilitada para WSL
- Docker Compose
- WSL Ubuntu

## 9. Instrucciones para ejecutar el proyecto

Todas las instrucciones siguientes se ejecutan desde una terminal Ubuntu WSL.

desde:

```bash
cd "/home/francisco/proyecto/BACKEND III/SEMANA 3/Exp1_S3_Francisco_Henriquez/proyecto"
```

### 9.1 Levantar PostgreSQL

Para comenzar desde una base de datos limpia:

```bash
docker compose down -v --remove-orphans
docker compose up -d postgres
docker compose ps
```

Comprobar que PostgreSQL esté disponible:

```bash
docker compose exec -T postgres pg_isready -U banco_user -d banco_xyz
```

El resultado debe indicar:

```text
accepting connections
```

### 9.2 Compilar el proyecto

```bash
chmod +x mvnw
./mvnw clean package
```

El resultado esperado es:

```text
BUILD SUCCESS
```

El JAR generado es:

```text
target/banco-xyz-batch-1.0.0.jar
```

### 9.3 Crear carpeta para evidencias reproducidas

Para no modificar las evidencias originales:

```bash
mkdir -p ../evidencias/reproduccion
```

### 9.4 Ejecutar dailyTransactionsJob

```bash
java -jar target/banco-xyz-batch-1.0.0.jar \
  --spring.batch.job.name=dailyTransactionsJob \
  ejecucion=301,java.lang.Long,true \
  2>&1 | tee ../evidencias/reproduccion/01_transacciones.txt
```

Resultado esperado:

```text
Read = 10
Write = 7
Skip = 3
Estado = COMPLETED
```

### 9.5 Ejecutar monthlyInterestJob

```bash
java -jar target/banco-xyz-batch-1.0.0.jar \
  --spring.batch.job.name=monthlyInterestJob \
  ejecucion=302,java.lang.Long,true \
  2>&1 | tee ../evidencias/reproduccion/02_intereses.txt
```

Resultado esperado:

```text
Read = 8
Write = 6
Skip = 2
Estado = COMPLETED
```

### 9.6 Ejecutar annualStatementsJob

```bash
java -jar target/banco-xyz-batch-1.0.0.jar \
  --spring.batch.job.name=annualStatementsJob \
  ejecucion=303,java.lang.Long,true \
  2>&1 | tee ../evidencias/reproduccion/03_estados_anuales.txt
```

Resultado esperado de `annualMovementsStep`:

```text
Read = 9
Write = 8
Skip = 1
Estado = COMPLETED
```

También deben finalizar:

```text
annualStatementReportStep = COMPLETED
annualStatementsJob = COMPLETED
```

## 10. Resultados de ejecución

| Job / Step | Read | Write | Skip | Estado |
| --- | ---: | ---: | ---: | --- |
| `dailyTransactionsJob` | 10 | 7 | 3 | `COMPLETED` |
| `monthlyInterestJob` | 8 | 6 | 2 | `COMPLETED` |
| `annualMovementsStep` | 9 | 8 | 1 | `COMPLETED` |
| `annualStatementReportStep` | 0 | 0 | 0 | `COMPLETED` |

`annualStatementReportStep` es un Step basado en Tasklet que genera el informe anual; por eso sus contadores no representan procesamiento por chunks.

## 11. Resultados en base de datos

Después de ejecutar los tres Jobs sobre una base limpia:

```text
transacciones_procesadas = 7
intereses_mensuales = 6
movimientos_anuales = 8
```

PostgreSQL también almacena los metadatos de Spring Batch en las tablas `BATCH_*`.

## 12. Archivo generado

`annualStatementsJob` genera:

```text
output/estados_cuenta_anuales.csv
```

## 13. Evidencias

Las evidencias originales del proyecto son:

```text
evidencias/01_transacciones.txt
evidencias/02_intereses.txt
evidencias/03_estados_anuales.txt
evidencias/04_reejecucion_transacciones.txt
evidencias/escala_A.txt
evidencias/escala_B.txt
evidencias/escala_C.txt
```

Las nuevas ejecuciones realizadas siguiendo este README se almacenan en:

```text
evidencias/reproduccion/
```

# 14. Reproducción de las 9 figuras del informe

Las siguientes instrucciones permiten reproducir las evidencias utilizadas en:

```text
PBY2203_Exp1_S3_Francisco_Henriquez.docx
```

Para las Figuras 1 a 6 se utiliza la ejecución normal realizada anteriormente.

## 14.1 Figura 1 - dailyTransactionsJob

Limpiar la terminal:

```bash
clear
```

Mostrar las líneas relevantes:

```bash
grep -E "TaskExecutor configurado|Registro omitido|Finaliza Step dailyTransactionsStep|RESUMEN dailyTransactionsJob" \
../evidencias/reproduccion/01_transacciones.txt
```

La salida debe permitir comprobar:

```text
dailyTransactionsJob
dailyTransactionsStep
leidos=10
escritos=7
omitidos=3
estado=COMPLETED
```

## 14.2 Figura 2 - monthlyInterestJob

Limpiar:

```bash
clear
```

Mostrar las líneas relevantes:

```bash
grep -E "TaskExecutor configurado|Registro omitido|Finaliza Step monthlyInterestStep|RESUMEN monthlyInterestJob" \
../evidencias/reproduccion/02_intereses.txt
```

La salida debe permitir comprobar:

```text
monthlyInterestJob
monthlyInterestStep
leidos=8
escritos=6
omitidos=2
estado=COMPLETED
```

## 14.3 Figura 3 - annualStatementsJob

Limpiar:

```bash
clear
```

Ejecutar:

```bash
grep -E "TaskExecutor configurado|Registro omitido|Finaliza Step annualMovementsStep|annualStatementReportStep|RESUMEN annualStatementsJob" \
../evidencias/reproduccion/03_estados_anuales.txt
```

La salida debe permitir comprobar:

```text
annualMovementsStep
leidos=9
escritos=8
omitidos=1
COMPLETED

annualStatementReportStep
COMPLETED

annualStatementsJob
COMPLETED
```

## 14.4 Figura 4 - Resultados en PostgreSQL

Después de ejecutar los tres Jobs, limpiar la terminal:

```bash
clear
```

Ejecutar:

```bash
docker compose exec -T postgres psql \
  -U banco_user \
  -d banco_xyz \
  -At \
  -c "
SELECT 'transacciones_procesadas: ' || COUNT(*) || ' filas'
FROM transacciones_procesadas;

SELECT 'intereses_mensuales: ' || COUNT(*) || ' filas'
FROM intereses_mensuales;

SELECT 'movimientos_anuales: ' || COUNT(*) || ' filas'
FROM movimientos_anuales;
"
```

El resultado esperado es:

```text
transacciones_procesadas: 7 filas
intereses_mensuales: 6 filas
movimientos_anuales: 8 filas
```

## 14.5 Figura 5 - Archivo anual generado

Comprobar que el archivo existe:

```bash
ls -l output/estados_cuenta_anuales.csv
```

Limpiar:

```bash
clear
```

Mostrar su contenido:

```bash
cat output/estados_cuenta_anuales.csv
```

El encabezado debe ser:

```text
cuenta_id,total_movimientos,total_depositos,total_cargos,saldo_neto
```

En la ejecución validada se obtuvo:

```text
101,2,1000.00,-500.00,500.00
102,1,1500.00,0,1500.00
103,1,2000.00,0,2000.00
104,1,0,-100.00,-100.00
105,1,2500.00,0,2500.00
106,1,3000.00,0,3000.00
108,1,2000.00,0,2000.00
```

## 14.6 Figura 6 - Procesamiento paralelo

La evidencia se obtiene de los logs de los tres Jobs.

Limpiar:

```bash
clear
```

Ejecutar:

```bash
{
  grep -m1 "TaskExecutor configurado" \
    ../evidencias/reproduccion/01_transacciones.txt

  grep "Batch-Thread-1" \
    ../evidencias/reproduccion/01_transacciones.txt \
    ../evidencias/reproduccion/02_intereses.txt \
    ../evidencias/reproduccion/03_estados_anuales.txt \
    | head -n 1

  grep "Batch-Thread-2" \
    ../evidencias/reproduccion/01_transacciones.txt \
    ../evidencias/reproduccion/02_intereses.txt \
    ../evidencias/reproduccion/03_estados_anuales.txt \
    | head -n 1

  grep "Batch-Thread-3" \
    ../evidencias/reproduccion/01_transacciones.txt \
    ../evidencias/reproduccion/02_intereses.txt \
    ../evidencias/reproduccion/03_estados_anuales.txt \
    | head -n 1
}
```

La salida debe permitir observar:

```text
TaskExecutor configurado
Batch-Thread-1
Batch-Thread-2
Batch-Thread-3
```

La asignación exacta de los registros a cada hilo puede variar entre ejecuciones.

Tomar la captura de pantalla.

## 14.7 Figura 7 - Comparación de escalamiento

La comparación utiliza `dailyTransactionsJob`.

Cada configuración debe ejecutarse desde una base de datos limpia y utilizando el mismo archivo de entrada.

Los valores de tiempo pueden ser diferentes a los registrados originalmente.

Lo que debe mantenerse en las tres pruebas es:

```text
Read = 10
Write = 7
Skip = 3
Estado = COMPLETED
```

### Configuración A

Reiniciar PostgreSQL:

```bash
docker compose down -v --remove-orphans
docker compose up -d postgres
```

Esperar a PostgreSQL:

```bash
until docker compose exec -T postgres pg_isready -U banco_user -d banco_xyz
do
  sleep 1
done
```

Ejecutar:

```bash
java -jar target/banco-xyz-batch-1.0.0.jar \
  --spring.batch.job.name=dailyTransactionsJob \
  --bank.batch.chunk-size=2 \
  --bank.batch.core-pool-size=2 \
  --bank.batch.max-pool-size=2 \
  --bank.batch.queue-capacity=25 \
  ejecucion=101,java.lang.Long,true \
  2>&1 | tee ../evidencias/reproduccion/escala_A.txt
```

### Configuración B

Reiniciar PostgreSQL:

```bash
docker compose down -v --remove-orphans
docker compose up -d postgres
```

Esperar:

```bash
until docker compose exec -T postgres pg_isready -U banco_user -d banco_xyz
do
  sleep 1
done
```

Ejecutar:

```bash
java -jar target/banco-xyz-batch-1.0.0.jar \
  --spring.batch.job.name=dailyTransactionsJob \
  --bank.batch.chunk-size=5 \
  --bank.batch.core-pool-size=3 \
  --bank.batch.max-pool-size=3 \
  --bank.batch.queue-capacity=25 \
  ejecucion=102,java.lang.Long,true \
  2>&1 | tee ../evidencias/reproduccion/escala_B.txt
```

### Configuración C

Reiniciar PostgreSQL:

```bash
docker compose down -v --remove-orphans
docker compose up -d postgres
```

Esperar:

```bash
until docker compose exec -T postgres pg_isready -U banco_user -d banco_xyz
do
  sleep 1
done
```

Ejecutar:

```bash
java -jar target/banco-xyz-batch-1.0.0.jar \
  --spring.batch.job.name=dailyTransactionsJob \
  --bank.batch.chunk-size=5 \
  --bank.batch.core-pool-size=4 \
  --bank.batch.max-pool-size=4 \
  --bank.batch.queue-capacity=25 \
  ejecucion=103,java.lang.Long,true \
  2>&1 | tee ../evidencias/reproduccion/escala_C.txt
```

### Mostrar las tres pruebas juntas

Limpiar:

```bash
clear
```

Ejecutar:

```bash
echo "CONFIGURACION A"
echo "chunk=2 | core=2 | max=2 | cola=25"
grep "Finaliza Step dailyTransactionsStep" \
  ../evidencias/reproduccion/escala_A.txt

echo
echo "CONFIGURACION B"
echo "chunk=5 | core=3 | max=3 | cola=25"
grep "Finaliza Step dailyTransactionsStep" \
  ../evidencias/reproduccion/escala_B.txt

echo
echo "CONFIGURACION C"
echo "chunk=5 | core=4 | max=4 | cola=25"
grep "Finaliza Step dailyTransactionsStep" \
  ../evidencias/reproduccion/escala_C.txt
```

La salida será equivalente a:

```text
CONFIGURACION A
chunk=2 | core=2 | max=2 | cola=25
Finaliza Step dailyTransactionsStep - duracionMs=XXX, leidos=10, escritos=7, omitidos=3, estado=COMPLETED

CONFIGURACION B
chunk=5 | core=3 | max=3 | cola=25
Finaliza Step dailyTransactionsStep - duracionMs=XXX, leidos=10, escritos=7, omitidos=3, estado=COMPLETED

CONFIGURACION C
chunk=5 | core=4 | max=4 | cola=25
Finaliza Step dailyTransactionsStep - duracionMs=XXX, leidos=10, escritos=7, omitidos=3, estado=COMPLETED
```

`XXX` corresponde al tiempo realmente obtenido en cada nueva ejecución.

## 14.8 Figura 8 - Nueva ejecución de dailyTransactionsJob

Esta prueba ejecuta nuevamente `dailyTransactionsJob` utilizando un parámetro identificador diferente. Corresponde a una nueva ejecución completa y no a una reanudación desde una fila específica del CSV.

Ejecutar:

```bash
java -jar target/banco-xyz-batch-1.0.0.jar \
  --spring.batch.job.name=dailyTransactionsJob \
  ejecucion=305,java.lang.Long,true \
  2>&1 | tee ../evidencias/reproduccion/04_reejecucion_transacciones.txt
```

El resultado esperado es:

```text
Read = 10
Write = 7
Skip = 3
Estado = COMPLETED
```

Para mostrar únicamente las líneas relevantes de la ejecución:

```bash
grep -E "Finaliza Step dailyTransactionsStep|RESUMEN dailyTransactionsJob|ejecucion.*305|status.*COMPLETED" \
  ../evidencias/reproduccion/04_reejecucion_transacciones.txt
```

La salida debe permitir comprobar que `dailyTransactionsJob` fue ejecutado nuevamente con el parámetro `ejecucion=305` y finalizó con estado `COMPLETED`.

## 14.9 Figura 9 - Verificación de reejecución y ausencia de duplicados

Después de la nueva ejecución, se verifica el registro de las ejecuciones de `dailyTransactionsJob` almacenadas por Spring Batch, el contenido final de la tabla `transacciones_procesadas` y la ausencia de registros duplicados.

Limpiar la terminal:

```bash
clear
```

Ejecutar:

```bash
docker compose exec -T postgres psql \
  -U banco_user \
  -d banco_xyz \
  -c "
SELECT
    ji.JOB_INSTANCE_ID,
    ji.JOB_NAME,
    je.JOB_EXECUTION_ID,
    je.STATUS,
    jep.PARAMETER_NAME,
    jep.PARAMETER_VALUE,
    jep.IDENTIFYING
FROM BATCH_JOB_INSTANCE ji
JOIN BATCH_JOB_EXECUTION je
    ON ji.JOB_INSTANCE_ID = je.JOB_INSTANCE_ID
LEFT JOIN BATCH_JOB_EXECUTION_PARAMS jep
    ON je.JOB_EXECUTION_ID = jep.JOB_EXECUTION_ID
WHERE ji.JOB_NAME = 'dailyTransactionsJob'
ORDER BY je.JOB_EXECUTION_ID;
"

docker compose exec -T postgres psql \
  -U banco_user \
  -d banco_xyz \
  -c "SELECT * FROM transacciones_procesadas ORDER BY id;"

docker compose exec -T postgres psql \
  -U banco_user \
  -d banco_xyz \
  -c "
SELECT id, COUNT(*) AS cantidad
FROM transacciones_procesadas
GROUP BY id
HAVING COUNT(*) > 1;
"
```

La salida debe permitir comprobar:

```text
dailyTransactionsJob - ejecucion 103 - COMPLETED
dailyTransactionsJob - ejecucion 305 - COMPLETED

transacciones_procesadas = 7 registros

Registros duplicados = 0
```

Las ejecuciones 103, 304 y 305 quedan registradas por Spring Batch con estado `COMPLETED`. Después de la reejecución, la tabla `transacciones_procesadas` mantiene 7 registros y la consulta de duplicados retorna `0 rows`, verificando que no se generaron registros duplicados.

## 15. Resumen de las evidencias reproducibles

| Figura | Evidencia |
| --- | --- |
| Figura 1 | Ejecución de `dailyTransactionsJob` |
| Figura 2 | Ejecución de `monthlyInterestJob` |
| Figura 3 | Ejecución de `annualStatementsJob` |
| Figura 4 | Resultados persistidos en PostgreSQL |
| Figura 5 | Contenido de `estados_cuenta_anuales.csv` |
| Figura 6 | Procesamiento paralelo mediante `Batch-Thread-*` |
| Figura 7 | Comparación A, B y C de escalamiento |
| Figura 8 | Nueva ejecución de `dailyTransactionsJob` |
| Figura 9 | Verificación de reejecución y ausencia de duplicados |

## 16. Consideración sobre los tiempos

Los tiempos originales observados fueron:

```text
A = 169 ms
B = 185 ms
C = 165 ms
```

Estos valores corresponden a una ejecución concreta y pueden variar al repetir la prueba.

En cada nueva ejecución se deben conservar los resultados funcionales:

```text
Read = 10
Write = 7
Skip = 3
Estado = COMPLETED
```

## 17. Ejecución completa mediante Docker Compose

Como alternativa a la ejecución de la aplicación mediante `java -jar`, el proyecto puede ejecutarse completamente mediante Docker Compose.

Esta modalidad utiliza dos componentes:

```text
Docker Compose
├── postgres
│   └── PostgreSQL 16
│
└── app
    └── Spring Boot + Spring Batch + Java 17
```

El servicio `postgres` mantiene la base de datos y el servicio `app` contiene la aplicación Spring Batch.

Esta alternativa facilita el despliegue del proyecto porque el equipo donde se ejecute solamente necesita Docker y Docker Compose. Java 17 y la compilación Maven son administrados dentro de la imagen Docker de la aplicación.

### 17.1 Construir la imagen de la aplicación

Desde la carpeta del proyecto:

```bash
cd "/home/francisco/proyecto/BACKEND III/SEMANA 3/Exp1_S3_Francisco_Henriquez/proyecto"
```

Construir la imagen:

```bash
docker compose build
```

Durante este proceso Docker utiliza el `Dockerfile` para:

```text
1. Utilizar Java 17 JDK para compilar.
2. Ejecutar Maven Wrapper.
3. Generar banco-xyz-batch-1.0.0.jar.
4. Crear una imagen final utilizando Java 17 JRE.
```

El resultado esperado debe finalizar indicando que la imagen de la aplicación fue construida correctamente.

Por ejemplo:

```text
Image proyecto-app Built
```

### 17.2 Levantar PostgreSQL

Ejecutar:

```bash
docker compose up -d postgres
```

Comprobar el estado:

```bash
docker compose ps
```

El contenedor debe aparecer con estado:

```text
healthy
```

Por ejemplo:

```text
banco-xyz-postgres   postgres:16   Up (...) (healthy)
```

El servicio PostgreSQL utiliza internamente:

```text
postgres:5432
```

El puerto publicado hacia WSL es:

```text
localhost:5433
```

Cuando la aplicación también se ejecuta dentro de Docker, Docker Compose utiliza el nombre del servicio `postgres` para establecer la comunicación entre los contenedores.

La conexión utilizada por el contenedor `app` es:

```text
jdbc:postgresql://postgres:5432/banco_xyz
```

### 17.3 Ejecutar dailyTransactionsJob

Ejecutar:

```bash
docker compose run --rm app \
  --spring.batch.job.name=dailyTransactionsJob \
  ejecucion=401,java.lang.Long,true
```

El contenedor espera automáticamente hasta que PostgreSQL se encuentre disponible.

El resultado funcional esperado es:

```text
Read = 10
Write = 7
Skip = 3
Estado = COMPLETED
```

En los logs debe observarse una salida equivalente a:

```text
Finaliza Step dailyTransactionsStep - leidos=10, escritos=7, omitidos=3, estado=COMPLETED
```

y posteriormente:

```text
dailyTransactionsJob ... status: [COMPLETED]
```

Cuando el Job ya indique `COMPLETED`, presionar:

```text
Ctrl+C
```

para cerrar el contenedor temporal de la aplicación.

La opción:

```text
--rm
```

hace que el contenedor temporal sea eliminado al finalizar.

PostgreSQL permanece ejecutándose.

### 17.4 Ejecutar monthlyInterestJob

Ejecutar:

```bash
docker compose run --rm app \
  --spring.batch.job.name=monthlyInterestJob \
  ejecucion=402,java.lang.Long,true
```

El resultado funcional esperado es:

```text
Read = 8
Write = 6
Skip = 2
Estado = COMPLETED
```

En los logs debe observarse una salida equivalente a:

```text
Finaliza Step monthlyInterestStep - leidos=8, escritos=6, omitidos=2, estado=COMPLETED
```

y posteriormente:

```text
monthlyInterestJob ... status: [COMPLETED]
```

Cuando el Job indique `COMPLETED`, presionar:

```text
Ctrl+C
```

para cerrar el contenedor temporal.

### 17.5 Ejecutar annualStatementsJob

Ejecutar:

```bash
docker compose run --rm app \
  --spring.batch.job.name=annualStatementsJob \
  ejecucion=403,java.lang.Long,true
```

El resultado funcional esperado de `annualMovementsStep` es:

```text
Read = 9
Write = 8
Skip = 1
Estado = COMPLETED
```

También deben finalizar correctamente:

```text
annualStatementReportStep = COMPLETED
annualStatementsJob = COMPLETED
```

En los logs debe observarse una salida equivalente a:

```text
Finaliza Step annualMovementsStep - leidos=9, escritos=8, omitidos=1, estado=COMPLETED
```

y:

```text
annualStatementsJob ... status: [COMPLETED]
```

Cuando el Job indique `COMPLETED`, presionar:

```text
Ctrl+C
```

para cerrar el contenedor temporal.

### 17.6 Verificar los resultados en PostgreSQL

Después de ejecutar los tres Jobs, consultar la base de datos:

```bash
docker compose exec -T postgres psql \
  -U banco_user \
  -d banco_xyz \
  -At \
  -c "
SELECT 'transacciones_procesadas: ' || COUNT(*) || ' filas'
FROM transacciones_procesadas;

SELECT 'intereses_mensuales: ' || COUNT(*) || ' filas'
FROM intereses_mensuales;

SELECT 'movimientos_anuales: ' || COUNT(*) || ' filas'
FROM movimientos_anuales;
"
```

El resultado esperado es:

```text
transacciones_procesadas: 7 filas
intereses_mensuales: 6 filas
movimientos_anuales: 8 filas
```

### 17.7 Verificar el archivo anual

El servicio `app` utiliza un volumen para compartir la carpeta:

```text
./output
```

con:

```text
/app/output
```

dentro del contenedor.

Por esta razón, el archivo generado por `annualStatementsJob` permanece disponible en el proyecto después de finalizar el contenedor.

Comprobar:

```bash
ls -l output/estados_cuenta_anuales.csv
```

Mostrar su contenido:

```bash
cat output/estados_cuenta_anuales.csv
```

El archivo debe comenzar con:

```text
cuenta_id,total_movimientos,total_depositos,total_cargos,saldo_neto
```

### 17.8 Detener PostgreSQL

Después de finalizar las pruebas, detener los servicios:

```bash
docker compose down
```

Este comando detiene y elimina los contenedores, pero mantiene el volumen de PostgreSQL.

Si se desea eliminar también la base de datos y comenzar nuevamente desde cero:

```bash
docker compose down -v --remove-orphans
```

El parámetro:

```text
-v
```

elimina el volumen de PostgreSQL y, por lo tanto, todos los datos almacenados.

### 17.9 Resumen de ejecución mediante Docker Compose

La ejecución completa mediante Docker Compose queda formada por:

```text
docker compose build
        ↓
construye la aplicación con Java 17
        ↓
docker compose up -d postgres
        ↓
PostgreSQL queda healthy
        ↓
docker compose run --rm app dailyTransactionsJob
        ↓
10 / 7 / 3 / COMPLETED
        ↓
docker compose run --rm app monthlyInterestJob
        ↓
8 / 6 / 2 / COMPLETED
        ↓
docker compose run --rm app annualStatementsJob
        ↓
9 / 8 / 1 / COMPLETED
        ↓
PostgreSQL
7 / 6 / 8
        ↓
output/estados_cuenta_anuales.csv
```

Esta modalidad no reemplaza las instrucciones de ejecución y reproducción de evidencias indicadas anteriormente. Corresponde a una alternativa adicional para facilitar la construcción y ejecución completa de la solución mediante contenedores Docker.

## 18. SEMANA 4 - BACKEND FOR FRONTEND (BFF)

### Objetivo y estrategia

El patron Backend for Frontend adapta la informacion y las operaciones al tipo de cliente que las consume. Para este proyecto se seleccionaron endpoints separados por canal dentro de la misma aplicacion Spring Boot: Web, Mobile y ATM. Esta estrategia entrega contratos especificos, reutiliza PostgreSQL y la logica existente, y evita introducir microservicios o componentes adicionales fuera del alcance academico.

La capa BFF consulta solamente datos persistidos por los Jobs previos. No lee los CSV ni modifica la arquitectura Spring Batch.

### Estructura BFF

```text
cl.duoc.bancoxyz.bff/
├── BffBankingService.java
├── web/
│   ├── WebBffController.java
│   └── dto/
├── mobile/
│   ├── MobileBffController.java
│   └── dto/
└── atm/
    ├── AtmBffController.java
    └── dto/
```

### Canales y endpoints

| Canal | Endpoint | Datos entregados |
| --- | --- | --- |
| Web | `GET /api/bff/web/accounts/{accountId}` | Cuenta, nombre, tipo, saldo inicial, tasa, interes y saldo final. |
| Web | `GET /api/bff/web/accounts/{accountId}/annual-statement` | Movimientos, depositos, cargos y saldo neto anual. |
| Mobile | `GET /api/bff/mobile/accounts/{accountId}` | Cuenta, nombre, saldo y tipo. |
| Mobile | `GET /api/bff/mobile/accounts/{accountId}/transactions` | Ultimos cinco movimientos por fecha descendente. |
| ATM | `GET /api/bff/atm/accounts/{accountId}/balance` | Cuenta y saldo. |
| ATM | `POST /api/bff/atm/accounts/{accountId}/withdraw` | Retiro y saldo actualizado. |

Web entrega el contrato mas completo; Mobile elimina tasa, saldo inicial e interes; ATM entrega solamente los datos necesarios para consultar y retirar efectivo.

### Seguridad por canal

Cada ruta BFF requiere el header `X-Channel-Token`. Los tokens se configuran mediante variables de entorno o `application.properties`:

| Canal | Variable | Valor local por defecto |
| --- | --- | --- |
| Web | `BFF_WEB_TOKEN` | `web-token-xyz` |
| Mobile | `BFF_MOBILE_TOKEN` | `mobile-token-xyz` |
| ATM | `BFF_ATM_TOKEN` | `atm-token-xyz` |

Un token ausente, o de un canal distinto, responde `401 Unauthorized`. Una cuenta inexistente responde `404`; los retiros con monto no valido o saldo insuficiente responden `400`.

## Reproducción de evidencias por consola

Los siguientes comandos corresponden a las ejecuciones registradas en `evidencias/01_bff_web.txt` a `evidencias/06_bff_verificacion_final.txt`. Utilizan los datos ya persistidos por los Jobs Batch; no reinicializan la base de datos ni vuelven a ejecutar los Jobs.

### Preparación del entorno

```bash
cd "/home/francisco/proyecto/BACKEND III/SEMANA 4/Exp1_S4_Francisco_Henriquez/proyecto"
docker compose ps
```

La evidencia registró PostgreSQL healthy en el puerto `5433`. Si la API BFF no está disponible, iniciarla sin lanzar los Jobs Batch:

```bash
java -jar target/banco-xyz-batch-1.0.0.jar --spring.batch.job.enabled=false
```

### BFF Web

```bash
curl -i -H 'X-Channel-Token: web-token-xyz' \
  http://127.0.0.1:8080/api/bff/web/accounts/101

curl -i -H 'X-Channel-Token: web-token-xyz' \
  http://127.0.0.1:8080/api/bff/web/accounts/101/annual-statement
```

Las respuestas registradas fueron `HTTP 200`. El detalle Web de la cuenta 101 incluyó `initialBalance` 8000.00, `interestRate` 0.0200, `calculatedInterest` 160.00 y `finalBalance` 8160.00. El estado anual registró 14 movimientos, depósitos por 21000.00, cargos por -1300.00 y saldo neto de 19700.00.

### BFF Mobile

```bash
curl -i -H 'X-Channel-Token: mobile-token-xyz' \
  http://127.0.0.1:8080/api/bff/mobile/accounts/101

curl -i -H 'X-Channel-Token: mobile-token-xyz' \
  http://127.0.0.1:8080/api/bff/mobile/accounts/101/transactions
```

Ambas respuestas registraron `HTTP 200`. El contrato Mobile de la cuenta 101 incluyó solamente cuenta, nombre, saldo y tipo. La consulta de movimientos devolvió cinco registros, ordenados desde `2024-12-22` hasta `2024-07-09`.

### BFF ATM y persistencia

Antes de retirar, consultar las cuentas reales con saldo suficiente:

```bash
docker compose exec -T postgres psql -U banco_user -d banco_xyz -c "
SELECT cuenta_id, nombre, tipo, saldo_inicial, tasa, saldo_final
FROM intereses_mensuales
WHERE saldo_final >= 10000
ORDER BY saldo_final DESC
LIMIT 10;
"
```

La evidencia seleccionó la cuenta 117, cuyo saldo era 12240.00. La siguiente secuencia registró el saldo antes y después, el detalle Web y la persistencia del retiro:

```bash
curl -i -H 'X-Channel-Token: atm-token-xyz' \
  http://127.0.0.1:8080/api/bff/atm/accounts/117/balance

curl -i -H 'X-Channel-Token: web-token-xyz' \
  http://127.0.0.1:8080/api/bff/web/accounts/117

curl -i -X POST -H 'X-Channel-Token: atm-token-xyz' \
  -H 'Content-Type: application/json' -d '{"amount":10000}' \
  http://127.0.0.1:8080/api/bff/atm/accounts/117/withdraw

curl -i -H 'X-Channel-Token: atm-token-xyz' \
  http://127.0.0.1:8080/api/bff/atm/accounts/117/balance

curl -i -H 'X-Channel-Token: web-token-xyz' \
  http://127.0.0.1:8080/api/bff/web/accounts/117

docker compose exec -T postgres psql -U banco_user -d banco_xyz -c "
SELECT cuenta_id, nombre, tipo, saldo_inicial, tasa, saldo_final
FROM intereses_mensuales
WHERE cuenta_id = 117;
"
```

Todas las consultas y el retiro registraron `HTTP 200`. El retiro de 10000.00 dejó saldo 2240.00, tanto en ATM como en PostgreSQL. El detalle Web posterior mantuvo `calculatedInterest` en 240.00, calculado desde saldo inicial 12000.00 y tasa 0.0200, mientras `finalBalance` disminuyó a 2240.00.

### Seguridad y validaciones ATM

```bash
curl -i http://127.0.0.1:8080/api/bff/web/accounts/101

curl -i -H 'X-Channel-Token: web-token-xyz' \
  http://127.0.0.1:8080/api/bff/mobile/accounts/101

curl -i -H 'X-Channel-Token: mobile-token-xyz' \
  http://127.0.0.1:8080/api/bff/mobile/accounts/101

curl -i -H 'X-Channel-Token: web-token-xyz' \
  http://127.0.0.1:8080/api/bff/atm/accounts/117/balance

curl -i -X POST -H 'X-Channel-Token: atm-token-xyz' \
  -H 'Content-Type: application/json' -d '{"amount":5000}' \
  http://127.0.0.1:8080/api/bff/atm/accounts/117/withdraw

curl -i -X POST -H 'X-Channel-Token: atm-token-xyz' \
  -H 'Content-Type: application/json' -d '{"amount":-10000}' \
  http://127.0.0.1:8080/api/bff/atm/accounts/117/withdraw

curl -i -X POST -H 'X-Channel-Token: atm-token-xyz' \
  -H 'Content-Type: application/json' -d '{"amount":10000}' \
  http://127.0.0.1:8080/api/bff/atm/accounts/117/withdraw

curl -i -H 'X-Channel-Token: web-token-xyz' \
  http://127.0.0.1:8080/api/bff/web/accounts/999
```

Las evidencias registraron `401` sin token y con token de canal cruzado; `200` con el token Mobile correcto; `400` para monto 5000, monto negativo y saldo insuficiente; y `404` para la cuenta 999.

### Verificación final

```bash
docker compose ps

docker compose exec -T postgres psql -U banco_user -d banco_xyz -c "
SELECT 'transacciones_procesadas' AS tabla, COUNT(*) AS registros
FROM transacciones_procesadas
UNION ALL
SELECT 'intereses_mensuales', COUNT(*)
FROM intereses_mensuales
UNION ALL
SELECT 'movimientos_anuales', COUNT(*)
FROM movimientos_anuales;
"

find "../evidencias" -maxdepth 1 -type f -printf "%f\n" | sort
find "../evidencias/semana 03" -maxdepth 1 -type f -printf "%f\n" | sort
```

La verificación registrada confirmó PostgreSQL healthy, 387 transacciones procesadas, 50 intereses mensuales y 289 movimientos anuales. Los seis archivos de evidencia de Semana 04 están directamente en `evidencias/`; los archivos históricos de `evidencias/semana 03` se conservan separados.

# Semana 5 - Spring Security y HTTPS

## Objetivo y arquitectura

La aplicacion conserva una sola instancia Spring Boot, una base PostgreSQL y los BFF existentes. La seguridad de Semana 4 basada en `X-Channel-Token` fue reemplazada por Spring Security, HTTP Basic y roles por canal. No se modificaron controladores, DTO, servicio bancario ni procesos Spring Batch.

```text
Cliente HTTPS -> Spring Boot :8443 -> Spring Security -> BFF -> PostgreSQL
                                      ROLE_WEB    -> /api/bff/web/**
                                      ROLE_MOBILE -> /api/bff/mobile/**
                                      ROLE_ATM    -> /api/bff/atm/**
```

Se usa `BCryptPasswordEncoder` para construir usuarios en memoria, `SessionCreationPolicy.STATELESS` para no mantener sesiones y HTTP Basic como mecanismo de autenticacion. Las contrasenas se obtienen desde `application.properties` y se pueden sobrescribir mediante `WEB_PASSWORD`, `MOBILE_PASSWORD` y `ATM_PASSWORD`.

| Usuario | ROLE | Web | Mobile | ATM |
|---|---|---|---|---|
| `webuser` | `ROLE_WEB` | SI | NO | NO |
| `mobileuser` | `ROLE_MOBILE` | NO | SI | NO |
| `atmuser` | `ROLE_ATM` | NO | NO | SI |

Un HTTP 401 significa que faltan credenciales o son invalidas. Un HTTP 403 significa que el usuario ya fue autenticado, pero su rol no puede utilizar el canal solicitado.

## HTTPS local

Toda la aplicacion escucha solamente en `https://localhost:8443`. El certificado de laboratorio es autofirmado, usa un keystore PKCS12, alias `bff-local`, CN `localhost` y SAN para `localhost` e `127.0.0.1`. No se usa certificado de cliente, JWT ni OAuth2.

### Preparar PostgreSQL desde cero

Desde la ruta real del proyecto:

```bash
cd "/home/francisco/proyecto/BACKEND III/SEMANA 5/Exp1_S5_Francisco_Henriquez/proyecto"

docker compose down -v --remove-orphans
docker compose up -d postgres

until docker compose exec -T postgres pg_isready -U banco_user -d banco_xyz
do
  sleep 1
done

docker compose ps
```

Este flujo elimina el volumen local de PostgreSQL, inicia una base limpia y espera hasta que PostgreSQL acepte conexiones. Permite reproducir el saldo inicial utilizado para la prueba ATM. `docker compose down -v --remove-orphans` elimina los datos locales almacenados en el volumen.

```bash
chmod +x mvnw
chmod +x scripts/generar-certificado.sh
./scripts/generar-certificado.sh
keytool -list -v -keystore src/main/resources/keystore.p12 -storetype PKCS12 -storepass changeit
./mvnw clean package
```

El script crea `src/main/resources/keystore.p12`, que se encuentra excluido del control de versiones. Para un certificado autofirmado local, `curl` usa `-k` exclusivamente en laboratorio.

### Preparar datos para los BFF

Si PostgreSQL se inicio desde cero, ejecutar los tres Jobs Batch antes de probar los BFF Web, Mobile y ATM.

```bash
java -jar target/banco-xyz-batch-1.0.0.jar \
  --spring.main.web-application-type=none \
  --spring.batch.job.name=dailyTransactionsJob \
  ejecucion=501,java.lang.Long,true

java -jar target/banco-xyz-batch-1.0.0.jar \
  --spring.main.web-application-type=none \
  --spring.batch.job.name=monthlyInterestJob \
  ejecucion=502,java.lang.Long,true

java -jar target/banco-xyz-batch-1.0.0.jar \
  --spring.main.web-application-type=none \
  --spring.batch.job.name=annualStatementsJob \
  ejecucion=503,java.lang.Long,true
```

Los resultados esperados finales son `transacciones_procesadas = 387`, `intereses_mensuales = 50` y `movimientos_anuales = 289`.

```bash
docker compose exec -T postgres psql \
  -U banco_user \
  -d banco_xyz \
  -c "
SELECT 'transacciones_procesadas' AS tabla, COUNT(*) AS registros
FROM transacciones_procesadas
UNION ALL
SELECT 'intereses_mensuales', COUNT(*)
FROM intereses_mensuales
UNION ALL
SELECT 'movimientos_anuales', COUNT(*)
FROM movimientos_anuales;
"

docker compose exec -T postgres psql \
  -U banco_user \
  -d banco_xyz \
  -c "
SELECT cuenta_id, saldo_final
FROM intereses_mensuales
WHERE cuenta_id = 102;
"
```

El resultado esperado para la cuenta 102 es `102 | 10200.00`; este saldo inicial permite reproducir el retiro documentado: `10200.00 - 10000.00 = 200.00`.

### Levantar API HTTPS

```bash
java -jar target/banco-xyz-batch-1.0.0.jar \
  --spring.batch.job.enabled=false
```

## Pruebas verificadas

Web autorizado, sin credenciales y con password incorrecta:

```bash
curl -k -sS -i -u webuser:web123 https://localhost:8443/api/bff/web/accounts/101
curl -k -sS -i https://localhost:8443/api/bff/web/accounts/101
curl -k -sS -i -u webuser:incorrecta https://localhost:8443/api/bff/web/accounts/101
```

Mobile autorizado, movimientos y rol Web rechazado:

```bash
curl -k -sS -i -u mobileuser:mobile123 https://localhost:8443/api/bff/mobile/accounts/101
curl -k -sS -i -u mobileuser:mobile123 https://localhost:8443/api/bff/mobile/accounts/101/transactions
curl -k -sS -i -u webuser:web123 https://localhost:8443/api/bff/mobile/accounts/101
```

ATM y retiro HTTPS sobre la cuenta verificada 102:

```bash
curl -k -sS -i -u atmuser:atm123 https://localhost:8443/api/bff/atm/accounts/102/balance
curl -k -sS -i -u webuser:web123 https://localhost:8443/api/bff/atm/accounts/102/balance
curl -k -sS -i -u mobileuser:mobile123 https://localhost:8443/api/bff/atm/accounts/102/balance
curl -k -sS -i -u atmuser:atm123 -X POST -H 'Content-Type: application/json' -d '{"amount":10000}' https://localhost:8443/api/bff/atm/accounts/102/withdraw
```

El POST ATM queda excluido de CSRF solamente bajo `/api/bff/**`, porque es una API REST stateless autenticada mediante HTTP Basic. La prueba real registro el saldo de la cuenta 102 desde 10200.00 hasta 200.00.

En Postman, para este certificado local, usar temporalmente `Settings -> General -> SSL certificate verification -> OFF`; en cada solicitud seleccionar `Authorization -> Basic Auth` y utilizar una de las credenciales de la tabla. No se debe enviar `X-Channel-Token`.

## Evidencias de Semana 5

| Evidencia | Contenido |
|---|---|
| `evidencias/01_compilacion_https.txt` | Compilacion, PostgreSQL y puerto HTTPS |
| `evidencias/02_certificado_https.txt` | Certificado PKCS12 y SAN |
| `evidencias/03_seguridad_web.txt` | Respuestas Web 200 y 401 |
| `evidencias/04_seguridad_mobile.txt` | Respuestas Mobile 200 y 403 |
| `evidencias/05_seguridad_atm.txt` | Respuestas ATM 200 y 403 |
| `evidencias/06_autorizacion_roles.txt` | Matriz real de roles |
| `evidencias/07_atm_retiro_https.txt` | Retiro HTTPS y persistencia PostgreSQL |
| `evidencias/08_verificacion_final.txt` | Estado final, puerto y conteos PostgreSQL |
| `evidencias/09_pruebas_automatizadas.txt` | Suite automatizada: pruebas unitarias, Spring Security/BFF e integracion Batch con PostgreSQL temporal |

### Pruebas automatizadas

El proyecto incorpora pruebas unitarias Batch, integración Spring Security y BFF, e integraciones Batch de regresión con PostgreSQL temporal. La suite protege resultados Batch y permisos por canal ya validados.

La ejecucion validada finalizo con 51 pruebas ejecutadas, 0 fallos, 0 errores y 0 pruebas omitidas.

```bash
cd "/home/francisco/proyecto/BACKEND III/SEMANA 5/Exp1_S5_Francisco_Henriquez/proyecto"
./mvnw test
./mvnw clean test
```

Las integraciones Batch usan Testcontainers y requieren Docker disponible para levantar PostgreSQL temporal.

# Semana 6 - Microservicios y seguridad con Spring Cloud

## Evolución arquitectónica

Semana 5 concentraba los controladores Web, Mobile y ATM y `BffBankingService` en una sola aplicación. Para esta entrega el proyecto se reorganizó en módulos Spring Boot independientes dentro de `./proyecto`:

```text
config-server:8888 -> configuración centralizada (config-repo)
eureka-server:8761 -> descubrimiento
core-banking-service:8081 -> PostgreSQL, consultas, movimientos y retiro
bff-web-service:8082 -> DTO Web, ROLE_WEB, JWT y Circuit Breaker
bff-mobile-service:8083 -> DTO Mobile, ROLE_MOBILE, JWT y Circuit Breaker
bff-atm-service:8084 -> saldo/retiro, ROLE_ATM, JWT y Circuit Breaker
```

El Core conserva el modelo, el esquema, los archivos de entrada y la configuración Batch de las semanas anteriores. Los BFF ya no acceden a PostgreSQL: resuelven `core-banking-service` mediante Eureka. Mobile conserva la consulta SQL `LIMIT 5` desde el Core; ATM valida monto múltiplo de 10.000, saldo disponible y persiste el nuevo saldo con una transacción.

## Seguridad

Cada BFF expone `POST /auth/login` con usuarios académicos `webuser/web123`, `mobileuser/mobile123` y `atmuser/atm123`. Las contraseñas se codifican con BCrypt, las sesiones son STATELESS y los JWT llevan el rol del usuario. Los endpoints de cada canal requieren respectivamente `ROLE_WEB`, `ROLE_MOBILE` y `ROLE_ATM`; el filtro devuelve 401 para token ausente/inválido y 403 para rol incorrecto. HTTP Basic no se utiliza como mecanismo final.

Config Server usa `./config-repo`; todos los módulos tienen respaldo local de desarrollo y consumen `spring.config.import` cuando Config Server está disponible. Cada llamada BFF -> Core tiene un Circuit Breaker Resilience4j y fallback HTTP 503 controlado.

## Reproducción completa de evidencias — Semana 06

Este procedimiento corresponde a la Opción A y fue utilizado para generar las evidencias entregadas.

Se ejecuta en una sola consola WSL y mantiene los servicios en segundo plano mediante `nohup`.

### 1. Definir las rutas de trabajo

```bash
ROOT="/home/francisco/proyecto/BACKEND III/SEMANA 6/Exp3_S6_Grupo11"
PROY="$ROOT/proyecto"
EVID="$ROOT/evidencias/semana 06"

mkdir -p "$EVID"

cd "$PROY"

echo "PROYECTO:"
pwd

echo
echo "EVIDENCIAS:"
echo "$EVID"
```

Resultado esperado:

```text
PROYECTO:
/home/francisco/proyecto/BACKEND III/SEMANA 6/Exp3_S6_Grupo11/proyecto

EVIDENCIAS:
/home/francisco/proyecto/BACKEND III/SEMANA 6/Exp3_S6_Grupo11/evidencias/semana 06
```

```bash
ls -ld "$EVID"
```

### 2. Detener instancias anteriores

```bash
pkill -f 'config-server/target/config-server-1.0.0.jar' 2>/dev/null || true
pkill -f 'eureka-server/target/eureka-server-1.0.0.jar' 2>/dev/null || true
pkill -f 'core-banking-service/target/core-banking-service-1.0.0.jar' 2>/dev/null || true
pkill -f 'bff-web-service/target/bff-web-service-1.0.0.jar' 2>/dev/null || true
pkill -f 'bff-mobile-service/target/bff-mobile-service-1.0.0.jar' 2>/dev/null || true
pkill -f 'bff-atm-service/target/bff-atm-service-1.0.0.jar' 2>/dev/null || true

sleep 3

ss -ltnp | grep -E ':(8081|8082|8083|8084|8761|8888)\b' || true
```

Para un inicio limpio no deberia aparecer ninguno de estos puertos.

### 3. Reiniciar PostgreSQL desde una base limpia

```bash
cd "$PROY"

docker compose down -v --remove-orphans
docker compose up -d postgres
docker ps

until docker exec banco-xyz-postgres \
    pg_isready -U banco_user -d banco_xyz >/dev/null 2>&1
do
    echo "Esperando PostgreSQL..."
    sleep 2
done

echo "PostgreSQL OK"
```

docker compose down -v --remove-orphans elimina el volumen PostgreSQL y los datos de ejecuciones anteriores. De esta forma las pruebas de Semana 6 comienzan desde una base limpia y reproducible.

El archivo `docker-compose.yml` también contiene los servicios necesarios para la ejecución completa mediante Docker. Sin embargo, `docker compose up -d postgres` utilizado en esta reproducción inicia únicamente PostgreSQL; los demás componentes se ejecutan mediante los comandos `java -jar` documentados en esta sección. La ejecución completa mediante Docker Compose se presenta posteriormente como una alternativa.

### 4. Compilar el proyecto y generar evidencia 18 - Build real

Este comando ejecuta `clean package` sobre el proyecto multimodulo, compila los seis componentes y genera los JAR que se utilizaran en los pasos siguientes. La misma ejecucion se almacena como evidencia `18_build.txt`.

```bash
cd "$PROY"

{
    echo "DIRECTORIO DE EJECUCION:"
    pwd
    echo
    echo "COMANDO:"
    echo "./mvnw -f pom.xml clean package"
    echo
    ./mvnw -f pom.xml clean package
} 2>&1 | tee "$EVID/18_build.txt"

grep -E "BUILD SUCCESS|BUILD FAILURE" "$EVID/18_build.txt"
tail -30 "$EVID/18_build.txt"
```

El resultado esperado es `BUILD SUCCESS`.

### 5. Verificar los seis JAR

```bash
ls -lh \
config-server/target/config-server-1.0.0.jar \
eureka-server/target/eureka-server-1.0.0.jar \
core-banking-service/target/core-banking-service-1.0.0.jar \
bff-web-service/target/bff-web-service-1.0.0.jar \
bff-mobile-service/target/bff-mobile-service-1.0.0.jar \
bff-atm-service/target/bff-atm-service-1.0.0.jar
```

Deben aparecer los seis artefactos.

### 6. Crear función de espera

```bash
wait_url() {
    URL="$1"
    NAME="$2"

    for i in {1..90}
    do
        if curl -fsS "$URL" >/dev/null 2>&1
        then
            echo "$NAME OK"
            return 0
        fi

        sleep 1
    done

    echo "ERROR: $NAME no inicio correctamente"
    return 1
}
```

### 7. Levantar Config Server

```bash
cd "$PROY"

nohup java -jar config-server/target/config-server-1.0.0.jar \
    > /tmp/s6_config.log 2>&1 &

echo $! > /tmp/s6_config.pid
wait_url "http://localhost:8888/actuator/health" "Config Server"
```

Resultado esperado: `Config Server OK`.

### 8. Levantar Eureka Server

```bash
nohup java -jar eureka-server/target/eureka-server-1.0.0.jar \
    > /tmp/s6_eureka.log 2>&1 &

echo $! > /tmp/s6_eureka.pid
wait_url "http://localhost:8761/actuator/health" "Eureka Server"
```

Resultado esperado: `Eureka Server OK`.

### 9. Levantar Core Banking

```bash
nohup java -jar core-banking-service/target/core-banking-service-1.0.0.jar \
    > /tmp/s6_core.log 2>&1 &

echo $! > /tmp/s6_core.pid
wait_url "http://localhost:8081/actuator/health" "Core Banking"
```

Resultado esperado: `Core Banking OK`.

### 10. Ejecutar nuevamente los Jobs Batch

```bash
cd "$PROY"

nohup java -jar core-banking-service/target/core-banking-service-1.0.0.jar \
  --server.port=0 \
  --eureka.client.enabled=false \
  --spring.batch.job.enabled=true \
  --spring.batch.job.name=dailyTransactionsJob \
  ejecucion=601,java.lang.Long,true \
  > /tmp/s6_batch_daily.log 2>&1 &

DAILY_PID=$!

echo "PID dailyTransactionsJob: $DAILY_PID"

for i in {1..60}
do
    if grep -qE "dailyTransactionsJob.*(estado=COMPLETED|status: \[COMPLETED\])" \
        /tmp/s6_batch_daily.log 2>/dev/null
    then
        echo "dailyTransactionsJob COMPLETED"
        break
    fi

    if grep -qE "dailyTransactionsJob.*(estado=FAILED|status: \[FAILED\])|APPLICATION FAILED TO START" \
        /tmp/s6_batch_daily.log 2>/dev/null
    then
        echo "ERROR: dailyTransactionsJob FAILED"
        tail -40 /tmp/s6_batch_daily.log
        break
    fi

    if [ "$i" -eq 60 ]
    then
        echo "ERROR: timeout esperando dailyTransactionsJob"
        tail -40 /tmp/s6_batch_daily.log
    fi

    sleep 2
done

grep -E "dailyTransactionsJob|COMPLETED|FAILED" /tmp/s6_batch_daily.log | tail -20

kill "$DAILY_PID" 2>/dev/null || true
wait "$DAILY_PID" 2>/dev/null || true

nohup java -jar core-banking-service/target/core-banking-service-1.0.0.jar \
  --server.port=0 \
  --eureka.client.enabled=false \
  --spring.batch.job.enabled=true \
  --spring.batch.job.name=monthlyInterestJob \
  ejecucion=602,java.lang.Long,true \
  > /tmp/s6_batch_monthly.log 2>&1 &

MONTHLY_PID=$!

echo "PID monthlyInterestJob: $MONTHLY_PID"

for i in {1..60}
do
    if grep -qE "monthlyInterestJob.*(estado=COMPLETED|status: \[COMPLETED\])" \
        /tmp/s6_batch_monthly.log 2>/dev/null
    then
        echo "monthlyInterestJob COMPLETED"
        break
    fi

    if grep -qE "monthlyInterestJob.*(estado=FAILED|status: \[FAILED\])|APPLICATION FAILED TO START" \
        /tmp/s6_batch_monthly.log 2>/dev/null
    then
        echo "ERROR: monthlyInterestJob FAILED"
        tail -40 /tmp/s6_batch_monthly.log
        break
    fi

    if [ "$i" -eq 60 ]
    then
        echo "ERROR: timeout esperando monthlyInterestJob"
        tail -40 /tmp/s6_batch_monthly.log
    fi

    sleep 2
done

grep -E "monthlyInterestJob|COMPLETED|FAILED" /tmp/s6_batch_monthly.log | tail -20

kill "$MONTHLY_PID" 2>/dev/null || true
wait "$MONTHLY_PID" 2>/dev/null || true

nohup java -jar core-banking-service/target/core-banking-service-1.0.0.jar \
  --server.port=0 \
  --eureka.client.enabled=false \
  --spring.batch.job.enabled=true \
  --spring.batch.job.name=annualStatementsJob \
  ejecucion=603,java.lang.Long,true \
  > /tmp/s6_batch_annual.log 2>&1 &

ANNUAL_PID=$!

echo "PID annualStatementsJob: $ANNUAL_PID"

for i in {1..60}
do
    if grep -qE "annualStatementsJob.*(estado=COMPLETED|status: \[COMPLETED\])" \
        /tmp/s6_batch_annual.log 2>/dev/null
    then
        echo "annualStatementsJob COMPLETED"
        break
    fi

    if grep -qE "annualStatementsJob.*(estado=FAILED|status: \[FAILED\])|APPLICATION FAILED TO START" \
        /tmp/s6_batch_annual.log 2>/dev/null
    then
        echo "ERROR: annualStatementsJob FAILED"
        tail -40 /tmp/s6_batch_annual.log
        break
    fi

    if [ "$i" -eq 60 ]
    then
        echo "ERROR: timeout esperando annualStatementsJob"
        tail -40 /tmp/s6_batch_annual.log
    fi

    sleep 2
done

grep -E "annualStatementsJob|COMPLETED|FAILED" /tmp/s6_batch_annual.log | tail -20

kill "$ANNUAL_PID" 2>/dev/null || true
wait "$ANNUAL_PID" 2>/dev/null || true
```

Cada Job Batch se ejecuta mediante una instancia temporal del `core-banking-service` con un puerto aleatorio. El procedimiento espera explicitamente que el log informe `COMPLETED` o `FAILED`, con un tiempo maximo de espera de 120 segundos. Una vez obtenido el resultado, la instancia temporal se detiene automaticamente.

Se utiliza `--server.port=0` para mantener el contexto web requerido por la configuracion de seguridad sin entrar en conflicto con el Core Banking principal que utiliza el puerto 8081. Adicionalmente, `--eureka.client.enabled=false` evita registrar estas instancias temporales en Eureka.

### 11. Comprobar datos generados por los Jobs Batch

```bash
docker exec -i banco-xyz-postgres \
psql -U banco_user -d banco_xyz \
-c "
SELECT 'transacciones_procesadas' AS tabla, COUNT(*) AS registros
FROM transacciones_procesadas
UNION ALL
SELECT 'intereses_mensuales', COUNT(*)
FROM intereses_mensuales
UNION ALL
SELECT 'movimientos_anuales', COUNT(*)
FROM movimientos_anuales;
"

docker exec -i banco-xyz-postgres \
psql -U banco_user -d banco_xyz \
-c "
SELECT cuenta_id,nombre,tipo,saldo_inicial,tasa,saldo_final
FROM intereses_mensuales
WHERE cuenta_id=104;
"

docker exec -i banco-xyz-postgres \
psql -U banco_user -d banco_xyz \
-c "
SELECT COUNT(*) AS movimientos_cuenta_104
FROM movimientos_anuales
WHERE cuenta_id=104;
"
```

Estas consultas son solamente `SELECT` y no modifican ningun dato.

### 12. Levantar BFF Web

```bash
cd "$PROY"

nohup java -jar bff-web-service/target/bff-web-service-1.0.0.jar \
    > /tmp/s6_web.log 2>&1 &

echo $! > /tmp/s6_web.pid
wait_url "http://localhost:8082/actuator/health" "BFF Web"
```

### 13. Levantar BFF Mobile

```bash
nohup java -jar bff-mobile-service/target/bff-mobile-service-1.0.0.jar \
    > /tmp/s6_mobile.log 2>&1 &

echo $! > /tmp/s6_mobile.pid
wait_url "http://localhost:8083/actuator/health" "BFF Mobile"
```

### 14. Levantar BFF ATM

```bash
nohup java -jar bff-atm-service/target/bff-atm-service-1.0.0.jar \
    > /tmp/s6_atm.log 2>&1 &

echo $! > /tmp/s6_atm.pid
wait_url "http://localhost:8084/actuator/health" "BFF ATM"
```

### 15. Esperar registro en Eureka, evidencias de infraestructura y JWT

```bash
for i in {1..60}; do
  APPS=$(curl -sS -H "Accept: application/xml" http://localhost:8761/eureka/apps)
  if echo "$APPS" | grep -q '<name>CORE-BANKING-SERVICE</name>' && echo "$APPS" | grep -q '<name>BFF-WEB-SERVICE</name>' && echo "$APPS" | grep -q '<name>BFF-MOBILE-SERVICE</name>' && echo "$APPS" | grep -q '<name>BFF-ATM-SERVICE</name>'; then
    echo "Los cuatro servicios estan registrados en Eureka."; break
  fi
  echo "Esperando registro completo en Eureka..."; sleep 2
done
curl -sS -H "Accept: application/xml" http://localhost:8761/eureka/apps | grep -E '<name>|<status>|<port'

{ echo "Comando: curl http://localhost:8888/core-banking-service/default"; echo; curl -sS http://localhost:8888/core-banking-service/default; echo; } > "$EVID/01_config_server.txt"
{ echo "Comando: curl -i http://localhost:8081/api/config/origin"; echo; curl -sS -i http://localhost:8081/api/config/origin; } > "$EVID/01b_consumo_config_server.txt"
{ echo "Comando: curl -i http://localhost:8761/actuator/health"; echo; curl -sS -i http://localhost:8761/actuator/health; } > "$EVID/02_eureka_server.txt"
curl -sS -H "Accept: application/xml" http://localhost:8761/eureka/apps > "$EVID/03_servicios_registrados.xml"
{ echo "Comando: curl -H 'Accept: application/xml' http://localhost:8761/eureka/apps"; echo; cat "$EVID/03_servicios_registrados.xml"; } > "$EVID/03_servicios_registrados.txt"

curl -sS -D /tmp/s6_atm_login_headers -o /tmp/s6_atm_login_body -X POST -H "Content-Type: application/json" -d '{"username":"atmuser","password":"atm123"}' http://localhost:8084/auth/login
ATM_TOKEN=$(python3 -c 'import json; print(json.load(open("/tmp/s6_atm_login_body"))["accessToken"])')
WEB_TOKEN=$(curl -sS -X POST -H "Content-Type: application/json" -d '{"username":"webuser","password":"web123"}' http://localhost:8082/auth/login | python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])')
MOBILE_TOKEN=$(curl -sS -X POST -H "Content-Type: application/json" -d '{"username":"mobileuser","password":"mobile123"}' http://localhost:8083/auth/login | python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])')
echo "WEB: ${#WEB_TOKEN}"; echo "MOBILE: ${#MOBILE_TOKEN}"; echo "ATM: ${#ATM_TOKEN}"
```

### Evidencias funcionales, seguridad y ATM

```bash
{ echo "Usuario: atmuser"; echo "Endpoint: POST /auth/login"; echo "HTTP: $(head -n 1 /tmp/s6_atm_login_headers | tr -d '\r' | awk '{print $2}')"; echo "Comando: curl -X POST -H 'Content-Type: application/json' -d '{\"username\":\"atmuser\",\"password\":\"atm123\"}' http://localhost:8084/auth/login"; echo; echo "Body real (JWT oculto):"; python3 -c 'import json; d=json.load(open("/tmp/s6_atm_login_body")); d["accessToken"]="[JWT emitido]"; print(json.dumps(d,ensure_ascii=False))'; } > "$EVID/04_jwt_login.txt"
{ echo "Usuario: webuser / ROLE_WEB"; echo "Endpoint: GET /api/bff/web/accounts/104"; echo; curl -sS -i -H "Authorization: Bearer $WEB_TOKEN" http://localhost:8082/api/bff/web/accounts/104; } > "$EVID/05_web_200.txt"
{ echo "Usuario: mobileuser / ROLE_MOBILE"; echo "Endpoint: GET /api/bff/mobile/accounts/104"; echo; curl -sS -i -H "Authorization: Bearer $MOBILE_TOKEN" http://localhost:8083/api/bff/mobile/accounts/104; } > "$EVID/06_mobile_200.txt"
{ echo "Usuario: atmuser / ROLE_ATM"; echo "Endpoint: GET /api/bff/atm/accounts/104/balance"; echo; curl -sS -i -H "Authorization: Bearer $ATM_TOKEN" http://localhost:8084/api/bff/atm/accounts/104/balance; } > "$EVID/07_atm_200.txt"
{ echo "Prueba: acceso Web sin JWT"; echo; curl -sS -i http://localhost:8082/api/bff/web/accounts/104; } > "$EVID/08_sin_token_401.txt"
{ echo "Usuario autenticado: webuser / ROLE_WEB"; echo "Endpoint solicitado: BFF Mobile"; echo; curl -sS -i -H "Authorization: Bearer $WEB_TOKEN" http://localhost:8083/api/bff/mobile/accounts/104; } > "$EVID/09_rol_incorrecto_403.txt"
{ echo "Usuario: mobileuser / ROLE_MOBILE"; echo "Endpoint: GET /api/bff/mobile/accounts/104/transactions"; echo; curl -sS -i -H "Authorization: Bearer $MOBILE_TOKEN" http://localhost:8083/api/bff/mobile/accounts/104/transactions; } > "$EVID/10_mobile_ultimos_5.txt"
curl -sS -H "Authorization: Bearer $MOBILE_TOKEN" http://localhost:8083/api/bff/mobile/accounts/104/transactions | python3 -c 'import json,sys; d=json.load(sys.stdin); print("Cantidad de movimientos:",len(d))'

{ echo "Cuenta: 104"; echo "Usuario: atmuser"; echo; curl -sS -i -H "Authorization: Bearer $ATM_TOKEN" http://localhost:8084/api/bff/atm/accounts/104/balance; } > "$EVID/11_atm_saldo_inicial.txt"
{ echo "Cuenta: 104"; echo "Monto: 10000"; echo "Usuario: atmuser"; echo; curl -sS -i -X POST -H "Authorization: Bearer $ATM_TOKEN" -H "Content-Type: application/json" -d '{"amount":10000}' http://localhost:8084/api/bff/atm/accounts/104/withdraw; } > "$EVID/12_atm_retiro.txt"
{ echo "Cuenta: 104"; echo "Saldo final por BFF y verificacion PostgreSQL:"; echo; curl -sS -i -H "Authorization: Bearer $ATM_TOKEN" http://localhost:8084/api/bff/atm/accounts/104/balance; echo; echo "CONSULTA POSTGRESQL:"; echo "SQL: SELECT cuenta_id,saldo_final FROM intereses_mensuales WHERE cuenta_id=104;"; docker exec -i banco-xyz-postgres psql -U banco_user -d banco_xyz -c "SELECT cuenta_id,saldo_final FROM intereses_mensuales WHERE cuenta_id=104;"; } > "$EVID/13_atm_saldo_final.txt"
```

El escenario esperado es saldo inicial `10200.00`, retiro `10000`, saldo final y PostgreSQL `200.00`.

### Circuit Breaker, recuperación y validación

```bash
kill "$(cat /tmp/s6_core.pid)"
for i in {1..30}; do if ! ss -ltn | grep -q ':8081 '; then echo "Core Banking detenido."; break; fi; sleep 1; done
ss -ltnp | grep ':8081' || echo "Puerto 8081 libre - Core detenido correctamente"
{ echo "Core Banking detenido."; echo; curl -sS -i -H "Authorization: Bearer $WEB_TOKEN" http://localhost:8082/api/bff/web/accounts/104; } > "$EVID/14_circuit_breaker_web.txt"
{ echo "Core Banking detenido."; echo; curl -sS -i -H "Authorization: Bearer $MOBILE_TOKEN" http://localhost:8083/api/bff/mobile/accounts/104; } > "$EVID/15_circuit_breaker_mobile.txt"
{ echo "Core Banking detenido."; echo; curl -sS -i -H "Authorization: Bearer $ATM_TOKEN" http://localhost:8084/api/bff/atm/accounts/104/balance; } > "$EVID/16_circuit_breaker_atm.txt"

nohup java -jar core-banking-service/target/core-banking-service-1.0.0.jar > /tmp/s6_core.log 2>&1 &
echo $! > /tmp/s6_core.pid
wait_url "http://localhost:8081/actuator/health" "Core Banking recuperado"
for i in {1..60}; do if curl -fsS -H "Accept: application/xml" http://localhost:8761/eureka/apps/CORE-BANKING-SERVICE | grep -q '<status>UP</status>'; then echo "CORE-BANKING-SERVICE nuevamente UP en Eureka."; break; fi; sleep 2; done
RECOVERY_CODE=""
for i in {1..30}; do RECOVERY_CODE=$(curl -sS -o /tmp/s6_recovery_body -w "%{http_code}" -H "Authorization: Bearer $WEB_TOKEN" http://localhost:8082/api/bff/web/accounts/104); echo "Intento $i -> HTTP $RECOVERY_CODE"; [ "$RECOVERY_CODE" = "200" ] && { echo "BFF Web recuperado correctamente."; break; }; sleep 2; done
{ echo "Core Banking reiniciado."; echo; echo "CORE-BANKING-SERVICE EN EUREKA:"; curl -sS -H "Accept: application/xml" http://localhost:8761/eureka/apps/CORE-BANKING-SERVICE; echo; echo "LLAMADA WEB DESPUES DE LA RECUPERACION:"; curl -sS -i -H "Authorization: Bearer $WEB_TOKEN" http://localhost:8082/api/bff/web/accounts/104; } > "$EVID/17_recuperacion_core.txt"
{ echo "Puertos y procesos activos durante la prueba:"; echo; ss -ltnp | grep -E ':(8761|8888|8081|8082|8083|8084)\b'; } > "$EVID/19_servicios_activos.txt"

echo "=== ARCHIVOS DE EVIDENCIA ==="
find "$EVID" -maxdepth 1 -type f -printf '%f\n' | sort

echo
echo "=== RESULTADOS HTTP ==="
grep -H "HTTP/1.1" "$EVID"/*.txt

echo
echo "=== RESULTADO ATM ==="
grep -E '"balance"|"withdrawnAmount"|"finalBalance"|200.00' \
    "$EVID/11_atm_saldo_inicial.txt" \
    "$EVID/12_atm_retiro.txt" \
    "$EVID/13_atm_saldo_final.txt"

echo
echo "=== BUILD ==="
grep -E "Reactor Summary|BUILD SUCCESS|BUILD FAILURE" \
    "$EVID/18_build.txt"

echo
echo "=== EUREKA ==="
grep -E '<name>|<status>|<port' \
    "$EVID/03_servicios_registrados.xml"
```

Los resultados esperados son operaciones `200`, ausencia de token `401`, rol incorrecto `403`, los tres fallbacks `503` y nuevamente `200` tras recuperación. Los archivos esperados son `01_config_server.txt`, `01b_consumo_config_server.txt`, `02_eureka_server.txt`, `03_servicios_registrados.txt`, `03_servicios_registrados.xml`, `04_jwt_login.txt`, `05_web_200.txt`, `06_mobile_200.txt`, `07_atm_200.txt`, `08_sin_token_401.txt`, `09_rol_incorrecto_403.txt`, `10_mobile_ultimos_5.txt`, `11_atm_saldo_inicial.txt`, `12_atm_retiro.txt`, `13_atm_saldo_final.txt`, `14_circuit_breaker_web.txt`, `15_circuit_breaker_mobile.txt`, `16_circuit_breaker_atm.txt`, `17_recuperacion_core.txt`, `18_build.txt` y `19_servicios_activos.txt`.


### Ejecución alternativa completa mediante Docker Compose

Esta es una alternativa para facilitar la reproducción completa de la arquitectura. No se ejecuta después de la Opción A, no reemplaza el procedimiento detallado usado para generar las evidencias y no genera evidencias nuevas.

```bash
cd "/home/francisco/proyecto/BACKEND III/SEMANA 6/Exp3_S6_Grupo11/proyecto"

docker compose down -v --remove-orphans
docker compose build
docker compose up -d

docker compose ps -a
```

El resultado esperado es PostgreSQL, Config Server, Eureka, Core Banking y los tres BFF en ejecución saludable; `batch-init` debe finalizar con `Exited (0)`. La representación exacta puede variar según la versión de Docker Compose, pero el estado funcional debe ser equivalente.

```bash
curl -sS http://localhost:8888/actuator/health
curl -sS http://localhost:8761/actuator/health
curl -sS http://localhost:8081/actuator/health
curl -sS http://localhost:8082/actuator/health
curl -sS http://localhost:8083/actuator/health
curl -sS http://localhost:8084/actuator/health

docker compose logs batch-init
```

El comando `docker compose logs batch-init` permite revisar el resultado de los tres Jobs y observar los estados `COMPLETED` impresos por el script.

Para detener la Opción B usar `docker compose down`. Para eliminar también PostgreSQL usar `docker compose down -v --remove-orphans`.
