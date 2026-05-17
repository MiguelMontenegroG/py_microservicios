# Microservicio de Autenticacion

Microservicio encargado de la autenticacion y autorizacion del sistema de gestion de empleados. Proporciona endpoints para login, cambio de contrasena, validacion de tokens JWT, y gestion de cuentas. Se comunica de forma asincrona via RabbitMQ con otros microservicios.

## Tecnologias

| Tecnologia | Version | Proposito |
|------------|---------|-----------|
| Java | 17+ | Lenguaje de programacion |
| Spring Boot | 3.1.12 | Framework principal |
| Spring Security | 6.x | Seguridad y autenticacion |
| Spring Data JPA / Hibernate | 6.2 | ORM y acceso a base de datos |
| PostgreSQL | 16 | Base de datos relacional |
| RabbitMQ | 3.13 | Mensajeria asincrona (AMQP) |
| jjwt | 0.12.5 | Generacion y validacion de tokens JWT (HMAC-SHA) |
| BCrypt | - | Hashing de contrasenas (strength 12) |
| springdoc-openapi | 2.3.0 | Documentacion OpenAPI / Swagger UI |
| Lombok | - | Reduccion de codigo boilerplate |
| Testcontainers | 1.19.8 | Tests de integracion con contenedores |
| JaCoCo | 0.8.12 | Cobertura de codigo (minimo 70%) |
| Prometheus / Micrometer | - | Metricas y observabilidad |

## Arquitectura

```
                     ┌───────────────────────┐
                     │   API Gateway (8080)   │
                     └──────────┬────────────┘
                                │
                     ┌──────────▼────────────┐
                     │   Autenticacion (8081) │
                     │   ┌───────────────┐   │
                     │   │  JWT Filter   │   │
                     │   └───────┬───────┘   │
                     │           │           │
                     │   ┌───────▼───────┐   │
                     │   │  AuthService  │   │
                     │   └───────┬───────┘   │
                     └──────────┬────────────┘
                                │
              ┌─────────────────┼─────────────────┐
              │                 │                  │
     ┌────────▼──────┐  ┌──────▼───────┐  ┌──────▼──────┐
     │  PostgreSQL   │  │   RabbitMQ   │  │  Prometheus  │
     │ autenticacion │  │  exchanges   │  │   metrics    │
     └───────────────┘  └──────────────┘  └─────────────┘
```

## Estructura del Proyecto

```
microservicios/autenticacion/
├── src/
│   ├── main/
│   │   ├── java/com/empresa/autenticacion/
│   │   │   ├── AutenticacionApplication.java    # Punto de entrada
│   │   │   ├── config/
│   │   │   │   ├── SecurityConfig.java          # Configuracion de Spring Security
│   │   │   │   ├── RabbitMQConfig.java          # Configuracion de RabbitMQ
│   │   │   │   └── SwaggerConfig.java           # Configuracion de OpenAPI/Swagger
│   │   │   ├── controller/
│   │   │   │   └── AuthController.java          # Endpoints REST
│   │   │   ├── dto/                             # Objetos de transferencia de datos
│   │   │   │   ├── LoginRequest.java
│   │   │   │   ├── LoginResponse.java
│   │   │   │   ├── ValidateTokenRequest.java
│   │   │   │   ├── ValidateTokenResponse.java
│   │   │   │   ├── ChangePasswordRequest.java
│   │   │   │   ├── HealthResponse.java
│   │   │   │   ├── ErrorResponse.java
│   │   │   │   ├── EventoEnvelope.java
│   │   │   │   ├── CuentaActivadaEvent.java
│   │   │   │   └── CuentaDesactivadaEvent.java
│   │   │   ├── exception/                       # Excepciones personalizadas
│   │   │   │   ├── GlobalExceptionHandler.java
│   │   │   │   ├── CredencialesInvalidasException.java
│   │   │   │   ├── CuentaDesactivadaException.java
│   │   │   │   ├── TokenInvalidoException.java
│   │   │   │   └── UsuarioNotFoundException.java
│   │   │   ├── model/
│   │   │   │   ├── Usuario.java                 # Entidad de usuarios
│   │   │   │   └── AuditLog.java                # Auditoria de acciones
│   │   │   ├── repository/
│   │   │   │   ├── UsuarioRepository.java
│   │   │   │   └── AuditLogRepository.java
│   │   │   ├── security/
│   │   │   │   └── JwtAuthenticationFilter.java # Filtro JWT
│   │   │   └── service/
│   │   │       ├── AuthService.java             # Logica de negocio
│   │   │       ├── JwtService.java              # Manejo de tokens JWT
│   │   │       ├── EmpleadoEventConsumer.java   # Consumidor de eventos RabbitMQ
│   │   │       └── CuentaEventPublisher.java    # Publicador de eventos RabbitMQ
│   │   └── resources/
│   │       ├── application.yml                  # Configuracion principal
│   │       └── logback-spring.xml               # Logging
│   └── test/java/com/empresa/autenticacion/
│       ├── service/
│       │   └── AuthServiceTest.java             # Tests unitarios (13)
│       └── controller/
│           └── AuthControllerIntegrationTest.java # Tests de integracion (9)
├── scripts/
│   └── generate_hash.py                         # Script para generar hash BCrypt
├── Dockerfile                                   # Build multi-etapa
├── Jenkinsfile                                  # Pipeline CI/CD
├── openapi.yaml                                 # Especificacion OpenAPI completa
└── pom.xml                                      # Dependencias Maven
```

## Endpoints

### Autenticacion

| Metodo | Ruta | Descripcion | Autenticacion |
|--------|------|-------------|---------------|
| `POST` | `/auth/login` | Iniciar sesion y obtener token JWT | No |
| `POST` | `/auth/change-password` | Cambiar contrasena del usuario autenticado | JWT |
| `POST` | `/auth/validate` | Validar token JWT (uso interno entre servicios) | No |
| `POST` | `/auth/seed` | **SOLO DESARROLLO** - Crear usuario admin de prueba | No |

### Gestion de Cuentas

| Metodo | Ruta | Descripcion | Autenticacion |
|--------|------|-------------|---------------|
| `PUT` | `/accounts/{empleadoId}/activate` | Activar cuenta de un empleado | JWT |
| `PUT` | `/accounts/{empleadoId}/deactivate` | Desactivar cuenta de un empleado | JWT |

### Salud y Metricas

| Metodo | Ruta | Descripcion | Autenticacion |
|--------|------|-------------|---------------|
| `GET` | `/health` | Health check del servicio | No |
| `GET` | `/actuator/health` | Health detallado (Spring Actuator) | No |
| `GET` | `/actuator/info` | Informacion del servicio | No |
| `GET` | `/actuator/prometheus` | Metricas en formato Prometheus | No |
| `GET` | `/actuator/metrics` | Metricas de la aplicacion | No |

### Documentacion

| Ruta | Descripcion |
|------|-------------|
| `/swagger-ui.html` | Interfaz Swagger UI |
| `/api-docs` | Especificacion OpenAPI en JSON |

## Flujo de Autenticacion

### Login
```
Cliente                      Servicio                    PostgreSQL
  │                            │                            │
  │  POST /auth/login          │                            │
  │  {username, password}      │                            │
  │──────────────────────────► │                            │
  │                            │  SELECT * FROM usuarios    │
  │                            │  WHERE username = ?        │
  │                            │──────────────────────────► │
  │                            │◄───────────────────────────│
  │                            │                            │
  │                            │  Verificar:                │
  │                            │  - Usuario existe?         │
  │                            │  - Cuenta activa?          │
  │                            │  - BCrypt match?           │
  │                            │                            │
  │                            │  Generar JWT               │
  │                            │  (empleadoId, username,    │
  │                            │   rol, expiracion)         │
  │                            │                            │
  │  {token, expiresIn,        │                            │
  │   rol, esPrimerAcceso}     │                            │
  │◄───────────────────────────│                            │
```

### Validacion de Token (entre servicios)
```
Servicio Interno              Autenticacion
  │                              │
  │  POST /auth/validate         │
  │  {token: "eyJ..."}          │
  │───────────────────────────► │
  │                              │  Decodificar y verificar  │
  │                              │  firma HMAC-SHA           │
  │                              │                            │
  │  {valid: true,              │
  │   empleadoId: "uuid",       │
  │   rol: "ADMIN",             │
  │   username: "admin@..."}    │
  │◄────────────────────────────│
```

## Comunicacion Asincrona (RabbitMQ)

### Eventos Consumidos

| Cola | Evento | Origen | Accion |
|------|--------|--------|--------|
| `auth.empleado.creado` | `empleado.creado` | gestion-empleados | Crear cuenta con password temporal |
| `auth.empleado.eliminado` | `empleado.eliminado` | gestion-empleados | Desactivar cuenta |
| `auth.vacaciones.programadas` | `vacaciones.programadas` | gestion-vacaciones | Desactivar cuenta por vacaciones |

### Eventos Publicados

| Exchange | Routing Key | Evento | Destino |
|----------|-------------|--------|---------|
| `cuentas.exchange` | `cuenta.activada` | Cuenta activada / creada | notificaciones |
| `cuentas.exchange` | `cuenta.desactivada` | Cuenta desactivada | notificaciones |

## Configuracion

### Variables de Entorno

| Variable | Default | Descripcion |
|----------|---------|-------------|
| `SERVER_PORT` | `8081` | Puerto del servidor |
| `AUTH_DB_URL` | `jdbc:postgresql://localhost:5432/autenticacion_db` | URL de conexion a PostgreSQL |
| `AUTH_DB_USERNAME` | `postgres` | Usuario de base de datos |
| `AUTH_DB_PASSWORD` | `postgres` | Contrasena de base de datos |
| `RABBITMQ_HOST` | `localhost` | Host de RabbitMQ |
| `RABBITMQ_PORT` | `5672` | Puerto de RabbitMQ |
| `RABBITMQ_USER` | `guest` | Usuario de RabbitMQ |
| `RABBITMQ_PASS` | `guest` | Contrasena de RabbitMQ |
| `JWT_SECRET` | `MiClaveSecretaParaJWTDeAutenticacionDebeSerLarga32Chars!` | Clave secreta para firmar JWT |
| `JWT_EXPIRATION_MS` | `86400000` | Tiempo de expiracion del token (24h en ms) |
| `BCRYPT_STRENGTH` | `12` | Coste del algoritmo BCrypt |
| `LOG_LEVEL` | `INFO` | Nivel de logging |

## Ejecucion

### Local (con Maven)

```bash
# Requisitos: Java 17+, Maven 3.9+, PostgreSQL, RabbitMQ

cd microservicios/autenticacion

# Compilar
mvn clean compile

# Ejecutar
mvn spring-boot:run
```

### Docker

```bash
# Construir imagen
docker build -t autenticacion:latest .

# Ejecutar (requiere red de docker compose o --link para PostgreSQL y RabbitMQ)
docker run -p 8081:8081 \
  -e AUTH_DB_URL=jdbc:postgresql://postgres:5432/autenticacion_db \
  -e RABBITMQ_HOST=rabbitmq \
  autenticacion:latest
```

### Docker Compose (recomendado)

```bash
# Desde la raiz del proyecto
docker-compose up -d --build autenticacion
```

## Pruebas

### Semilla de Desarrollo

Para crear un usuario administrador de prueba, ejecutar:

```bash
curl -X POST http://localhost:8081/auth/seed
```

Respuesta esperada:
```json
{
  "success": true,
  "message": "Usuario admin creado exitosamente",
  "username": "admin@empresa.com",
  "password": "Admin123!",
  "rol": "ADMIN"
}
```

### Credenciales de Prueba

| Usuario | Contrasena | Rol |
|---------|------------|-----|
| `admin@empresa.com` | `Admin123!` | ADMIN |

### Login

```bash
curl -X POST http://localhost:8081/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin@empresa.com", "password": "Admin123!"}'
```

Respuesta esperada:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "expiresIn": 86400000,
  "rol": "ADMIN",
  "esPrimerAcceso": false
}
```

### Tests Automatizados

```bash
# Tests unitarios y de integracion
mvn test

# Con reporte de cobertura
mvn verify

# El reporte HTML se genera en: target/site/jacoco/index.html
```

**Requisitos para tests de integracion:**
- Docker (Testcontainers levanta PostgreSQL y RabbitMQ en contenedores)

### Swagger UI

Una vez ejecutando el servicio, acceder a:
- **Swagger UI**: [http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html)
- **OpenAPI JSON**: [http://localhost:8081/api-docs](http://localhost:8081/api-docs)

## CI/CD (Jenkins)

El pipeline definido en `Jenkinsfile` ejecuta las siguientes etapas:

1. **Build**: Compilacion con Maven
2. **Test**: Ejecucion de tests unitarios y de integracion
3. **Quality**: Verificacion de cobertura minima del 70% (JaCoCo)
4. **Package**: Construccion y publicacion de imagen Docker

```bash
# Requisitos: Jenkins con plugin de Docker
# El pipeline se configura automaticamente al apuntar al repositorio
```

## Base de Datos

### Esquema

```sql
-- Tabla: usuarios
CREATE TABLE usuarios (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    empleado_id UUID NOT NULL UNIQUE,
    username VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    rol VARCHAR(20) NOT NULL DEFAULT 'EMPLEADO',
    es_primer_acceso BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Tabla: audit_logs
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    empleado_id UUID NOT NULL,
    accion VARCHAR(50) NOT NULL,
    detalle TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

## Seguridad

- **JWT**: Tokens firmados con HMAC-SHA (jjwt 0.12.x)
- **Contrasenas**: Hash con BCrypt (coste 12)
- **Stateless**: Sin sesiones de servidor; cada request lleva el token
- **CORS**: Deshabilitado en desarrollo (configurable)
- **CSRF**: Deshabilitado (API REST sin estado)

### Manejo de Tokens

Todos los endpoints protegidos requieren el header:

```
Authorization: Bearer <token>
```

El token JWT contiene los siguientes claims:

```json
{
  "sub": "550e8400-e29b-41d4-a716-446655440001",
  "username": "admin@empresa.com",
  "rol": "ADMIN",
  "iat": 1705312800,
  "exp": 1705399200
}
```

## Roles

| Rol | Descripcion |
|-----|-------------|
| `ADMIN` | Acceso completo a todos los endpoints |
| `EMPLEADO` | Acceso a operaciones basicas (cambio de password) |

## Excepciones y Codigos de Error

| HTTP Status | Codigo de Error | Descripcion |
|-------------|-----------------|-------------|
| `400` | `DATOS_INVALIDOS` | Datos de entrada incorrectos |
| `401` | `CREDENCIALES_INVALIDAS` | Usuario o contrasena incorrectos |
| `401` | `TOKEN_EXPIRADO` | El token JWT ha expirado |
| `401` | `TOKEN_INVALIDO` | Token JWT invalido |
| `403` | `CUENTA_DESACTIVADA` | La cuenta del usuario esta desactivada |
| `404` | `USUARIO_NO_ENCONTRADO` | Usuario no encontrado |

## Mantenimiento

### Notas Importantes

- El endpoint `/auth/seed` es **SOLO PARA DESARROLLO**. Debe deshabilitarse o protegerse en produccion.
- Las cuentas de empleados se crean automaticamente al recibir el evento `empleado.creado` desde el microservicio `gestion-empleados`.
- La reactivacion automatica de cuentas al finalizar vacaciones requiere un scheduler externo (pendiente de implementar).
- Cambiar `JWT_SECRET` en produccion por una clave segura de al menos 256 bits (32 caracteres).

### Scripts Utiles

Generar hash BCrypt para pruebas:
```bash
cd microservicios/autenticacion/scripts
python generate_hash.py
```
