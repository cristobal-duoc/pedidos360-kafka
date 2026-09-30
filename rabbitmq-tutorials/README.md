# rabbitmq-tutorials · Guía 2.1.2 (Hello World con RabbitMQ)

Implementación de la guía **2.1.2 «Crear cola, productor y consumidor básicos»**:
RabbitMQ en Docker, una cola `hello`, un productor (`Sender`) y un consumidor
(`Receiver`) en Spring Boot, más el endpoint REST opcional del Paso 9.

Es un proyecto aparte, independiente de Pedidos360: no toca `backend/` ni `frontend/`.

## Qué es cada archivo (y a qué paso de la guía corresponde)

| Paso | Archivo | Qué hace |
|:--|:--|:--|
| 1 | `docker-compose.yml` | Levanta RabbitMQ 4.2 con la consola web |
| 2 | `pom.xml` | Proyecto de Spring Initializr con Spring for RabbitMQ (`spring-boot-starter-amqp`) y Spring Web |
| 3 | `src/main/resources/application.yml` | Dónde está RabbitMQ: `localhost:5672`, `guest/guest`, vhost `/` |
| 4 | `RabbitMQConfig.java` | Declara la cola `hello` (no durable) |
| 5 | `Sender.java` | Productor: `rabbitTemplate.convertAndSend("hello", ...)` |
| 6 | `Receiver.java` | Consumidor: `@RabbitListener(queues = "hello")` |
| 7 | `Application.java` | Arranca Spring Boot y muestra el menú interactivo |
| 9 | `MessageController.java` | `POST /api/messages` y `GET /api/messages/send?message=...` |

## Cómo correrlo

Todo desde esta carpeta, en una terminal (PowerShell o Git Bash):

```bash
cd rabbitmq-tutorials
```

**1. Levantar RabbitMQ** (Docker Desktop tiene que estar abierto)

```bash
docker compose up -d
```
```bash
docker compose ps
```

Espera a que `STATUS` diga `(healthy)`. Para comprobarlo de las tres formas de la guía:

```bash
docker logs rabbitmq
```
```bash
docker exec rabbitmq rabbitmq-diagnostics ping
```

Y en el navegador: http://localhost:15672 (usuario `guest`, contraseña `guest`).

**2. Compilar** (no necesitas Maven instalado: `mvnw` lo descarga solo)

```bash
./mvnw clean package
```

Genera `target/rabbitmq-tutorials-0.0.1-SNAPSHOT.jar`.

**3. Ejecutar**

```bash
java -jar target/rabbitmq-tutorials-0.0.1-SNAPSHOT.jar
```

En PowerShell, si los `✓` o las tildes se ven como `?`, corre antes `chcp 65001`.

**4. Probar**: en el menú, opción `1`, escribe un mensaje. Verás:

```
[✓] Mensaje enviado: '[14:30:45.123] Hola RabbitMQ desde Spring Boot!'
[14:30:45.234] [✓] Mensaje recibido: '[14:30:45.123] Hola RabbitMQ desde Spring Boot!'
```

**5. Por REST** (con la app corriendo, desde otra terminal Git Bash):

```bash
curl -X POST http://localhost:8080/api/messages -H "Content-Type: application/json" -d '{"message":"Hola desde REST API!"}'
```
```bash
curl "http://localhost:8080/api/messages/send?message=HelloWorld"
```

**6. Apagar**: opción `3` del menú, y luego `docker compose down`.

## Cómo revisar todo en RabbitMQ (http://localhost:15672)

### Overview
- **Queued messages**: cuántos mensajes están esperando en todas las colas.
- **Message rates**: gráfico de mensajes publicados (*Publish*) y entregados (*Deliver*) por segundo.
  Envía la opción `2` del menú con 20 mensajes y mira cómo sube.
- **Global counts**: conexiones, canales, exchanges, colas y consumidores totales.

### Connections
- Con la app corriendo aparece **una conexión** desde `172.x.x.x` (tu máquina vista desde Docker),
  usuario `guest`, estado `running`. Es la conexión de Spring Boot.
- Si cierras la app, la conexión desaparece.

### Channels
- Los canales AMQP que abrió esa conexión. Cada `@RabbitListener` usa el suyo,
  así que verás varios. Columna **Consumer count**: cuántos consumidores tiene cada uno.

### Exchanges
- Busca **`(AMQP default)`**: el exchange sin nombre (`""`). El `Sender` publica ahí,
  y ese exchange manda cada mensaje a la cola **cuyo nombre sea igual a la routing key** (`hello`).
- Por eso la guía dice que `convertAndSend("hello", ...)` «especifica la cola destino».

### Queues and Streams → `hello`
Es la sección más importante. En la lista:
- **Features**: vacío, porque la cola **no es durable** (`new Queue("hello", false)`).
  Una cola durable mostraría una **D**.
- **Messages → Ready**: mensajes esperando a ser consumidos.
- **Messages → Unacked**: entregados a un consumidor que todavía no confirmó (ack).
- **Consumers**: **2**. ¿Por qué 2 si hay un solo `Receiver`? Porque tiene **dos métodos
  `@RabbitListener`** sobre la misma cola (ver abajo).

Entra a la cola `hello` para ver:
- **Consumers**: la tabla con los 2 consumidores, su canal y *Ack required*.
- **Get messages**: saca mensajes de la cola para leerlos (útil para el experimento de abajo).
- **Publish message**: envía un mensaje a mano desde la consola, sin la app.
- **Purge Messages**: vacía la cola.
- **Delete**: borra la cola (Spring la vuelve a crear al arrancar).

### Experimento 1: ver mensajes esperando en la cola
Demuestra que RabbitMQ **guarda** los mensajes aunque nadie los esté leyendo.

1. Cierra la app (opción `3`). En **Queues**, `hello` queda con **Consumers: 0**.
2. En la cola `hello` → **Publish message** → *Payload*: `mensaje sin consumidor` → **Publish message**.
3. Vuelve a la lista: **Ready: 1**. El mensaje está en la cola, esperando.
4. Arranca la app otra vez: en la consola aparece `Mensaje recibido: 'mensaje sin consumidor'`
   y en la UI **Ready** vuelve a 0.

### Experimento 2: el reparto por turnos (round-robin)
El `Receiver` tiene dos métodos escuchando `hello`: `receiveMessage` y `receiveMessageAdvanced`.
RabbitMQ no le entrega cada mensaje a los dos: los **reparte por turnos**.

- Usa la opción `2` con 4 mensajes. En la consola verás que se alternan:
  - `receiveMessage`: `[17:07:38.049] [✓] Mensaje recibido: ...` (con hora adelante).
  - `receiveMessageAdvanced`: `[✓] Mensaje recibido: ...` seguido de `Content-Type: text/plain`.
- `Content-Type: text/plain` es porque se envió un `String`. `Timestamp: null` porque el
  `Sender` no pone esa propiedad del mensaje (la hora va dentro del texto).

### Experimento 3: la cola no durable
1. `docker compose restart rabbitmq` (con la app cerrada).
2. En **Queues**, `hello` ya no existe: al no ser durable, se borró con el reinicio.
3. Arranca la app: Spring la vuelve a declarar y aparece de nuevo.

## Dónde este proyecto se aparta de la guía, y por qué

La guía tiene cuatro detalles que no funcionan tal como están escritos:

1. **`Channel` en `Receiver.java`**: la guía lo importa desde `org.springframework.amqp.rabbit.core.Channel`,
   que no existe; con eso el proyecto no compila. El correcto es `com.rabbitmq.client.Channel`.
2. **El bloque `retry` en `application.yml`**: la guía lo pone bajo `spring.amqp.template`, que no es una
   propiedad de Spring Boot y se ignora sin avisar. Aquí va bajo `spring.rabbitmq.template`.
3. **Versión de Java**: la guía sugiere Java 11, pero Spring Boot 3 o superior exige Java 17 como mínimo.
   Se usa Java 21.
4. **Spring Boot 4.1.1**: es lo que ofrece hoy Spring Initializr (la guía pide «3.1.x o superior»).
   En Boot 4 la dependencia «Spring Web» se llama `spring-boot-starter-webmvc`.

Además, Docker Compose avisa que el atributo `version: '3.8'` es obsoleto. Es solo un aviso;
se dejó para respetar la guía.
