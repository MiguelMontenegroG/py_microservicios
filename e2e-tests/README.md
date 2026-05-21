# Pruebas E2E (BDD) - Sistema de Gestion de Empleados

## Que es BDD

Behavior-Driven Development (BDD) describe el comportamiento del sistema en lenguaje natural usando escenarios Given/When/Then. Estos escenarios sirven como documentacion ejecutable y pruebas automatizadas que validan los flujos de negocio del sistema.

## Prerrequisitos

- Node.js 20+
- Docker y Docker Compose
- Sistema de microservicios ejecutandose:
  ```bash
  cd proyecto-final-microservicios
  docker-compose up --build -d
  ```
- Verificar que todos los servicios esten saludables:
  ```bash
  curl http://localhost:8080/health
  ```

## Ejecucion Local

```bash
cd e2e-tests
npm install
copy env.example .env    # Windows
# cp env.example .env    # Linux/Mac
# Editar .env si los puertos o credenciales son diferentes
npm test
```

## Ejecucion en Docker

El servicio `e2e-tests` esta definido en el `docker-compose.yml` con perfil `tests`.

```bash
# Ejecutar las pruebas BDD dentro de Docker
docker-compose --profile tests run --rm e2e-tests

# Para reconstruir y ejecutar (si hay cambios en el codigo)
docker-compose --profile tests run --rm --build e2e-tests

# Para mantener los contenedores principales corriendo mientras se ejecutan las pruebas
docker-compose up -d
docker-compose --profile tests run --rm e2e-tests
```

El contenedor se conecta al API Gateway a traves de la red interna `backend-network` usando la URL `http://api-gateway:8080`.

## Parametros de Polling

| Parametro | Valor | Justificacion |
|---|---|---|
| `maxAttempts` | 12 | Cubre el peor caso de propagacion asincrona |
| `intervalMs` | 2000 | Balance entre velocidad y carga del servidor |
| **Espera maxima** | **24s** | Suficiente para RabbitMQ + consumidores + BD |

El sistema usa eventos asincronos via RabbitMQ. Cuando se crea un empleado, el servicio de autenticacion debe consumir el evento `empleado.creado`, generar credenciales y persistirlas. Esto tipicamente toma 1-5 segundos pero puede demorar mas bajo carga. El polling evita pruebas flaky que ocurririan con `setTimeout` fijo.

## Escenarios Cubiertos

| Archivo Feature | Escenarios | Flujo Cubierto |
|---|---|---|
| `smoke.feature` | 1 | Health check - verifica que el API Gateway responda |
| `security.feature` | 4 | Autenticacion - sin token, token invalido, acceso ADMIN, RBAC |
| `onboarding.feature` | 4 | Creacion de empleados - exito, credenciales async, validacion, duplicados |
| `offboarding.feature` | 2 | Eliminacion de empleados - login deshabilitado, 404 en busqueda |

## Estructura del Proyecto

```
e2e-tests/
├── package.json              # Dependencias y scripts
├── cucumber.js               # Configuracion de Cucumber
├── Dockerfile                # Imagen para ejecucion en Docker
├── env.example               # Template de variables de entorno
├── README.md                 # Este archivo
├── features/
│   ├── smoke.feature         # Escenarios de health check
│   ├── security.feature      # Escenarios de autenticacion/autorizacion
│   ├── onboarding.feature    # Escenarios de creacion de empleados
│   └── offboarding.feature   # Escenarios de eliminacion de empleados
└── src/
    ├── support/
    │   ├── world.js          # Contexto compartido (World constructor)
    │   ├── hooks.js          # Before/After por escenario
    │   └── polling.js        # Utilidad de polling asincrono reutilizable
    └── steps/
        ├── common.steps.js   # Steps genericos HTTP y aserciones
        ├── auth.steps.js     # Steps de autenticacion (login, token)
        ├── employee.steps.js # Steps de CRUD de empleados
        └── offboarding.steps.js # Steps de flujo de offboarding
```

## Mejores Practicas

1. **Emails unicos**: Cada escenario genera emails con timestamp para evitar colisiones
2. **Escenarios independientes**: Cada escenario resetea su estado en el hook `Before`
3. **Polling sin sleeps**: Usar `waitUntil()` de `polling.js`, jamas `setTimeout(fn, N)`
4. **Variables de entorno**: Todas las URLs y credenciales vienen de `.env`

## Solucion de Problemas

**Las pruebas fallan con 401 en login ADMIN:**
- Verificar que el sistema este corriendo: `curl http://localhost:8080/health`
- Verificar credenciales de admin en `.env`
- Ejecutar `curl -X POST http://localhost:8081/auth/seed` si el admin no fue inicializado

**Las pruebas fallan con 409 al crear empleados:**
- El email ya esta en uso. Los steps usan emails unicos con timestamp
- Verificar que `.env` este configurado correctamente

**Las aserciones asincronas agotan el tiempo de espera:**
- `waitUntil` espera hasta 24 segundos. Si el sistema es mas lento, aumentar `maxAttempts`
- Verificar que los consumidores de RabbitMQ esten activos: `http://localhost:15672` (guest/guest)
