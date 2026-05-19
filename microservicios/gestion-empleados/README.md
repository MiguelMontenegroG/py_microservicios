# gestion-empleados — Java/Spring Boot

Microservicio responsable del registro y gestión del ciclo de vida de los empleados. Publica eventos cuando un empleado es creado, actualizado o eliminado, permitiendo que otros servicios reaccionen de forma asíncrona.

## Stack Tecnológico

| Característica | Detalle |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot 3.1.12 |
| Puerto | 8082 |
| Base de Datos | PostgreSQL 16 |
| Message Broker | RabbitMQ 3.13 |
| Documentación | springdoc-openapi 2.3 (Swagger UI) |
| Testing | JUnit 5, Testcontainers, JaCoCo (cobertura mínima 70%) |

## Endpoints

| Método | Path | Descripción | Auth requerida |
|---|---|---|---|
| `GET` | `/health` | Health check del servicio | No |
| `GET` | `/employees` | Listar empleados paginados (page, size) | Sí |
| `POST` | `/employees` | Crear empleado y publicar evento `empleado.creado` | Sí |
| `GET` | `/employees/{id}` | Obtener empleado por ID | Sí |
| `PUT` | `/employees/{id}` | Actualizar empleado y publicar evento | Sí |
| `DELETE` | `/employees/{id}` | Soft delete (estado RETIRADO) y publicar evento | Sí |
| `GET` | `/employees/{id}/status` | Obtener estado actual del empleado | Sí |
| `PUT` | `/employees/{id}/status` | Cambiar estado del empleado | Sí |
| `GET` | `/actuator/health` | Health check detallado (Spring Actuator) | No |
| `GET` | `/actuator/prometheus` | Métricas formato Prometheus | No |

## Estados del Empleado

| Estado | Descripción | Transiciones |
|---|---|---|
| `ACTIVO` | Empleado activo en la organización | → `EN_VACACIONES`, → `RETIRADO` |
| `EN_VACACIONES` | Empleado de vacaciones (cuenta desactivada temporalmente) | → `ACTIVO`, → `RETIRADO` |
| `RETIRADO` | Empleado dado de baja (soft delete, no se elimina de BD) | Estado final, no se puede cambiar |

## Eventos que Publica

| Evento | Routing Key | Cuándo se dispara | Quién lo consume |
|---|---|---|---|
| `empleado.creado` | `empleado.creado` | Alta de nuevo empleado | autenticación, gestión-perfiles, notificaciones |
| `empleado.actualizado` | `empleado.actualizado` | Modificación de datos del empleado | gestión-perfiles |
| `empleado.eliminado` | `empleado.eliminado` | Soft delete del empleado | autenticación, gestión-perfiles |

Todos los eventos se publican en el exchange `empleados.exchange` (tipo Topic) de RabbitMQ.

## Variables de Entorno

| Variable | Descripción | Valor por Defecto |
|---|---|---|
| `SERVER_PORT` | Puerto del servidor | `8082` |
| `SPRING_DATASOURCE_URL` | URL de conexión a PostgreSQL | `jdbc:postgresql://localhost:5432/empleados_db` |
| `SPRING_DATASOURCE_USERNAME` | Usuario de BD | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | Contraseña de BD | `postgres` |
| `RABBITMQ_HOST` | Host de RabbitMQ | `localhost` |
| `RABBITMQ_PORT` | Puerto de RabbitMQ | `5672` |
| `RABBITMQ_USER` | Usuario de RabbitMQ | `guest` |
| `RABBITMQ_PASS` | Contraseña de RabbitMQ | `guest` |
| `LOG_LEVEL` | Nivel de logging | `INFO` |

## Cómo Correr los Tests
