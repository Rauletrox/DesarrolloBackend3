# Instrucciones para ejecutar y verificar Banco XYZ

Esta guía permite iniciar el proyecto con Docker y verificar cada componente desde Windows PowerShell y Postman. Ejecutar los pasos en orden. Los comandos están escritos para PowerShell; no copiar las continuaciones `\` de ejemplos para Bash. Mantener la misma terminal para conservar las variables de las pruebas.

## 1. Requisitos y preparación

1. Instalar Docker Desktop con soporte para contenedores Linux y abrirlo.
2. Esperar a que el motor de Docker esté funcionando.
3. Instalar Postman si se desean capturas de las peticiones HTTP.
4. Tener conexión a Internet para descargar imágenes, Maven y dependencias durante la primera compilación.
5. Abrir PowerShell dentro de la carpeta `proyecto`. Si el proyecto está en otra ubicación, adaptar este comando:

```powershell
cd 'C:\Users\rzuni\Desktop\09 - EFT\EFT_S9_RaulZuniga\proyecto'
docker version
docker compose version
Test-Path ./docker-compose.yml
```

Resultado esperado: Docker muestra información de cliente y servidor, Compose muestra su versión y `Test-Path` devuelve `True`. Para esta modalidad no es necesario instalar Java ni Maven en Windows: la compilación ocurre dentro de Docker.

## 2. Iniciar el sistema

```powershell
docker compose config --quiet
docker compose up -d --build
docker compose ps -a
```

El primer comando debe terminar sin errores. La primera construcción puede tardar varios minutos. El arranque espera la disponibilidad de las dependencias e incluye los trabajos Batch de carga de datos.

Repetir `docker compose ps -a` hasta que los servicios permanentes aparezcan como `Up ... (healthy)`. El contenedor `batch-init` debe aparecer como `Exited (0)`: es una tarea que termina después de cargar los datos, no un servidor permanente.

| Componente | Puerto del equipo | Función |
| --- | --- | --- |
| PostgreSQL | 5433 | Datos bancarios y registro de eventos |
| Kafka | 29092 | Comunicación mediante eventos |
| Config Server | 8888 | Configuración centralizada |
| Eureka Server | 8761 | Registro y descubrimiento de servicios |
| Core Banking | 8081 | Consultas bancarias y procesamiento de retiros |
| BFF Web | 8082 | Consultas del canal Web |
| BFF Mobile | 8083 | Consultas del canal Mobile |
| BFF ATM | 8084 | Saldo y solicitudes de retiro |
| Account Service | 8085 | API de cuentas de demostración |
| Payment Service | 8086 | API de pagos de demostración y publicación de eventos |
| Customer Service | 8087 | API de clientes de demostración |
| Batch Init | Sin puerto fijo | Ejecuta tres trabajos de carga |

Captura sugerida: estado de todos los contenedores. No ejecutar `down -v` para resolver un arranque normal: elimina la base de datos del proyecto.

## 3. Comprobar la salud HTTP

```powershell
foreach ($puerto in @(8888,8761,8081,8082,8083,8084,8085,8086,8087)) {
    try {
        $salud = Invoke-RestMethod "http://localhost:$puerto/actuator/health"
        Write-Output "Puerto $puerto : $($salud.status)"
    } catch {
        Write-Output "Puerto $puerto : ERROR $($_.Exception.Message)"
    }
}
```

Resultado esperado: todos muestran `UP`. La salud del proceso no demuestra por sí sola que una consulta entre servicios funcione; por eso se realizan las pruebas siguientes.

## 4. Verificar Config Server

Los archivos de `config-repo` se crean como parte del proyecto. Config Server los lee; no los genera. `application.yml` aporta propiedades comunes y el archivo con el nombre del servicio aporta propiedades específicas.

```powershell
$configuracion = Invoke-RestMethod 'http://localhost:8888/core-banking-service/default'
$configuracion | ConvertTo-Json -Depth 10
Invoke-RestMethod 'http://localhost:8081/api/config/origin'
```

Resultado esperado: la primera respuesta contiene `name`, `profiles` y `propertySources`, con la configuración del Core. La segunda muestra `configOrigin` indicando que la configuración fue cargada desde Spring Cloud Config Server.

También se pueden consultar `/bff-web-service/default`, `/bff-mobile-service/default`, `/bff-atm-service/default`, `/account-service/default`, `/payment-service/default` y `/customer-service/default` en el puerto 8888.

Captura sugerida: respuesta del origen de configuración y uno de los documentos de configuración. Evitar mostrar claves secretas en capturas compartidas.

## 5. Verificar Eureka

1. Abrir `http://localhost:8761` en el navegador.
2. Revisar las instancias registradas: Core, los tres BFF, Account, Payment y Customer.
3. Confirmar que sus estados son `UP`.

Eureka permite localizar servicios por nombre. Puede haber un breve retraso entre el inicio de un contenedor y la actualización del registro de Eureka o de sus clientes. Si las primeras consultas fallan, esperar y repetir un GET.

Captura sugerida: tabla de instancias y estados del panel de Eureka.

## 6. Verificar Batch y PostgreSQL

```powershell
docker compose logs --no-color batch-init
docker exec banco-xyz-postgres pg_isready -U banco_user -d banco_xyz
docker exec banco-xyz-postgres psql -U banco_user -d banco_xyz -c "SELECT job_name, status FROM batch_job_instance JOIN batch_job_execution USING (job_instance_id) ORDER BY job_execution_id DESC LIMIT 10;"
docker exec banco-xyz-postgres psql -U banco_user -d banco_xyz -c "SELECT cuenta_id, nombre, tipo, saldo_final FROM intereses_mensuales ORDER BY cuenta_id LIMIT 10;"
```

Resultado esperado:

- `dailyTransactionsJob COMPLETED`.
- `monthlyInterestJob COMPLETED`.
- `annualStatementsJob COMPLETED`.
- `BATCH INIT COMPLETED`.
- PostgreSQL informa `accepting connections` y las consultas muestran registros.

Para las pruebas del Core y de los BFF se usará la cuenta `137`, verificada en los datos del proyecto:

```powershell
$cuenta = 137
Invoke-RestMethod "http://localhost:8081/api/core/accounts/$cuenta"
```

Debe devolver los datos de la cuenta. Si no existe en una carga distinta, seleccionar un `cuenta_id` de la consulta SQL anterior y cambiar `$cuenta`. No usar automáticamente `1001`: existe en la API de demostración de Account Service, pero no en los datos actuales del Core.

Capturas sugeridas: trabajos `COMPLETED` y consulta de cuentas en PostgreSQL.

## 7. Obtener los tokens de cada canal

Ejecutar en la misma terminal:

```powershell
$loginWeb = Invoke-RestMethod -Method Post -Uri 'http://localhost:8082/auth/login' -ContentType 'application/json' -Body '{"username":"webuser","password":"web123"}'
$loginMobile = Invoke-RestMethod -Method Post -Uri 'http://localhost:8083/auth/login' -ContentType 'application/json' -Body '{"username":"mobileuser","password":"mobile123"}'
$loginAtm = Invoke-RestMethod -Method Post -Uri 'http://localhost:8084/auth/login' -ContentType 'application/json' -Body '{"username":"atmuser","password":"atm123"}'
$cabeceraWeb = @{ Authorization = "Bearer $($loginWeb.accessToken)" }
$cabeceraMobile = @{ Authorization = "Bearer $($loginMobile.accessToken)" }
$cabeceraAtm = @{ Authorization = "Bearer $($loginAtm.accessToken)" }
```

Cada respuesta debe contener `accessToken`, `tokenType` y `role`. Cada BFF exige el rol de su canal. Reutilizar el token hasta que expire; si una petición devuelve 401 por expiración, ejecutar nuevamente este paso.

En Postman:

1. Crear una petición POST a la URL de login del canal.
2. Seleccionar **Body → raw → JSON** y pegar el objeto de usuario y contraseña del comando correspondiente.
3. Enviar y copiar únicamente `accessToken`.
4. En las peticiones de negocio seleccionar **Authorization → Bearer Token** y pegar ese valor, sin agregar otra vez la palabra `Bearer`.
5. Para peticiones GET usar **Body → none**. Para POST usar **raw → JSON**.

No hay una página de login requerida para estas pruebas: el login es un endpoint HTTP. Los usuarios anteriores son los valores predeterminados del entorno de laboratorio.

## 8. Probar Core Banking y los tres BFF

Core Banking expone consultas internas sin el token de los BFF en esta configuración:

```powershell
Invoke-RestMethod "http://localhost:8081/api/core/accounts/$cuenta"
Invoke-RestMethod "http://localhost:8081/api/core/accounts/$cuenta/balance"
Invoke-RestMethod "http://localhost:8081/api/core/accounts/$cuenta/annual-statement"
Invoke-RestMethod "http://localhost:8081/api/core/accounts/$cuenta/movements/latest"
```

Consultar después las rutas de los BFF:

```powershell
Invoke-RestMethod "http://localhost:8082/api/bff/web/accounts/$cuenta" -Headers $cabeceraWeb
Invoke-RestMethod "http://localhost:8082/api/bff/web/accounts/$cuenta/annual-statement" -Headers $cabeceraWeb
Invoke-RestMethod "http://localhost:8083/api/bff/mobile/accounts/$cuenta" -Headers $cabeceraMobile
Invoke-RestMethod "http://localhost:8083/api/bff/mobile/accounts/$cuenta/transactions" -Headers $cabeceraMobile
Invoke-RestMethod "http://localhost:8084/api/bff/atm/accounts/$cuenta/balance" -Headers $cabeceraAtm
```

Resultado esperado: HTTP 200 con datos JSON. Web devuelve detalles e interés calculado, Mobile devuelve una vista resumida y movimientos, ATM devuelve el saldo. Una lista de movimientos vacía puede ser válida si la cuenta no tiene movimientos anuales.

En Postman copiar las mismas URL sustituyendo `$cuenta` por `137`, seleccionar GET y usar el token de cada canal.

Pruebas de seguridad en Postman:

- GET Web con **Authorization → No Auth**: esperar 401.
- GET Mobile con el token Web: esperar 403 por rol incorrecto.
- Restaurar el token correcto: esperar 200.

Capturas sugeridas: un GET exitoso por canal y las respuestas 401 y 403.

## 9. Probar Account Service y Customer Service

Estos servicios aceptan un JWT válido; en la configuración Docker comparten la clave de firma con los BFF. Para estas pruebas se puede reutilizar el token Web. Sus datos de demostración son independientes de los datos del Core.

```powershell
Invoke-RestMethod 'http://localhost:8085/api/accounts' -Headers $cabeceraWeb
Invoke-RestMethod 'http://localhost:8085/api/accounts/1001' -Headers $cabeceraWeb
Invoke-RestMethod 'http://localhost:8087/api/customers' -Headers $cabeceraWeb
Invoke-RestMethod 'http://localhost:8087/api/customers/CUST-001' -Headers $cabeceraWeb
```

Resultado esperado: HTTP 200, cuenta `1001` asociada a `CUST-001` y cliente Maria Soto.

Probar también los POST:

```powershell
Invoke-RestMethod -Method Post -Uri 'http://localhost:8085/api/accounts' -Headers $cabeceraWeb -ContentType 'application/json' -Body '{"accountId":"3001","customerId":"CUST-003","type":"SAVINGS"}'
Invoke-RestMethod -Method Post -Uri 'http://localhost:8087/api/customers' -Headers $cabeceraWeb -ContentType 'application/json' -Body '{"customerId":"CUST-003","fullName":"Cliente de prueba","email":"prueba@example.com","segment":"STANDARD"}'
```

Resultado esperado: HTTP 200 con los objetos recibidos o construidos. Estos POST son demostrativos: no agregan los objetos a las listas fijas ni los guardan en PostgreSQL. Un GET posterior no confirma su persistencia. Un identificador desconocido puede devolver HTTP 200 con campos `NOT_FOUND` o `UNKNOWN`, según el servicio.

## 10. Probar Payment Service

```powershell
$transferencia = Invoke-RestMethod -Method Post -Uri 'http://localhost:8086/api/payments/transfer' -Headers $cabeceraWeb -ContentType 'application/json' -Body '{"sourceAccountId":"1001","targetAccountId":"2001","amount":1000}'
$transferencia | ConvertTo-Json
$deposito = Invoke-RestMethod -Method Post -Uri 'http://localhost:8086/api/payments/deposit' -Headers $cabeceraWeb -ContentType 'application/json' -Body '{"accountId":"1001","amount":500}'
$deposito | ConvertTo-Json
```

Resultado esperado: HTTP 200 con `transactionId`, monto y `status: COMPLETED`. Estas operaciones construyen una respuesta y envían un mensaje a `bank.transactions.completed`; no implementan una transferencia de saldo en las tablas del Core. La respuesta por sí sola tampoco prueba la entrega a Kafka: verificar los mensajes:

```powershell
docker exec banco-xyz-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic bank.transactions.completed --from-beginning --timeout-ms 10000
```

Buscar los `transactionId` de las dos respuestas. El consumidor puede mostrar mensajes de pruebas anteriores. Al agotarse el tiempo sin nuevos mensajes puede informar un timeout; eso no invalida los eventos ya mostrados.

## 11. Verificar Kafka y un retiro ATM completo

### 11.1. Listar topics

```powershell
docker exec -it banco-xyz-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
docker exec banco-xyz-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic bank.withdrawal.requested
```

Esperar los topics `bank.withdrawal.requested`, `bank.withdrawal.completed` y `bank.withdrawal.rejected`. El comando `--describe` muestra particiones y líderes. Kafka se consulta en el puerto 9092 porque el comando se ejecuta dentro del contenedor; desde el equipo se expone 29092.

### 11.2. Registrar el saldo y enviar una solicitud

Esta prueba modifica el saldo. Cada POST genera un retiro distinto; no repetirlo para consultar el resultado.

```powershell
$saldoAntes = Invoke-RestMethod "http://localhost:8084/api/bff/atm/accounts/$cuenta/balance" -Headers $cabeceraAtm
$saldoAntes | ConvertTo-Json
$montoRetiro = 1000
if ([decimal]$saldoAntes.balance -lt $montoRetiro) { throw 'Saldo insuficiente: elegir otra cuenta con saldo suficiente antes de continuar.' }
$cuerpoRetiro = @{ amount = $montoRetiro } | ConvertTo-Json -Compress
$respuestaRetiro = Invoke-WebRequest -UseBasicParsing -Method Post -Uri "http://localhost:8084/api/bff/atm/accounts/$cuenta/withdraw" -Headers $cabeceraAtm -ContentType 'application/json' -Body $cuerpoRetiro
$respuestaRetiro.StatusCode
$retiro = $respuestaRetiro.Content | ConvertFrom-Json
$retiro | ConvertTo-Json
```

Esperar HTTP **202**, un `transactionId` y `status: PENDING`. Esto indica recepción de la solicitud; verificar el resultado final en el siguiente paso.

En Postman: POST `http://localhost:8084/api/bff/atm/accounts/137/withdraw`, token ATM y Body JSON `{"amount":1000}`. Guardar el `transactionId` devuelto.

### 11.3. Consultar el resultado final

```powershell
$estadoRetiro = $null
for ($intento = 1; $intento -le 15; $intento++) {
    try {
        $estadoRetiro = Invoke-RestMethod "http://localhost:8084/api/bff/atm/accounts/transactions/$($retiro.transactionId)" -Headers $cabeceraAtm
        if ($estadoRetiro.status -in @('COMPLETED','REJECTED')) { break }
    } catch {
        Write-Output "Intento $intento : procesamiento o consulta aún no disponible"
    }
    Start-Sleep -Seconds 2
}
if ($null -eq $estadoRetiro -or $estadoRetiro.status -notin @('COMPLETED','REJECTED')) { throw 'No se confirmó el resultado: revisar los registros antes de enviar otro retiro.' }
$estadoRetiro | ConvertTo-Json
$saldoDespues = Invoke-RestMethod "http://localhost:8084/api/bff/atm/accounts/$cuenta/balance" -Headers $cabeceraAtm
$saldoDespues | ConvertTo-Json
Write-Output "Diferencia de saldo: $([decimal]$saldoAntes.balance - [decimal]$saldoDespues.balance)"
```

Esperar `COMPLETED` y una diferencia de saldo igual a 1000 si no hubo operaciones concurrentes. Si aparece `REJECTED`, leer `rejectionReason`.

En Postman: GET `http://localhost:8084/api/bff/atm/accounts/transactions/IDENTIFICADOR`, reemplazando `IDENTIFICADOR` por el UUID de la respuesta y usando el token ATM.

### 11.4. Correlacionar registros y datos

```powershell
docker compose logs --no-color --since=10m bff-atm-service core-banking-service | Select-String "$($retiro.transactionId)"
docker exec banco-xyz-postgres psql -U banco_user -d banco_xyz -c "SELECT transaction_id, account_id, amount, status, final_balance, rejection_reason FROM withdrawal_transactions WHERE transaction_id='$($retiro.transactionId)';"
docker exec banco-xyz-postgres psql -U banco_user -d banco_xyz -c "SELECT event_type, transaction_id, account_id, amount FROM banking_events WHERE transaction_id='$($retiro.transactionId)' ORDER BY occurred_at;"
docker exec banco-xyz-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group core-banking-withdrawal-group
```

Esperar registros de productor y consumidor con el mismo identificador, una transacción `COMPLETED` y eventos `WithdrawalRequested` y `WithdrawalCompleted`. El grupo consumidor debe mostrar las particiones; una vez procesados los mensajes, `LAG` debería llegar a 0.

Para mostrar el mensaje almacenado en Kafka:

```powershell
docker exec banco-xyz-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic bank.withdrawal.requested --from-beginning --timeout-ms 10000
```

Localizar el mismo `transactionId`; pueden aparecer retiros anteriores. También se puede consultar `bank.withdrawal.completed` con el mismo comando cambiando el topic.

Capturas sugeridas: topics, POST con 202, estado final, saldo antes/después y registros del mismo `transactionId`. No mostrar el token completo.

## 12. Verificar Resilience4j y el fallback

### 12.1. Evidencia de configuración y código

Abrir `config-repo/bff-web-service.yml` y capturar el bloque:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      coreWeb:
        sliding-window-size: 5
        failure-rate-threshold: 50
        wait-duration-in-open-state: 5s
        permitted-number-of-calls-in-half-open-state: 2
```

Abrir `bff-web-service/src/main/java/cl/duoc/bancoxyz/web/WebBankingClient.java` y capturar `breakers.create("coreWeb").run(...)` junto con `unavailable()`. Este fallback responde 503 cuando falla la consulta o el circuito rechaza la llamada. Los clientes Mobile y ATM utilizan `coreMobile` y `coreAtm` respectivamente.

La ventana y el porcentaje controlan el cálculo de fallos. El archivo no declara `minimum-number-of-calls`; no asumir que cinco peticiones bastan para observar la apertura. Además, una respuesta 503 demuestra el fallback, pero por sí sola no demuestra el estado OPEN del circuito.

### 12.2. Probar una caída y restaurar el Core

Realizar esta prueba después de terminar los retiros. Mientras el Core está detenido, otros usuarios tampoco podrán consultar sus datos. Preparar Postman con el GET Web de una cuenta existente y token Web vigente.

1. Enviar el GET Web con el Core disponible: esperar 200 y capturar.
2. En la terminal ejecutar:

```powershell
docker compose stop core-banking-service
```

3. Enviar el mismo GET Web: esperar 503 y capturar. Se pueden probar también los GET de Mobile y ATM con sus tokens.
4. Restaurar inmediatamente el servicio:

```powershell
docker compose start core-banking-service
docker compose ps -a
```

5. Esperar a que vuelva a `healthy`. Esperar además la actualización de Eureka y la comprobación de recuperación del circuito; repetir únicamente GET hasta obtener 200. No recrear ni ejecutar Batch para esta prueba.
6. Capturar la respuesta 200 recuperada y comprobar que los demás contenedores siguen saludables.

Esta secuencia demuestra la respuesta normal, el fallback ante una caída y la recuperación. El proyecto no expone métricas del estado del circuito en la configuración actual, por lo que no presentar estas capturas como prueba directa de OPEN/HALF_OPEN/CLOSED.

## 13. Diagnóstico de errores frecuentes

| Síntoma | Qué revisar |
| --- | --- |
| Docker no conecta al motor | Abrir Docker Desktop y comprobar `docker version` |
| Puerto ocupado | Revisar `docker compose ps -a` y las otras aplicaciones que usan el puerto |
| Compilación Maven falla | Revisar el primer error de construcción. El Dockerfile usa el wrapper real de `core-banking-service`; las referencias `XSym` de la raíz no son scripts ejecutables válidos |
| 401 | Token ausente, inválido o vencido; solicitar uno nuevo |
| 403 en un BFF | Token de otro canal; usar el rol Web, Mobile o ATM correspondiente |
| 503 en un GET de BFF | Core detenido, descubrimiento pendiente, timeout o error del Core; también ocurre para cuentas inexistentes porque el fallback transforma los errores |
| 404 en Core | Cuenta o transacción no encontrada; verificar el identificador en PostgreSQL |
| Retiro devuelve 202 pero no cambia el saldo | Consultar el estado final y revisar productor, consumidor y Kafka; 202 no confirma que el retiro terminó |
| `batch-init` aparece detenido | `Exited (0)` es correcto; otro código indica fallo de carga |
| Consumer de consola termina con timeout | Si ya mostró eventos, el timeout al finalizar la espera no implica pérdida de esos eventos |

Comandos generales de diagnóstico:

```powershell
docker compose ps -a
docker compose logs --tail=100 config-server eureka-server
docker compose logs --tail=100 core-banking-service bff-web-service bff-mobile-service bff-atm-service
docker compose logs --tail=100 kafka postgres batch-init
```

Si se modifican los archivos de configuración, reconstruir y recrear los contenedores afectados: el Dockerfile copia `config-repo` dentro de las imágenes. No asumir que un cambio de YAML en Windows se refleja automáticamente en los servicios que ya están ejecutándose.

## 14. Detener y volver a iniciar

Para una pausa conservando los contenedores y datos:

```powershell
docker compose stop
docker compose start
```

Para retirar los contenedores conservando el volumen PostgreSQL:

```powershell
docker compose down
```

Para crear e iniciar otra vez:

```powershell
docker compose up -d --build
```

Una nueva creación de `batch-init` ejecuta nuevamente la carga Batch y puede recalcular los saldos a partir de los archivos de entrada. Para comprobar un retiro ya realizado, consultar sus resultados antes de recrear el sistema.

Solo si se desea reiniciar completamente el laboratorio y borrar sus datos:

```powershell
docker compose down -v --remove-orphans
docker compose up -d --build
```

El comando con `-v` elimina el volumen de PostgreSQL, incluidos retiros, eventos e historial Batch. Kafka no tiene un volumen persistente definido en este Compose: sus mensajes pueden perderse al eliminar y recrear su contenedor.

## 15. Lista final de evidencias

- Contenedores saludables y `batch-init` terminado con código 0.
- Salud `UP` de los nueve servicios HTTP.
- Config Server entregando propiedades y Core mostrando su origen.
- Eureka con los servicios registrados como `UP`.
- Tres trabajos Batch `COMPLETED` y cuentas cargadas en PostgreSQL.
- GET exitoso de Web, Mobile y ATM con sus tokens.
- Seguridad: 401 sin token y 403 con token de otro canal.
- Account y Customer devolviendo sus datos de demostración.
- Payment devolviendo operaciones y mensajes visibles en `bank.transactions.completed`.
- Topics Kafka y retiro ATM: 202, identificador correlacionado, resultado `COMPLETED` y cambio de saldo.
- Configuración y uso del circuito `coreWeb`, fallback 503 ante caída y recuperación a 200.

Conservar en las capturas el método, la URL y el código HTTP. Usar el mismo `transactionId` para relacionar la petición de retiro, los registros y la base de datos. Los saldos y UUID varían entre ejecuciones.
