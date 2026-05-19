# API Gateway — Node.js / Express

Punto de entrada único del sistema. Enruta las peticiones a los microservicios internos, valida la autenticación JWT, inyecta headers de identidad (`X-Empleado-Id`, `X-Rol`) y compone respuestas de múltiples servicios cuando es necesario (endpoint composite).

## Stack Tecnológico

| Característica | Detalle |
|---|---|
| Lenguaje | Node.js 20 |
| Framework | Express 4.19 |
| Puerto | 8080 |
| Documentación | swagger-ui-express + openapi.yaml |
| Métricas | prom-client 15.1 |
| Logging | winston |
| Testing | Jest 29, supertest, nock |

## Endpoints Completos

| Método | Path | Auth requerida | Servicio Destino |
|---|---|---|---|
| `GET` | `/health` | No | — (propio) |
| `GET` | `/metrics` | No | — (propio) |
| `GET` | `/api-docs` | No | — (propio) |
| `POST` | `/auth/login` | No | autenticacion:8081 |
| `POST` | `/auth/change-password` | JWT | autenticacion:8081 |
| `GET` | `/employees` | JWT | gestion-empleados:8082 |
| `POST` | `/employees` | JWT | gestion-empleados:8082 |
| `GET` | `/employees/:id` | JWT | **Composite** (gestion-empleados + gestion-perfiles) |
| `PUT` | `/employees/:id` | JWT | gestion-empleados:8082 |
| `DELETE` | `/employees/:id` | JWT | gestion-empleados:8082 |
| `GET` | `/profile` | JWT | gestion-perfiles:8083 |
| `PUT` | `/profile` | JWT | gestion-perfiles:8083 |
| `POST` | `/vacations` | JWT | gestion-vacaciones:8084 |
| `GET` | `/vacations` | JWT | gestion-vacaciones:8084 |
| `GET` | `/vacations/:id` | JWT | gestion-vacaciones:8084 |
| `DELETE` | `/vacations/:id` | JWT | gestion-vacaciones:8084 |

## Endpoint Composite `GET /employees/:id`

Combina datos del empleado (gestion-empleados) con su perfil extendido (gestion-perfiles) en una sola respuesta. Si la consulta al perfil falla, se retorna el empleado con `perfil: null`.

Respuesta típica:
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "nombre": "Juan",
    "apellido": "Pérez",
    "email": "juan@empresa.com",
    "perfil": {
      "empleadoId": "uuid",
      "foto": "...",
      "biografia": "...",
      "telefono": "...",
      "direccion": { ... },
      "redesSociales": { ... }
    }
  }
}
```

## Flujo de Autenticación

```
Cliente → Authorization: Bearer {token}
    → Gateway recibe el request
    → Llama POST autenticacion:8081/auth/validate con el token
    → Si es válido: agrega headers X-Empleado-Id y X-Rol al request
    → Reenvía al microservicio destino con esos headers
    → Si no es válido: responde 401 sin reenviar
```

## Variables de Entorno

| Variable | Descripción | Valor por Defecto |
|---|---|---|
| `PORT` | Puerto del servidor | `8080` |
| `JWT_SECRET` | Clave secreta JWT (debe coincidir con autenticación) | `MiClaveSecreta...` |
| `AUTH_URL` | URL del servicio de autenticación | `http://localhost:8081` |
| `EMPLEADOS_URL` | URL de gestión de empleados | `http://localhost:8082` |
| `PERFILES_URL` | URL de gestión de perfiles | `http://localhost:8083` |
| `VACACIONES_URL` | URL de gestión de vacaciones | `http://localhost:8084` |

## Swagger UI

```
http://localhost:8080/api-docs
```

## Health Check con Estado de Dependencias

```bash
curl http://localhost:8080/health
```

Respuesta esperada:
```json
{
  "status": "UP",
  "service": "api-gateway",
  "version": "1.0.0",
  "timestamp": "2024-01-15T10:00:00Z",
  "dependencies": {
    "autenticacion": "UP",
    "gestion-empleados": "UP",
    "gestion-perfiles": "UP",
    "gestion-vacaciones": "UP",
    "notificaciones": "UP"
  }
}
```

## Métricas

```
http://localhost:8080/metrics
```

Expone métricas de Prometheus incluyendo:
- `gateway_http_request_duration_seconds` (histograma)
- `gateway_http_requests_total` (contador por método/ruta/status)

## Cómo Correr los Tests

```bash
# Tests con cobertura
npm test -- --coverage

# Forzar cierre de handles abiertos
npm test
```
