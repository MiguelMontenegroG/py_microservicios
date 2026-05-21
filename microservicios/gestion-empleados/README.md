# Gestión de Empleados

Microservicio responsable del CRUD completo de empleados y del ciclo de vida de sus estados. Es el **origen de todos los eventos del sistema** — cada creación, actualización o eliminación dispara eventos asíncronos que coordinan el resto de los microservicios.

**Lenguaje:** Java 21 · **Framework:** Spring Boot 3.1 · **Puerto:** 8082 · **BD:** PostgreSQL 16

---

## Endpoints

| Método | Path | Descripción |
|--------|------|-------------|
| `GET` | `/health` | Estado del servicio y dependencias |
| `GET` | `/employees` | Listar empleados paginado (`?page=0&size=10`) |
| `POST` | `/employees` | Crear empleado — dispara evento `empleado.creado` |
| `GET` | `/employees/{id}` | Obtener empleado por ID |
| `PUT` | `/employees/{id}` | Actualizar empleado — dispara evento `empleado.actualizado` |
| `DELETE` | `/employees/{id}` | Soft delete → estado RETIRADO — dispara evento `empleado.eliminado` |
| `GET` | `/employees/{id}/status` | Obtener solo el estado actual |
| `PUT` | `/employees/{id}/status` | Cambiar estado manualmente |
| `GET` | `/swagger-ui.html` | Documentación interactiva |
| `GET` | `/actuator/prometheus` | Métricas Prometheus |

---

## Modelo de datos

```
Empleado {
  id            UUID (autogenerado)
  nombre        String (requerido)
  apellido      String (requerido)
  email         String (único, requerido)
  numeroEmpleado String (único, requerido)
  fechaIngreso  LocalDate (requerido)
  cargo         String
  area          String
  estado        ACTIVO | EN_VACACIONES | RETIRADO
  createdAt     LocalDateTime
  updatedAt     LocalDateTime
}
```

## Estados del empleado

```
ACTIVO ──► (vacaciones programadas) ──► EN_VACACIONES ──► (fin vacaciones) ──► ACTIVO
  │
  └──► (DELETE /employees/{id}) ──► RETIRADO (permanente)
```

---

## Eventos que publica

| Exchange | Routing Key | Cuándo | Consumido por |
|---|---|---|---|
| `empleados.exchange` | `empleado.creado` | `POST /employees` exitoso | autenticacion, gestion-perfiles, notificaciones |
| `empleados.exchange` | `empleado.actualizado` | `PUT /employees/{id}` exitoso | gestion-perfiles |
| `empleados.exchange` | `empleado.eliminado` | `DELETE /employees/{id}` | autenticacion, gestion-perfiles |

---

## Variables de entorno

| Variable | Descripción | Valor por defecto |
|---|---|---|
| `SPRING_DATASOURCE_URL` | URL JDBC de PostgreSQL | `jdbc:postgresql://localhost:5432/empleados_db` |
| `SPRING_DATASOURCE_USERNAME` | Usuario de BD | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | Contraseña de BD | `postgres` |
| `RABBITMQ_HOST` | Host de RabbitMQ | `localhost` |
| `RABBITMQ_PORT` | Puerto AMQP | `5672` |
| `RABBITMQ_USER` | Usuario RabbitMQ | `guest` |
| `RABBITMQ_PASS` | Contraseña RabbitMQ | `guest` |
| `SERVER_PORT` | Puerto del servicio | `8082` |
| `LOG_LEVEL` | Nivel de log | `INFO` |

---

## Tests

```bash
# Solo tests unitarios (sin Docker)
mvn test

# Tests unitarios + integración (requiere Docker para Testcontainers)
mvn verify
```

| Suite | Cantidad | Tipo |
|---|---|---|
| `EmpleadoServiceTest` | 17 | Unitarios (Mockito) |
| `EmpleadoControllerIntegrationTest` | 17 | Integración (Testcontainers) |
| **Total** | **34** | **0 fallos** |

---

## Levantar solo este servicio

```bash
docker-compose up -d db-empleados rabbitmq
docker-compose up -d --build gestion-empleados

# Verificar
curl http://localhost:8082/health
```