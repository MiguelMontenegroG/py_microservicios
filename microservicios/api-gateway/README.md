# API Gateway

Punto de entrada único del sistema. Centraliza el routing, la validación de JWT y la composición de respuestas. Ningún microservicio backend es accesible directamente desde el exterior — todo pasa por aquí.

**Lenguaje:** Node.js 20 · **Framework:** Express 4 · **Puerto:** 8080

---

## Endpoints

| Método | Path | Auth | Destino | Notas |
|--------|------|------|---------|-------|
| `POST` | `/auth/login` | ❌ | autenticacion:8081 | Retorna JWT |
| `POST` | `/auth/change-password` | ✅ | autenticacion:8081 | |
| `POST` | `/auth/forgot-password` | ❌ | autenticacion:8081 | Solicitar codigo de recuperacion |
| `POST` | `/auth/reset-password` | ❌ | autenticacion:8081 | Restablecer contrasena con codigo |
| `GET` | `/employees` | ✅ | gestion-empleados:8082 | Paginado: `?page=0&size=10` |
| `POST` | `/employees` | ✅ | gestion-empleados:8082 | Dispara onboarding automático |
| `GET` | `/employees/:id` | ✅ | **COMPOSITE** | Combina empleados + perfiles |
| `PUT` | `/employees/:id` | ✅ | gestion-empleados:8082 | |
| `DELETE` | `/employees/:id` | ✅ | gestion-empleados:8082 | Soft delete → RETIRADO |
| `GET` | `/profile` | ✅ | gestion-perfiles:8083 | Perfil del empleado autenticado |
| `PUT` | `/profile` | ✅ | gestion-perfiles:8083 | |
| `POST` | `/vacations` | ✅ | gestion-vacaciones:8084 | |
| `GET` | `/vacations` | ✅ | gestion-vacaciones:8084 | `?empleadoId=uuid` opcional |
| `GET` | `/health` | ❌ | — | Pinga los 5 microservicios |
| `GET` | `/metrics` | ❌ | — | Métricas Prometheus (prom-client) |
| `GET` | `/api-docs` | ❌ | — | Swagger UI |

---

## Flujo de autenticación

```
Cliente → Authorization: Bearer {token}
              │
              ▼
       auth.middleware.js
       POST autenticacion:8081/auth/validate
              │
         ┌────┴────┐
       válido    inválido
         │          │
         ▼          ▼
    agrega headers  401 TOKEN_INVALIDO
    X-Empleado-Id
    X-Rol
         │
         ▼
    proxy al microservicio destino
    (sin reenviar Authorization)
```

Los microservicios internos **confían en los headers `X-Empleado-Id` y `X-Rol`** inyectados por el gateway. No validan JWT por su cuenta.

---

## Endpoint composite `GET /employees/:id`

Este endpoint llama en paralelo a dos servicios y combina la respuesta:

```javascript
const [empleadoResult, perfilResult] = await Promise.allSettled([
  axios.get(`gestion-empleados:8082/employees/${id}`),
  axios.get(`gestion-perfiles:8083/profiles/${id}`)
]);
```

- Si **empleado no existe** → 404
- Si **perfil no existe** → retorna el empleado con `perfil: null` (no es error)
- Usa `Promise.allSettled` para que el fallo del perfil no cancele la respuesta del empleado

---

## Variables de entorno

| Variable | Descripción | Valor por defecto |
|---|---|---|
| `PORT` | Puerto del servicio | `8080` |
| `JWT_SECRET` | Secreto para validar JWT (debe coincidir con autenticacion) | `MiClaveSecretaParaJWTDeAutenticacionDebeSerLargaDe32Chars!` |
| `AUTH_URL` | URL del servicio de autenticación | `http://localhost:8081` |
| `EMPLEADOS_URL` | URL del servicio de empleados | `http://localhost:8082` |
| `PERFILES_URL` | URL del servicio de perfiles | `http://localhost:8083` |
| `VACACIONES_URL` | URL del servicio de vacaciones | `http://localhost:8084` |
| `NOTIFICACIONES_URL` | URL del servicio de notificaciones | `http://localhost:8085` |
| `REQUEST_TIMEOUT` | Timeout en ms para llamadas a microservicios | `5000` |

---

## Estructura del código

```
src/
├── app.js                      # Configuración Express — NO hace listen()
├── index.js                    # Hace listen() solo si es módulo principal
├── config.js                   # Variables de entorno centralizadas
├── server.js                   # Entry point de producción
├── middleware/
│   ├── auth.middleware.js      # Valida JWT llamando a autenticacion
│   ├── error.middleware.js     # Manejo global de errores Express
│   └── logger.middleware.js    # Winston + Morgan en formato JSON
├── proxy/
│   └── httpProxy.js            # Forward con axios (timeout, error mapping)
└── routes/
    ├── auth.routes.js
    ├── employees.routes.js     # Incluye lógica composite
    ├── profile.routes.js
    └── vacations.routes.js
```

> `app.js` y `index.js` están separados intencionalmente: los tests importan `app.js` directamente para evitar el error `EADDRINUSE` que ocurre cuando Jest levanta el servidor en múltiples archivos de test en paralelo.

---

## Tests

```bash
npm ci
npm test -- --coverage
```

| Suite | Tests | Cobertura |
|---|---|---|
| `auth.routes.test.js` | 8 | 88.23% |
| `employees.routes.test.js` | 11 | 90.38% |
| `health.routes.test.js` | 4 | — |
| `profile.routes.test.js` | 4 | 90% |
| `vacations.routes.test.js` | 7 | 87.87% |
| `error.middleware.test.js` | 3 | 100% |
| **Total** | **37** | **89.68% global** |

Los tests usan `nock` para interceptar llamadas HTTP salientes y `node-mocks-http` para tests de middleware.

---

## Levantar solo este servicio

```bash
# Requiere que autenticacion esté corriendo (para validar tokens)
docker-compose up -d autenticacion gestion-empleados gestion-perfiles gestion-vacaciones
docker-compose up -d --build api-gateway

# Verificar
curl http://localhost:8080/health
```