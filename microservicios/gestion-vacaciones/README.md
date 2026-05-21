# Gestión de Vacaciones

Microservicio responsable de programar y gestionar períodos de vacaciones. Al crear un período, publica un evento `vacaciones.programadas` que el servicio de autenticación consume para desactivar la cuenta del empleado.

**Lenguaje:** Go 1.22 · **Framework:** Gin · **Puerto:** 8084 · **BD:** PostgreSQL 16

---

## Endpoints

| Método | Path | Query params | Descripción |
|--------|------|-------------|-------------|
| `GET` | `/health` | — | Estado del servicio y dependencias |
| `GET` | `/metrics` | — | Métricas Prometheus |
| `POST` | `/vacations` | — | Programar período de vacaciones |
| `GET` | `/vacations` | `?empleadoId=uuid` | Listar vacaciones (filtro opcional por empleado) |
| `GET` | `/vacations/{id}` | — | Obtener un período específico |
| `DELETE` | `/vacations/{id}` | — | Cancelar período → estado CANCELADA |

---

## Modelo de datos

```go
Vacacion {
  ID          UUID
  EmpleadoID  UUID      (requerido)
  FechaInicio time.Time (requerido, formato YYYY-MM-DD)
  FechaFin    time.Time (requerido, formato YYYY-MM-DD)
  Estado      string    // PROGRAMADA | ACTIVA | COMPLETADA | CANCELADA
  CreatedAt   time.Time
}
```

## Request para crear vacaciones

```json
{
  "empleadoId": "uuid-del-empleado",
  "email": "ana@empresa.com",
  "nombre": "Ana García",
  "fechaInicio": "2025-08-01",
  "fechaFin": "2025-08-15"
}
```

> El `email` y `nombre` se incluyen en el request porque este servicio no llama a gestion-empleados por HTTP — los datos viajan en el evento publicado a RabbitMQ.

---

## Validaciones de negocio

- `fechaFin` debe ser posterior a `fechaInicio` → error `FECHA_INVALIDA`
- No se permiten períodos solapados para el mismo empleado → error `VACACIONES_SOLAPADAS` (409)
- Formato de fechas obligatorio: `YYYY-MM-DD` — cualquier otro formato es rechazado

---

## Evento que publica

**Exchange:** `vacaciones.exchange` · **Routing key:** `vacaciones.programadas`

```json
{
  "eventId": "uuid-v4",
  "eventType": "vacaciones.programadas",
  "timestamp": "2024-01-15T10:30:00Z",
  "source": "gestion-vacaciones",
  "version": "1.0",
  "payload": {
    "vacacionId": "uuid",
    "empleadoId": "uuid",
    "email": "ana@empresa.com",
    "nombre": "Ana García",
    "fechaInicio": "2025-08-01",
    "fechaFin": "2025-08-15"
  }
}
```

Consumido por: **autenticacion** (desactiva cuenta) y **notificaciones** (envía email de confirmación).

---

## Variables de entorno

| Variable | Descripción | Valor por defecto |
|---|---|---|
| `DB_HOST` | Host de PostgreSQL | `localhost` |
| `DB_PORT` | Puerto de PostgreSQL | `5432` |
| `DB_NAME` | Nombre de la base de datos | `vacaciones_db` |
| `DB_USER` | Usuario de BD | `vacaciones_user` |
| `DB_PASSWORD` | Contraseña de BD | `vacaciones_pass` |
| `RABBITMQ_URL` | URL AMQP | `amqp://guest:guest@localhost:5672` |
| `PORT` | Puerto del servicio | `8084` |

---

## Tests

```bash
# Sin Go instalado localmente:
docker run --rm -v "${PWD}:/app" -w /app golang:1.22-alpine \
  go test ./... -v -cover

# Con Go instalado:
go test ./... -cover
```

| Suite | Cantidad | Tipo |
|---|---|---|
| `vacaciones_service_test.go` | 14 | Unitarios (mocks con testify) |
| `vacaciones_handler_test.go` | 13 | Integración (httptest) |
| **Total** | **27** | **0 fallos** |

> Los mocks `MockRepository` y `MockPublisher` se definen en `vacaciones_service_test.go` y se reutilizan en `vacaciones_handler_test.go` (mismo paquete).

---

## Levantar solo este servicio

```bash
docker-compose up -d db-vacaciones rabbitmq
docker-compose up -d --build gestion-vacaciones

# Verificar
curl http://localhost:8084/health
```