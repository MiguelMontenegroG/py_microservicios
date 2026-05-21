# Notificaciones — Python / FastAPI

Microservicio exclusivamente consumidor. No tiene base de datos ni publica eventos. Se encarga de procesar eventos de RabbitMQ y enviar correos electrónicos mediante plantillas HTML renderizadas con Jinja2.

## Stack Tecnológico

| Característica | Detalle |
|---|---|
| Lenguaje | Python 3.12 |
| Framework | FastAPI 0.111 |
| Puerto | 8085 |
| Base de Datos | Ninguna |
| Message Broker | RabbitMQ 3.13 (via aio-pika) |
| Templates | Jinja2 3.1 |
| SMTP | aiosmtplib (usa MailHog en desarrollo) |
| Testing | pytest, pytest-asyncio, pytest-cov, httpx |

## Colas que Consume

| Cola | Evento | Plantilla Usada | Destinatario |
|---|---|---|---|
| `notif.cuenta.activada` | `cuenta.activada` | `bienvenida.html` | Email del empleado |
| `notif.cuenta.desactivada` | `cuenta.desactivada` | `desactivacion.html` | Email del empleado |
| `notif.vacaciones.programadas` | `vacaciones.programadas` | `vacaciones.html` | Email del empleado |
| `notif.cuenta.reset-solicitado` | `cuenta.reset-solicitado` | `recuperacion.html` | Email del empleado |

## Plantillas de Email

### bienvenida.html

Variables que recibe:
- `nombre` — Nombre del empleado
- `username` — Email del empleado (usuario de login)
- `passwordTemporal` — Contraseña generada automáticamente
- `url_acceso` — URL del sistema (http://localhost:8080)

### vacaciones.html

Variables que recibe:
- `nombre` — Nombre del empleado
- `fechaInicio` — Fecha de inicio del período vacacional
- `fechaFin` — Fecha de fin del período vacacional
- `dias_totales` — Número total de días de vacaciones

### desactivacion.html

Variables que recibe:
- `nombre` — Nombre del empleado
- `motivo` — Razón de la desactivación (VACACIONES u OFFBOARDING)
- `timestamp` — Fecha y hora de la desactivación

### recuperacion.html

Variables que recibe:
- `nombre` — Nombre del empleado
- `codigo` — Código de 6 dígitos para restablecer la contraseña
- `expira_minutos` — Tiempo de validez del código (por defecto 5 minutos)

## Cómo Verificar que Funciona

1. Crear un empleado nuevo (ver flujo de Onboarding en README raíz)
2. Revisar MailHog en http://localhost:8025
3. Debería aparecer un email de bienvenida con las credenciales

## Variables de Entorno

| Variable | Descripción | Valor por Defecto |
|---|---|---|
| `RABBITMQ_HOST` | Host de RabbitMQ | `localhost` |
| `RABBITMQ_PORT` | Puerto de RabbitMQ | `5672` |
| `RABBITMQ_USER` | Usuario de RabbitMQ | `guest` |
| `RABBITMQ_PASS` | Contraseña de RabbitMQ | `guest` |
| `SMTP_HOST` | Servidor SMTP | `mailhog` |
| `SMTP_PORT` | Puerto SMTP | `1025` |
| `SMTP_FROM` | Dirección remitente | `noreply@empresa.com` |
| `PORT` | Puerto del servidor HTTP | `8085` |

## Endpoints

| Método | Path | Descripción |
|---|---|---|
| `GET` | `/health` | Health check del servicio |
| `GET` | `/notifications/stats` | Estadísticas de emails enviados/errores |
| `GET` | `/metrics` | Métricas en formato Prometheus |

## Cómo Correr los Tests

```bash
# Todos los tests
pytest --cov=src

# Con reporte HTML
pytest --cov=src --cov-report=html
```
