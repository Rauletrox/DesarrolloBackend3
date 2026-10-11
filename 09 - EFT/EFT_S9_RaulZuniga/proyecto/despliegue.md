# Despliegue de Banco XYZ en AWS EC2 con Ubuntu y WinSCP

## 1. Alcance y requisitos

Este documento describe cómo desplegar todo el Docker Compose del proyecto en una instancia **AWS EC2 t2.medium**, con **Ubuntu Server 24.04 LTS, arquitectura x86_64**, transfiriendo los archivos desde Windows mediante **WinSCP/SFTP**. Los comandos del servidor se ejecutan en Bash, dentro de una sesión SSH.

Se necesita una cuenta AWS, permisos para crear EC2, un par de claves SSH, WinSCP, Postman y la carpeta completa del proyecto con el Dockerfile corregido. No es necesario instalar Java ni Maven en Ubuntu: Docker los utiliza dentro de las imágenes.

La t2.medium dispone de **2 vCPU y 4 GiB de RAM**. El proyecto ejecuta múltiples JVM, Kafka y PostgreSQL; esta capacidad puede resultar ajustada y no garantiza que todos los servicios funcionen simultáneamente sin ajustes. Los créditos de CPU de T2 también influyen en la duración de la compilación. Revisar la modalidad de créditos y sus costos en AWS. [Características de T2](https://aws.amazon.com/ec2/instance-types/t2/).

La guía conserva la configuración de laboratorio del proyecto. Sus contraseñas predeterminadas y HTTP directo se destinan a pruebas restringidas a la IP del evaluador. Un despliegue público requiere gestionar secretos y configurar HTTPS antes de compartir el acceso.

## 2. Crear la instancia EC2

1. Entrar en AWS y seleccionar la región de trabajo.
2. Abrir **EC2 → Instancias → Lanzar instancia**.
3. Asignar un nombre, por ejemplo `banco-xyz-ubuntu`.
4. Elegir una AMI oficial de **Ubuntu Server 24.04 LTS de Canonical**, x86_64. No elegir ARM para este tipo de instancia.
5. Seleccionar **t2.medium**.
6. Crear o seleccionar un par de claves RSA y descargar la clave privada en formato `.pem`. Guardarla en Windows; no subirla al servidor ni incluirla en el proyecto.
7. Elegir una VPC y una subred pública con ruta hacia un Internet Gateway. Habilitar la asignación de IPv4 pública para el acceso desde el equipo.
8. Configurar un volumen EBS **gp3 de 30 GiB** como punto de partida para imágenes, compilación y datos; controlar el espacio libre después del build.
9. Crear el Security Group siguiendo el paso siguiente.
10. Lanzar la instancia y esperar el estado `Running` y las comprobaciones de estado aprobadas.
11. Anotar la IPv4 pública. Una IP pública asignada automáticamente puede cambiar tras detener e iniciar la instancia; actualizar Postman y WinSCP si sucede. Una Elastic IP permite mantener una dirección estable, con los costos correspondientes.

Captura sugerida: nombre de la instancia, AMI, tipo t2.medium y estado. No mostrar la clave privada.

## 3. Configurar el Security Group

En **EC2 → instancia → Seguridad → Security Group → Editar reglas de entrada**, crear estas reglas. Usar **Mi IP**, equivalente a la IP pública del equipo con máscara `/32`, como origen. Si el evaluador accede desde otra conexión, agregar su IP autorizada.

| Tipo / protocolo | Puerto | Uso |
| --- | --- | --- |
| SSH / TCP | 22 | SSH y transferencia SFTP mediante WinSCP |
| TCP personalizado | 8082–8084 | Login y consultas de los BFF Web, Mobile y ATM |
| TCP personalizado | 8085–8087 | Pruebas directas de Account, Payment y Customer |
| TCP personalizado | 8081 | Consultas directas al Core, si se necesitan evidencias |
| TCP personalizado | 8761 | Panel Eureka desde el navegador |
| TCP personalizado | 8888 | Consulta de Config Server, si se necesita evidencia remota |

Las reglas para 8081, 8761 y 8888 son opcionales si todas sus verificaciones se hacen por SSH. Para probar únicamente los canales basta con 22 y 8082–8084.

No abrir PostgreSQL 5433 ni Kafka 29092 hacia Internet para estas pruebas. Los contenedores se comunican por la red interna de Docker con `postgres:5432` y `kafka:9092`. Los puertos 9092 y 9093 tampoco requieren reglas de entrada EC2. Los puertos 80 y 443 solo se necesitan si posteriormente se configura un proxy o balanceador HTTP/HTTPS.

Mantener la salida de red necesaria para descargar imágenes y dependencias. No confiar exclusivamente en UFW para filtrar puertos publicados por Docker: su publicación puede eludir reglas UFW. Usar el Security Group para restringir el acceso externo. [Reglas de Security Groups de AWS](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/security-group-rules-reference.html), [consideraciones de Docker en Ubuntu](https://docs.docker.com/engine/install/ubuntu/).

Captura sugerida: reglas de entrada, puertos y origen autorizado.

## 4. Conectarse por SSH desde Windows

En PowerShell, sustituir la ruta de la clave y `IP_PUBLICA_EC2` por valores reales:

```powershell
ssh -i "C:\ruta\banco-xyz.pem" ubuntu@IP_PUBLICA_EC2
```

El usuario predeterminado de la AMI Ubuntu es `ubuntu`. Antes de aceptar la clave del servidor, verificar su huella por un canal confiable de AWS. Si SSH rechaza los permisos del `.pem`, restringir en Windows el acceso al archivo de clave al usuario que lo utiliza. [Conexión SSH a EC2](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/connect-linux-inst-ssh.html).

En la terminal Ubuntu:

```bash
cat /etc/os-release
uname -m
free -h
df -h /
mkdir -p /home/ubuntu/proyecto
```

Esperar Ubuntu, arquitectura `x86_64` y la carpeta de destino creada. Los siguientes bloques Bash se ejecutan en esta sesión SSH.

## 5. Subir el proyecto utilizando WinSCP

1. Abrir WinSCP y crear un **Nuevo sitio**.
2. Seleccionar protocolo **SFTP**, puerto **22**.
3. En **Nombre del servidor**, ingresar la IPv4 pública de EC2.
4. En **Nombre de usuario**, ingresar `ubuntu`.
5. Ir a **Avanzado → SSH → Autenticación** y seleccionar la clave privada descargada. Si WinSCP solicita convertir la clave `.pem` a `.ppk`, aceptar la conversión y guardar el archivo localmente.
6. Iniciar sesión y verificar la huella del servidor antes de aceptarla. [Guía oficial de WinSCP para EC2](https://winscp.net/eng/docs/guide_amazon_ec2).
7. En el panel local, abrir la carpeta `proyecto`; en el remoto, abrir `/home/ubuntu/proyecto`.
8. Habilitar la visualización y transferencia de archivos ocultos, porque se necesita `core-banking-service/.mvn`.
9. Transferir en **modo binario** todo el código necesario. Evitar subir `target`, `.git`, evidencias y archivos temporales. No aplicar máscaras que excluyan `.mvn`.
10. Esperar a que la cola termine sin errores y revisar que el contenido esté directamente en `/home/ubuntu/proyecto`, no en una carpeta adicional `proyecto/proyecto`.

Si se dispone de un ZIP, transferirlo mediante WinSCP, instalar `unzip` y revisar su contenido antes de extraer:

```bash
sudo apt update
sudo apt install -y unzip
unzip -l /home/ubuntu/proyecto.zip
```

Si el ZIP contiene la carpeta superior `proyecto/`, extraer en `/home/ubuntu`:

```bash
unzip /home/ubuntu/proyecto.zip -d /home/ubuntu
```

Si contiene directamente `pom.xml`, `docker-compose.yml` y los módulos, extraer en `/home/ubuntu/proyecto`. Adaptar el nombre del ZIP real y no sobrescribir una instalación existente sin revisar sus archivos.

## 6. Comprobar los archivos transferidos

```bash
cd /home/ubuntu/proyecto
ls -la
test -f docker-compose.yml && echo 'Compose encontrado'
test -f pom.xml && echo 'POM raíz encontrado'
test -f core-banking-service/.mvn/wrapper/maven-wrapper.properties && echo 'Configuración Maven encontrada'
head -n 1 core-banking-service/mvnw
head -n 6 docker/Dockerfile.service
```

El wrapper real debe comenzar con `#!/bin/sh`. El Dockerfile corregido debe usar:

```dockerfile
COPY core-banking-service/.mvn .mvn
COPY core-banking-service/mvnw ./mvnw
COPY pom.xml ./
```

Las referencias `XSym` de `mvnw` y `.mvn` en la raíz del paquete original no son el wrapper real. No sustituir las rutas corregidas del Dockerfile por esas referencias. El Dockerfile normaliza los finales de línea del wrapper y de `batch-init.sh` antes de ejecutarlos en Linux.

También deben estar presentes `config-repo`, `docker`, `shared-events`, `config-server`, `eureka-server`, `account-service`, `payment-service`, `customer-service`, `core-banking-service` y los tres módulos BFF.

Captura sugerida: transferencia completada en WinSCP y estructura de archivos en Ubuntu.

## 7. Instalar Docker Engine y Compose

Estos comandos corresponden a una instancia Ubuntu nueva. Si Docker ya está instalado, comprobar su versión y evitar mezclar paquetes de distintos proveedores. Se utiliza el repositorio oficial y el plugin Compose. [Instalación oficial de Docker en Ubuntu](https://docs.docker.com/engine/install/ubuntu/).

```bash
sudo apt update
sudo apt install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
```

Agregar el repositorio:

```bash
sudo tee /etc/apt/sources.list.d/docker.sources > /dev/null <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
sudo docker run --rm hello-world
sudo docker version
sudo docker compose version
```

Resultado esperado: el ejemplo `hello-world` confirma la instalación y Docker muestra información de cliente y servidor. Esta guía usa `sudo docker` para evitar depender de la pertenencia del usuario al grupo Docker.

## 8. Revisar recursos y compilar

```bash
cd /home/ubuntu/proyecto
free -h
df -h /
sudo docker compose config --quiet
sudo docker compose --parallel 1 build
```

Se construyen los servicios de forma secuencial para limitar la concurrencia durante la compilación en t2.medium. Esperar que finalice sin errores. El Dockerfile ejecuta `clean package -DskipTests`: construye los módulos, pero omite ejecutar las pruebas automatizadas. Las verificaciones funcionales siguientes son necesarias.

Si la compilación informa `Killed`, código 137 o se interrumpe por memoria, comprobar:

```bash
free -h
sudo journalctl -k --since '30 minutes ago' | grep -Ei 'out of memory|oom|killed process'
```

Si existe presión de memoria, se puede añadir swap como apoyo para el laboratorio. Este paso es opcional, crea un archivo de 4 GiB y solo debe ejecutarse si no existe `/swapfile` y hay espacio libre:

```bash
sudo swapon --show
ls -l /swapfile
df -h /
```

Si `ls` informa que `/swapfile` no existe:

```bash
sudo fallocate -l 4G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
free -h
```

Para habilitarlo después de reiniciar, agregar una sola entrada a `/etc/fstab`:

```bash
grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

Swap puede reducir fallos por memoria, pero es más lenta que RAM. Si siguen ocurriendo fallos al ejecutar todo el sistema, se requiere ajustar de forma medida los límites de memoria de las JVM o aumentar la capacidad de la instancia. No asumir que el tipo t2.medium garantiza recursos suficientes.

## 9. Iniciar y verificar los contenedores

```bash
sudo docker compose up -d --no-build
sudo docker compose ps -a
```

Repetir el comando de estado hasta observar todos los servicios permanentes `healthy`. `batch-init` debe terminar en `Exited (0)`. El arranque incluye dependencias, registro en Eureka y carga Batch; puede tardar varios minutos.

```bash
sudo docker compose logs --tail=100 batch-init
sudo docker stats --no-stream
```

Esperar los tres trabajos `dailyTransactionsJob`, `monthlyInterestJob` y `annualStatementsJob` en `COMPLETED`, además de `BATCH INIT COMPLETED`.

El Compose actual no define políticas de reinicio automáticas. Después de reiniciar Ubuntu, comprobar Docker y ejecutar `sudo docker compose up -d --no-build` si los contenedores no están activos. Revisar si Batch se ejecutó nuevamente antes de comparar saldos de pruebas anteriores.

## 10. Comprobar el sistema desde Ubuntu

```bash
for puerto in 8888 8761 8081 8082 8083 8084 8085 8086 8087; do
  echo "Comprobando puerto $puerto"
  curl --max-time 10 -fsS "http://localhost:$puerto/actuator/health"
  echo
done
curl -fsS http://localhost:8081/api/config/origin
curl -fsS http://localhost:8081/api/core/accounts/137
sudo docker exec banco-xyz-postgres pg_isready -U banco_user -d banco_xyz
sudo docker exec banco-xyz-postgres psql -U banco_user -d banco_xyz -c "SELECT cuenta_id, nombre, saldo_final FROM intereses_mensuales ORDER BY cuenta_id LIMIT 10;"
```

Esperar salud `UP`, origen de configuración centralizada, cuenta `137` disponible y PostgreSQL aceptando conexiones. Si esa cuenta no está en una carga diferente, elegir una cuenta existente de la consulta SQL y sustituir 137 en las pruebas.

Desde el navegador del equipo, abrir `http://IP_PUBLICA_EC2:8761` si se habilitó ese puerto. Los servicios deben registrarse como `UP`. Config Server se comprueba con `http://IP_PUBLICA_EC2:8888/core-banking-service/default`; no compartir capturas que revelen secretos.

## 11. Probar desde Postman

En Postman usar la IPv4 pública de EC2. `localhost` en el equipo Windows apunta a Windows, no a AWS. Crear una variable de entorno `EC2_HOST` con la IP real permite escribir `http://{{EC2_HOST}}:8082/...`.

### 11.1. Obtener tokens

Seleccionar POST, Authorization **No Auth** y Body **raw → JSON**:

| Canal | URL | JSON |
| --- | --- | --- |
| Web | `http://IP_PUBLICA_EC2:8082/auth/login` | `{"username":"webuser","password":"web123"}` |
| Mobile | `http://IP_PUBLICA_EC2:8083/auth/login` | `{"username":"mobileuser","password":"mobile123"}` |
| ATM | `http://IP_PUBLICA_EC2:8084/auth/login` | `{"username":"atmuser","password":"atm123"}` |

Copiar el `accessToken` de cada canal. No colocar la palabra `Bearer` dentro del campo Token de Postman. Si el token vence, solicitar uno nuevo.

### 11.2. Consultar las cuentas

Seleccionar GET, Body **none** y Authorization **Bearer Token** con el token correcto:

| Prueba | URL | Resultado esperado |
| --- | --- | --- |
| Web | `http://IP_PUBLICA_EC2:8082/api/bff/web/accounts/137` | 200 y detalle de cuenta |
| Mobile | `http://IP_PUBLICA_EC2:8083/api/bff/mobile/accounts/137` | 200 y resumen de cuenta |
| ATM | `http://IP_PUBLICA_EC2:8084/api/bff/atm/accounts/137/balance` | 200 y saldo |
| Account | `http://IP_PUBLICA_EC2:8085/api/accounts/1001` | 200 y cuenta de demostración; usar token Web |
| Customer | `http://IP_PUBLICA_EC2:8087/api/customers/CUST-001` | 200 y cliente; usar token Web |

La cuenta `1001` de Account Service no pertenece a la misma fuente de datos que las cuentas del Core. Para los BFF usar una cuenta existente en PostgreSQL, como 137.

### 11.3. Probar Payment

POST `http://IP_PUBLICA_EC2:8086/api/payments/transfer`, token Web y Body JSON:

```json
{"sourceAccountId":"1001","targetAccountId":"2001","amount":1000}
```

Esperar HTTP 200, `transactionId` y `status: COMPLETED`. Esta API es demostrativa: genera una respuesta y publica en `bank.transactions.completed`; no mueve el saldo de las tablas del Core. Para comprobar el mensaje en Ubuntu:

```bash
sudo docker exec -it banco-xyz-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic bank.transactions.completed --from-beginning
```

Buscar el mismo identificador y finalizar la consulta con Ctrl+C.

## 12. Evidenciar Kafka y retiro ATM

### 12.1. Comprobar topics en Ubuntu

```bash
sudo docker compose ps kafka
sudo docker exec -it banco-xyz-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

Esperar `bank.withdrawal.requested`, `bank.withdrawal.completed` y `bank.withdrawal.rejected`. El puerto 9092 es interno: se utiliza dentro del contenedor, sin abrirlo en el Security Group.

### 12.2. Solicitar y seguir un retiro

1. Consultar en Postman el saldo ATM de la cuenta 137 y guardar la respuesta inicial.
2. Comprobar que tiene al menos 1000 disponibles.
3. Enviar una sola vez POST `http://IP_PUBLICA_EC2:8084/api/bff/atm/accounts/137/withdraw`, token ATM y Body JSON:

```json
{"amount":1000}
```

4. Capturar HTTP **202 Accepted**, `transactionId` y `PENDING`. El 202 no confirma la finalización del retiro.
5. Consultar GET `http://IP_PUBLICA_EC2:8084/api/bff/atm/accounts/transactions/TRANSACTION_ID`, sustituyendo el marcador por el identificador recibido y usando token ATM.
6. Si aún no está disponible, esperar unos segundos y repetir solamente el GET. Esperar `COMPLETED` o `REJECTED` con su motivo.
7. Consultar de nuevo el saldo: para un retiro completado sin operaciones concurrentes debe disminuir en 1000. Cada nuevo POST produce otro retiro y vuelve a afectar el saldo.

En Ubuntu, sustituir el marcador por el UUID real:

```bash
sudo docker compose logs --since=10m bff-atm-service core-banking-service | grep 'TRANSACTION_ID'
sudo docker exec -it banco-xyz-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic bank.withdrawal.requested --from-beginning
```

Localizar el mismo identificador. La consola muestra también eventos anteriores y queda esperando; Ctrl+C detiene esa consulta sin detener Kafka. Repetir con `bank.withdrawal.completed` para el resultado exitoso o `bank.withdrawal.rejected` para rechazos.

Comprobar el grupo consumidor:

```bash
sudo docker exec banco-xyz-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group core-banking-withdrawal-group
```

Después de procesar los mensajes, `LAG` debería llegar a 0. Para comprobar el registro persistido:

```bash
sudo docker exec banco-xyz-postgres psql -U banco_user -d banco_xyz -c "SELECT transaction_id, account_id, amount, status, final_balance FROM withdrawal_transactions WHERE transaction_id='TRANSACTION_ID';"
```

Capturas sugeridas: topics, POST 202, evento con el mismo UUID, resultado final y cambio de saldo.

## 13. Evidenciar Resilience4j

1. Capturar el bloque `resilience4j.circuitbreaker.instances.coreWeb` de `config-repo/bff-web-service.yml` y el uso de `breakers.create("coreWeb").run(...)` en `WebBankingClient.java`.
2. Consultar una cuenta existente por el BFF Web y capturar HTTP 200.
3. Después de terminar las pruebas de retiro, detener únicamente el Core:

```bash
sudo docker compose stop core-banking-service
```

4. Repetir el GET Web y capturar HTTP 503: el fallback informa indisponibilidad.
5. Restaurar inmediatamente el Core:

```bash
sudo docker compose start core-banking-service
sudo docker compose ps -a
```

6. Esperar su salud, actualización de Eureka y recuperación del circuito. Repetir GET hasta obtener 200 y capturar.

Un 503 demuestra el comportamiento alternativo, pero por sí solo no demuestra que el circuito esté OPEN. La configuración actual no expone métricas de los estados del circuito. También convierte errores del Core, como una cuenta inexistente, en 503; por eso esta prueba debe usar una cuenta existente.

## 14. Diagnóstico

| Problema | Acción |
| --- | --- |
| WinSCP no conecta | Revisar IP pública, puerto 22, usuario ubuntu, clave y origen del Security Group |
| SSH `Permission denied (publickey)` | Revisar que la clave corresponde al par seleccionado al crear EC2 |
| Postman timeout | Revisar IP, puerto y Security Group; probar el mismo endpoint con curl desde Ubuntu |
| Curl local funciona y Postman no | Revisar acceso externo: IP pública, rutas de la subred, Security Group y reglas de red personalizadas |
| 401 | Solicitar un token vigente y revisar Bearer Token |
| 403 | Utilizar el token del canal correspondiente |
| 503 | Revisar Core, Eureka y existencia de la cuenta; puede ser el fallback |
| Error `No goals have been specified` | Comprobar que se subió el Dockerfile corregido y el wrapper real del módulo Core |
| Contenedor detenido con código 137 | Investigar memoria y posibles terminaciones OOM |
| `batch-init` Exited (0) | Correcto: finalizó la inicialización |
| Disco lleno | Revisar `df -h` y `sudo docker system df`; identificar archivos prescindibles antes de borrar |

```bash
sudo docker compose ps -a
sudo docker compose logs --tail=100
sudo docker stats --no-stream
free -h
df -h /
```

No ejecutar limpiezas de volúmenes como respuesta automática a un error: pueden borrar los datos utilizados en las evidencias.

## 15. Actualizar, detener y conservar datos

Para actualizar, transferir los archivos modificados por WinSCP al mismo directorio y ejecutar:

```bash
cd /home/ubuntu/proyecto
sudo docker compose --parallel 1 build
sudo docker compose up -d --no-build
sudo docker compose ps -a
```

El Dockerfile copia la configuración dentro de las imágenes; un YAML subido no actualiza automáticamente los procesos existentes. Revisar los registros de Batch después de una recreación, porque su ejecución puede volver a calcular saldos.

Para pausar conservando contenedores:

```bash
sudo docker compose stop
sudo docker compose start
```

Para retirar los contenedores conservando el volumen PostgreSQL:

```bash
sudo docker compose down
```

Kafka no tiene un volumen persistente en este Compose: sus mensajes pueden perderse al eliminar su contenedor. PostgreSQL sí utiliza un volumen. `docker compose down -v` elimina ese volumen y sus datos; no utilizarlo si se desea conservar retiros, eventos e historial Batch.

Después de terminar el laboratorio, detener la instancia EC2 si ya no se necesita. Revisar los cargos de almacenamiento y direcciones públicas; detener la instancia no elimina todos los costos. Antes de terminarla definitivamente, guardar las evidencias y los datos que se necesiten.

## 16. Evidencias de despliegue

- Instancia Ubuntu t2.medium en ejecución y comprobaciones de estado aprobadas.
- Security Group con accesos restringidos y puertos utilizados.
- Transferencia de archivos mediante WinSCP y carpeta del proyecto en Ubuntu.
- Versiones de Docker y Compose, y compilación finalizada sin errores.
- Contenedores saludables, trabajos Batch completados y datos en PostgreSQL.
- Config Server funcionando y servicios registrados en Eureka.
- Peticiones Postman hacia la IP pública de AWS con respuestas 200, 202 y resultado del retiro.
- Topics y eventos Kafka relacionados mediante el mismo `transactionId`.
- Resilience4j: configuración, fallback ante caída y recuperación.

La guía detallada de todas las rutas y pruebas está también en `instrucciones.md`. Sus comandos PowerShell deben adaptarse a Bash cuando se ejecutan en Ubuntu; los de este documento ya están escritos para el servidor Linux.
