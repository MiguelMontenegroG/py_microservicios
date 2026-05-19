# Gestión de Vacaciones — Go / Gin

Microservicio que gestiona las solicitudes de vacaciones de los empleados. Valida fechas, evita solapamientos y publica eventos cuando se programa un período vacacional.

## Stack Tecnológico

| Característica | Detalle |
|---|---|
| Lenguaje | Go 1.22 |
| Framework | Gin 1.10 |
| Puerto | 8084 |
| Base de Datos | PostgreSQL 16 (tabla: `vacaciones`) |
| Message Broker | RabbitMQ 3.13 (via amqp091-go) |
| Métricas | Prometheus (client_golang 1.19) |
| Logging | zerolog |
| Testing | testify, go-sqlmock |

## Endpoints

| Método | Path | Descripción | Auth requerida |
|---|---|---|---|
| `GET` | `/health` | Health check del servicio | No |
| `GET` | `/metrics` | Métricas en formato Prometheus | No |
| `POST` | `/vacations` | Programar nuevas vacaciones | Sí |
| `GET` | `/vacations` | Listar vacaciones (opcional `?empleadoId={uuid}`) | Sí |
| `GET` | `/vacations/{id}` | Obtener vacación por ID | Sí |
| `DELETE` | `/vacations/{id}` | Cancelar vacación programada | Sí |

## Validaciones de Negocio

- **fechaFin** debe ser posterior o igual a **fechaInicio**
- No se permiten períodos solapados para el mismo empleado
- Formato de fechas: `YYYY-MM-DD`
- No se puede cancelar una vacación ya completada
- No se puede cancelar una vacación que ya esté cancelada

## Estados de Vacación

| Estado | Descripción |
|---|---|
| `PROGRAMADA` | Vacación registrada, aún no iniciada |
| `ACTIVA` | El período vacacional está transcurriendo |
| `COMPLETADA` | El período vacacional ha finalizado |
| `CANCELADA` | Vacación cancelada manualmente |

## Evento que Publica

| Evento | Exchange | Routing Key | Payload |
|---|---|---|---|
| `vacaciones.programadas` | `vacaciones.exchange` | `vacaciones.programadas` | Ver payload completo abajo |

Payload del evento `vacaciones.programadas`:

```json
{
  "eventId": "uuid",
  "eventType": "vacaciones.programadas",
  "timestamp": "2024-01-15T10:00:00Z",
  "source": "gestion-vacaciones",
  "version": "1.0",
  "payload": {
    "vacacionId": "uuid",
    "empleadoId": "uuid",
    "email": "ana@empresa.com",
    "nombre": "Ana García",
    "fechaInicio": "2024-08-01",
    "fechaFin": "2024-08-15"
  }
}
```

## Variables de Entorno

| Variable | Descripción | Valor por Defecto |
|---|---|---|
| `DB_HOST` | Host de PostgreSQL | `localhost` |
| `DB_PORT` | Puerto de PostgreSQL | `5432` |
| `DB_NAME` | Nombre de la base de datos | `vacaciones_db` |
| `DB_USER` | Usuario de BD | `vacaciones_user` |
| `DB_PASSWORD` | Contraseña de BD | `vacaciones_pass` |
| `RABBITMQ_URL` | URL de conexión a RabbitMQ | `amqp://guest:guest@localhost:5672` |
| `PORT` | Puerto del servidor | `8084` |

## Cómo Correr los Tests

Sin Go instalado localmente:

```bash
docker run --rm -v "${PWD}:/app" -w /app golang:1.22-alpine go test ./... -v -cover
```

Con Go instalado localmente:

```bash
go test ./... -v -cover
```

## Health Check

```bash
curl http://localhost:8084/health
```

Respuesta esperada:
```json
{
  "status": "UP",
  "service": "gestion-vacaciones",
  "timestamp": "2024-01-15T10:00:00Z",
  "dependencies": {
    "database": "UP",
    "rabbitmq": "UP"
  }
}
```
