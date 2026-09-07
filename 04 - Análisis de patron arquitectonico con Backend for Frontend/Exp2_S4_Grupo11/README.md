# Banco XYZ - Backend for Frontend con Spring Boot

## Objetivo

Este proyecto implementa el patron **Backend for Frontend (BFF)** para el Banco XYZ. La aplicacion carga datos desde archivos CSV hacia MySQL con Spring Batch y expone tres backends especializados:

- **BFF Web**: entrega datos completos para interfaces de escritorio o navegador.
- **BFF Movil**: entrega respuestas ligeras con los datos esenciales.
- **BFF Cajeros Automaticos**: expone solo operaciones criticas y controladas, como consulta de saldo y retiro.

## Estrategia de Implementacion BFF

La estrategia seleccionada es **un BFF por canal dentro del mismo proyecto Spring Boot**, separado por controladores, rutas, DTOs y token de canal.

Esta estrategia conviene para la actividad porque mantiene una sola base de despliegue para un proyecto academico de alcance acotado, permite que cada cliente tenga su propio contrato de API sin duplicar la logica de acceso a datos, facilita demostrar diferencias reales entre canales y conserva integridad porque todos los BFF leen y escriben sobre la misma fuente procesada desde CSV.

En un escenario productivo con mayor escala, cada BFF podria separarse en microservicios independientes. Para este caso, la separacion modular en un monolito Spring Boot es suficiente, clara y mantenible.

## Tecnologias

- Java 21
- Spring Boot 3.5.7
- Spring Web
- Spring Batch
- Spring Data JPA
- MySQL 8
- Maven Wrapper
- Docker Compose

## Datos CSV

Los archivos base se encuentran en `src/main/resources/input`:

- `transacciones.csv`: movimientos diarios.
- `intereses.csv`: cuentas, saldos y tipo de producto.
- `cuentas_anuales.csv`: movimientos anuales por cuenta.

Spring Batch procesa estos archivos y crea tablas operacionales y de reporte:

- `transacciones`
- `intereses`
- `cuentas_anuales`
- `resumen_transacciones_diarias`
- `informe_estados_cuenta_anuales`

## Estructura del Codigo

```text
src/main/java/com/backen3/Duoc
├── DuocApplication.java
├── batch
│   └── CsvBatchConfig.java
├── bff
│   ├── AtmBffController.java
│   ├── MobileBffController.java
│   └── WebBffController.java
├── config
│   ├── ChannelAuthInterceptor.java
│   └── WebConfig.java
├── dto
│   ├── AtmBalanceResponse.java
│   ├── AtmWithdrawalRequest.java
│   ├── AtmWithdrawalResponse.java
│   ├── MobileAccountResponse.java
│   ├── MobileMovementResponse.java
│   ├── WebAccountDetailResponse.java
│   ├── WebAnnualReportResponse.java
│   └── WebDailySummaryResponse.java
├── model
│   ├── CuentaAnual.java
│   ├── Interes.java
│   └── Transaccion.java
└── service
    └── BffBankingService.java
```

## Autenticacion y Autorizacion por Canal

Cada BFF exige el header `X-Channel-Token`.

Tokens por defecto:

```text
Web:    web-token-xyz
Movil:  mobile-token-xyz
Cajero: atm-token-xyz
```

Tambien se pueden configurar con variables de entorno:

```bash
export BFF_WEB_TOKEN=web-token-xyz
export BFF_MOBILE_TOKEN=mobile-token-xyz
export BFF_ATM_TOKEN=atm-token-xyz
```

## Endpoints BFF

### BFF Web

Pensado para navegadores e interfaces complejas. Devuelve informacion detallada.

```http
GET /api/bff/web/cuentas
GET /api/bff/web/resumen-transacciones
GET /api/bff/web/informe-anual
```

### BFF Movil

Pensado para bajo consumo de datos. Devuelve respuestas compactas.

```http
GET /api/bff/mobile/cuentas
GET /api/bff/mobile/cuentas/{cuentaId}/movimientos
```

### BFF Cajeros Automaticos

Pensado para operaciones criticas, con respuestas acotadas.

```http
GET  /api/bff/atm/cuentas/{cuentaId}/saldo
POST /api/bff/atm/cuentas/{cuentaId}/retiro
```

El retiro valida monto mayor a cero, monto multiplo de `10000` y saldo disponible suficiente.

## Ejecucion del Proyecto

### 1. Levantar MySQL con Docker

```bash
export MYSQL_ROOT_PASSWORD=admin123
docker compose up -d mysql
```

### 2. Ejecutar la aplicacion

```bash
export SPRING_DATASOURCE_PASSWORD=admin123
./mvnw spring-boot:run -Dspring-boot.run.arguments="--fechaProceso=2024-01"
```

Al iniciar, la aplicacion ejecuta los Jobs Batch y carga los datos desde los CSV.

### 3. Ejecutar pruebas

```bash
./mvnw test
```

Resultado esperado:

```text
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Evidencia de Ejecucion

### BFF Web

```bash
curl -H "X-Channel-Token: web-token-xyz" \
  http://localhost:8080/api/bff/web/cuentas
```

Salida esperada:

```json
[
  {
    "cuentaId": 101,
    "nombre": "John Doe",
    "edad": 30,
    "tipo": "ahorro",
    "saldoInicial": 5000.00,
    "tasaInteres": 0.0150,
    "interesCalculado": 75.00,
    "saldoFinal": 5075.00
  }
]
```

```bash
curl -H "X-Channel-Token: web-token-xyz" \
  http://localhost:8080/api/bff/web/resumen-transacciones
```

### BFF Movil

```bash
curl -H "X-Channel-Token: mobile-token-xyz" \
  http://localhost:8080/api/bff/mobile/cuentas
```

Salida esperada:

```json
[
  {
    "cuentaId": 101,
    "nombre": "John Doe",
    "saldo": 5075.00,
    "tipo": "ahorro"
  }
]
```

```bash
curl -H "X-Channel-Token: mobile-token-xyz" \
  http://localhost:8080/api/bff/mobile/cuentas/101/movimientos
```

### BFF Cajero Automatico

```bash
curl -H "X-Channel-Token: atm-token-xyz" \
  http://localhost:8080/api/bff/atm/cuentas/101/saldo
```

Salida esperada:

```json
{
  "cuentaId": 101,
  "saldoDisponible": 5075.00,
  "estado": "OPERATIVA",
  "canal": "ATM"
}
```

```bash
curl -X POST \
  -H "Content-Type: application/json" \
  -H "X-Channel-Token: atm-token-xyz" \
  -d '{"monto":10000}' \
  http://localhost:8080/api/bff/atm/cuentas/107/retiro
```

### Evidencia de Seguridad

Solicitud sin token o con token incorrecto:

```bash
curl http://localhost:8080/api/bff/web/cuentas
```

Salida esperada:

```json
{
  "error": "No autorizado",
  "detalle": "Token de canal invalido o ausente"
}
```

## Integridad y Consistencia

El proyecto mantiene consistencia de datos mediante:

- Claves primarias y restricciones `NOT NULL` en `schema.sql`.
- Carga idempotente con `ON DUPLICATE KEY UPDATE`.
- Filtro de registros invalidos en los processors de Spring Batch.
- Calculo centralizado de intereses y reportes.
- Transaccion en la operacion de retiro del BFF de cajeros.
- Contratos separados por canal para evitar exponer datos innecesarios.

## Repositorio GitHub

Para la entrega, crear un repositorio en GitHub y subir este proyecto:

```bash
git init
git add .
git commit -m "Implementa patron Backend for Frontend Banco XYZ"
git branch -M main
git remote add origin https://github.com/USUARIO/REPOSITORIO.git
git push -u origin main
```
