# Gestión de Perfiles — Python / FastAPI

Microservicio que gestiona los perfiles extendidos de los empleados (foto, biografía, teléfono, dirección, redes sociales). Se utiliza MongoDB porque los perfiles tienen un esquema flexible que puede variar según las necesidades de cada empleado.

## Stack Tecnológico

| Característica | Detalle |
|---|---|
| Lenguaje | Python 3.12 |
| Framework | FastAPI 0.111 |
| Puerto | 8083 |
| Base de Datos | MongoDB 7 (colección: `perfiles`) |
| Message Broker | RabbitMQ 3.13 (via aio-pika) |
| Documentación | Swagger UI (integrada en FastAPI) |
| Testing | pytest, pytest-asyncio, httpx, mongomock |

## Endpoints

| Método | Path | Descripción | Auth requerida |
|---|---|---|---|
| `GET` | `/health` | Health check del servicio | No |
| `POST` | `/profiles` | Crear perfil vacío (uso interno desde eventos) | No |
| `GET` | `/profiles/{empleado_id}` | Obtener perfil por empleadoId | X-Empleado-Id |
| `PUT` | `/profiles/{empleado_id}` | Actualizar perfil (solo el propio empleado) | X-Empleado-Id |
| `GET` | `/metrics` | Métricas en formato Prometheus | No |

> **Nota:** La validación de identidad se realiza mediante el header `X-Empleado-Id`. El API Gateway agrega este header automáticamente tras validar el JWT. Un empleado solo puede modificar su propio perfil.

## Esquema del Documento MongoDB

```json
{
  "empleadoId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "email": "juan.perez@empresa.com",
  "nombre": "Juan",
  "apellido": "Pérez",
  "foto": "https://...",
  "biografia": "Desarrollador senior con 10 años de experiencia",
  "telefono": "+525512345678",
  "direccion": {
    "calle": "Av. Principal 123",
    "ciudad": "Ciudad de México",
    "codigoPostal": "06600",
    "pais": "México"
  },
  "redesSociales": {
    "linkedin": "https://linkedin.com/in/juanperez",
    "github": "https://github.com/juanperez"
  },
  "archivado": false,
  "createdAt": "2024-01-15T10:00:00Z",
  "updatedAt": "2024-01-15T10:00:00Z"
}
```

## Eventos que Consume

| Cola | Evento | Acción |
|---|---|---|
| `perfiles.empleado.creado` | `empleado.creado` | Crear perfil vacío para el nuevo empleado |
| `perfiles.empleado.actualizado` | `empleado.actualizado` | Sincronizar nombre y apellido |
| `perfiles.empleado.eliminado` | `empleado.eliminado` | Archivar perfil (soft delete) |

Los eventos se consumen del exchange `empleados.exchange` de RabbitMQ.

## Variables de Entorno

| Variable | Descripción | Valor por Defecto |
|---|---|---|
| `MONGO_URL` | URL de conexión a MongoDB | `mongodb://perfiles_user:perfiles_pass@localhost:27017/perfiles_db?authSource=admin` |
| `RABBITMQ_URL` | URL de conexión a RabbitMQ | `amqp://guest:guest@localhost:5672` |
| `PORT` | Puerto del servidor | `8083` |

## Cómo Correr los Tests

```bash
# Todos los tests
pytest --cov=src

# Con reporte de cobertura HTML
pytest --cov=src --cov-report=html
```

> Los tests con Testcontainers requieren Docker.

## Health Check

```bash
curl http://localhost:8083/health
```

Respuesta esperada:
```json
{
  "status": "UP",
  "service": "gestion-perfiles",
  "version": "1.0.0",
  "timestamp": "2024-01-15T10:00:00Z",
  "dependencies": {
    "mongodb": "UP",
    "rabbitmq": "UP"
  }
}
```

## Swagger UI

```
http://localhost:8083/docs
```
