# Gestión de Perfiles

Microservicio responsable de la información complementaria del empleado: foto, biografía, dirección, redes sociales. Usa MongoDB por la naturaleza flexible y opcional de sus campos. El perfil se crea automáticamente (vacío) al consumir el evento `empleado.creado`.

**Lenguaje:** Python 3.12 · **Framework:** FastAPI · **Puerto:** 8083 · **BD:** MongoDB 7

---

## Endpoints

| Método | Path | Header requerido | Descripción |
|--------|------|-----------------|-------------|
| `GET` | `/health` | — | Estado del servicio y dependencias |
| `POST` | `/profiles` | — | Crear perfil vacío (uso interno desde eventos) |
| `GET` | `/profiles/{empleadoId}` | `X-Empleado-Id` | Obtener perfil por empleadoId |
| `PUT` | `/profiles/{empleadoId}` | `X-Empleado-Id` | Actualizar perfil |
| `GET` | `/docs` | — | Swagger UI (FastAPI auto-docs) |
| `GET` | `/metrics` | — | Métricas Prometheus |

> El header `X-Empleado-Id` lo inyecta el API Gateway tras validar el JWT. El servicio verifica que el empleado solo pueda editar su propio perfil.

---

## Esquema del documento MongoDB

```json
{
  "empleadoId": "uuid-string",
  "email": "ana@empresa.com",
  "nombre": "Ana",
  "apellido": "García",
  "foto": null,
  "biografia": null,
  "telefono": null,
  "direccion": {
    "calle": null,
    "ciudad": null,
    "codigoPostal": null,
    "pais": null
  },
  "redesSociales": {
    "linkedin": null,
    "github": null
  },
  "archivado": false,
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z"
}
```

Todos los campos excepto `empleadoId` son opcionales. El campo `archivado` se activa al eliminar el empleado (no se borra el documento — auditoría).

---

## Eventos que consume

| Queue | Evento | Acción |
|---|---|---|
| `perfiles.empleado.creado` | `empleado.creado` | Crear documento vacío con empleadoId, email, nombre, apellido |
| `perfiles.empleado.actualizado` | `empleado.actualizado` | Sincronizar nombre y apellido si cambiaron |
| `perfiles.empleado.eliminado` | `empleado.eliminado` | Marcar `archivado=true` (el documento NO se elimina) |

Los consumers corren como tareas asyncio en background usando el patrón `lifespan` de FastAPI. Cada consumer tiene conexión RabbitMQ independiente con 10 reintentos automáticos.

---

## Variables de entorno

| Variable | Descripción | Valor por defecto |
|---|---|---|
| `MONGO_URL` | URL de conexión MongoDB | `mongodb://perfiles_user:perfiles_pass@localhost:27017/perfiles_db?authSource=admin` |
| `RABBITMQ_URL` | URL AMQP de RabbitMQ | `amqp://guest:guest@localhost:5672` |
| `PORT` | Puerto del servicio | `8083` |
| `SERVICE_NAME` | Nombre en logs | `gestion-perfiles` |

---

## Tests

```bash
# Desde la carpeta del microservicio
pip install -r requirements.txt
pytest --cov=src -v

# Con Docker (sin Python local)
docker run --rm -v "${PWD}:/app" -w /app python:3.12-alpine \
  sh -c "pip install -r requirements.txt -q && pytest --cov=src -q"
```

| Suite | Cantidad | Tipo |
|---|---|---|
| `test_perfil_service.py` | 9 | Unitarios (mongomock) |
| `test_perfil_router.py` | 9 | Integración (httpx + ASGITransport) |
| **Total** | **18** | **0 fallos** |

---

## Levantar solo este servicio

```bash
docker-compose up -d mongo-perfiles rabbitmq
docker-compose up -d --build gestion-perfiles

# Verificar
curl http://localhost:8083/health
```