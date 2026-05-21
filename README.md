# Sistema de Gestión de Empleados - Arquitectura de Microservicios

Sistema empresarial que automatiza el ciclo de vida completo del empleado: **onboarding**, **gestión de perfil**, **vacaciones** y **offboarding**. Construido sobre arquitectura de microservicios con comunicación asíncrona mediante RabbitMQ, observabilidad completa con ELK + Prometheus + Grafana, y pipeline CI/CD con Jenkins.

---

## Tabla de Contenidos

1. [Arquitectura](#arquitectura)
2. [Stack tecnológico](#stack-tecnológico)
3. [Requisitos previos](#requisitos-previos)
4. [Levantar el sistema](#levantar-el-sistema)
5. [URLs de acceso](#urls-de-acceso)
6. [Credenciales por defecto](#credenciales-por-defecto)
7. [Flujos de negocio](#flujos-de-negocio)
8. [Endpoints del API Gateway](#endpoints-del-api-gateway)
9. [Eventos del sistema](#eventos-del-sistema)
10. [Observabilidad](#observabilidad)
11. [Estructura del proyecto](#estructura-del-proyecto)
12. [Comandos útiles](#comandos-útiles)
13. [Solución de problemas](#solución-de-problemas)

---

## Arquitectura

```
┌─────────────────────────────────────────────────────────────┐
│                        CLIENTE                               │
│                  (curl / Browser / App)                      │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTPS
                           ▼
┌─────────────────────────────────────────────────────────────┐
│               API GATEWAY - Node.js :8080                    │
│     Routing · Validación JWT · Endpoint composite           │
└──┬──────────┬────────────┬────────────┬────────────┬────────┘
   │ REST     │ REST       │ REST       │ REST       │ REST
   ▼          ▼            ▼            ▼            ▼
:8081      :8082         :8083        :8084        :8085
Auth       Empleados     Perfiles     Vacaciones   Notificaciones
Java       Java          Python       Go           Python
PostgreSQL PostgreSQL    MongoDB      PostgreSQL   (sin BD)
   │          │            │            │            │
   └──────────┴────────────┴────────────┴────────────┘
                           │ Eventos asíncronos
                           ▼
              ┌─────────────────────────┐
              │   RabbitMQ :5672        │
              │   3 exchanges · 9 queues│
              └─────────────────────────┘
                           │
              ┌────────────┴────────────┐
              ▼                         ▼
   ELK Stack (logs)         Prometheus + Grafana (métricas)
   Filebeat→Logstash→ES→Kibana    Scraping /metrics
```

**Principios de comunicación:**
- **Sincrónica (REST):** solo entre Cliente ↔ API Gateway ↔ Microservicios
- **Asíncrona (Eventos):** entre microservicios, exclusivamente via RabbitMQ
- Los microservicios nunca se llaman entre sí por HTTP

---

## Stack Tecnológico

| Microservicio | Lenguaje | Framework | Base de Datos | Puerto |
|---|---|---|---|---|
| API Gateway | Node.js 20 | Express 4 | — | 8080 |
| Autenticación | Java 21 | Spring Boot 3.1 + Security | PostgreSQL 16 | 8081 |
| Gestión de Empleados | Java 21 | Spring Boot 3.1 + JPA | PostgreSQL 16 | 8082 |
| Gestión de Perfiles | Python 3.12 | FastAPI | MongoDB 7 | 8083 |
| Gestión de Vacaciones | Go 1.22 | Gin | PostgreSQL 16 | 8084 |
| Notificaciones | Python 3.12 | FastAPI | — | 8085 |

> ✅ Cumple la restricción de **4 lenguajes distintos**: Java · Node.js · Python · Go

**Infraestructura de soporte:**

| Servicio | Imagen | Propósito |
|---|---|---|
| RabbitMQ | `rabbitmq:3.13-management-alpine` | Message broker |
| PostgreSQL (×3) | `postgres:16-alpine` | BD para auth, empleados, vacaciones |
| MongoDB | `mongo:7` | BD para perfiles |
| MailHog | `mailhog/mailhog` | Servidor SMTP para desarrollo |
| Elasticsearch | `elasticsearch:8.13.0` | Almacén de logs |
| Logstash | `logstash:8.13.0` | Procesamiento de logs |
| Filebeat | `elastic/filebeat:8.13.0` | Captura de logs de contenedores |
| Kibana | `kibana:8.13.0` | Visualización de logs |
| Prometheus | `prom/prometheus:v2.51.0` | Recolección de métricas |
| Grafana | `grafana/grafana:10.4.0` | Dashboards de métricas |
| Jenkins | `jenkins/jenkins:lts-jdk21` | Pipeline CI/CD |

---

## Requisitos Previos

- Docker >= 24.x
- Docker Compose >= 2.x
- Git
- 8 GB RAM disponible recomendados (ELK consume ~2 GB)

---

## Levantar el Sistema

```bash
# 1. Clonar el repositorio
git clone https://github.com/MiguelMontenegroG/py_microservicios
cd py_microservicios

# 2. Levantar todo el sistema
docker-compose up --build

# La primera vez tarda ~5 minutos:
# - Descarga imágenes base (~3 GB)
# - Compila los servicios Java con Maven
# - Instala dependencias Python y Node.js
# - Compila el binario Go

# 3. Verificar que todos los servicios están saludables
docker-compose ps
# Todos deben mostrar "healthy" o "running"
```

**Levantar en segundo plano:**
```bash
docker-compose up --build -d
docker-compose logs -f   # seguir logs en tiempo real
```

**Detener el sistema:**
```bash
# Detener preservando datos (bases de datos, volúmenes)
docker-compose down

# Detener y borrar TODOS los datos — reset completo
docker-compose down -v
```

**Reconstruir un servicio individual:**
```bash
docker-compose up -d --build gestion-empleados
```

---

## URLs de Acceso

| Servicio | URL | Notas |
|---|---|---|
| **API Gateway** | http://localhost:8080 | Punto de entrada único |
| **Swagger UI** | http://localhost:8080/api-docs | Documentación interactiva de todos los endpoints |
| Swagger — Auth | http://localhost:8081/swagger-ui.html | |
| Swagger — Empleados | http://localhost:8082/swagger-ui.html | |
| Swagger — Perfiles | http://localhost:8083/docs | FastAPI auto-docs |
| Swagger — Notificaciones | http://localhost:8085/docs | FastAPI auto-docs |
| **RabbitMQ** | http://localhost:15672 | Gestión de exchanges y queues |
| **MailHog** | http://localhost:8025 | Ver emails enviados por el sistema |
| **Kibana** | http://localhost:5601 | Explorar logs centralizados |
| **Grafana** | http://localhost:3000 | Dashboards de métricas |
| **Prometheus** | http://localhost:9091 | Métricas crudas y targets |
| **Jenkins** | http://localhost:9090 | Pipelines CI/CD |

---

## Credenciales por Defecto

| Servicio | Usuario | Contraseña   |
|---|---|--------------|
| Admin del sistema | `admin@empresa.com` | `Admin123!`  |
| RabbitMQ | `guest` | `guest`      |
| Grafana | `admin` | `admin123`   |
| Jenkins | `admin` | `admin`      |
| MailHog | — | — (sin auth) |
| Kibana | — | — (sin auth) |

> ⚠️ El usuario admin del sistema se crea llamando a `POST /auth/seed` la primera vez (ver flujo de onboarding).

---

## Flujos de Negocio

### Onboarding — Alta de empleado

Cuando RRHH registra un nuevo empleado, el sistema orquesta automáticamente la creación de credenciales y el envío del email de bienvenida sin intervención manual.

```
RRHH → POST /employees
         │
         ▼
  gestion-empleados
  guarda empleado (ACTIVO)
  publica: empleado.creado
         │
         ├──► autenticacion
         │    genera password aleatoria (10 chars)
         │    guarda hash BCrypt
         │    publica: cuenta.activada {email, passwordTemporal}
         │              │
         │              └──► notificaciones
         │                   envía email de bienvenida
         │                   con credenciales a MailHog
         │
         └──► gestion-perfiles
              crea perfil vacío
              listo para que el empleado lo complete
```

**Comandos:**
```bash
# Paso 1 — Crear usuario admin (solo la primera vez)
curl -X POST http://localhost:8081/auth/seed

# Paso 2 — Login como admin
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin@empresa.com","password":"Admin123!"}'
# Guardar el "token" de la respuesta

# Paso 3 — Crear empleado
curl -X POST http://localhost:8080/employees \
  -H "Authorization: Bearer {TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "nombre": "Ana",
    "apellido": "García",
    "email": "ana@empresa.com",
    "numeroEmpleado": "EMP-010",
    "fechaIngreso": "2024-01-15",
    "cargo": "Desarrolladora",
    "area": "TI"
  }'

# Paso 4 — Ver el email de bienvenida con la contraseña temporal
# Abrir http://localhost:8025
```

**En PowerShell:**
```powershell
$token = (Invoke-RestMethod -Uri "http://localhost:8081/auth/login" `
  -Method POST -ContentType "application/json" `
  -Body '{"username":"admin@empresa.com","password":"Admin123!"}').token

$empleado = Invoke-RestMethod -Uri "http://localhost:8082/employees" `
  -Method POST -Headers @{"Authorization"="Bearer $token"} `
  -ContentType "application/json" `
  -Body '{"nombre":"Ana","apellido":"Garcia","email":"ana@empresa.com","numeroEmpleado":"EMP-010","fechaIngreso":"2024-01-15","cargo":"Desarrolladora","area":"TI"}'

Write-Output "Empleado creado: $($empleado.id)"
```

---

### Login del empleado

```bash
# Con la contraseña temporal del email de bienvenida
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"ana@empresa.com","password":"{PASSWORD_TEMPORAL}"}'
```

---

### Gestión de Perfil

```bash
# Ver mi perfil
curl http://localhost:8080/profile \
  -H "Authorization: Bearer {TOKEN_EMPLEADO}"

# Actualizar mi perfil
curl -X PUT http://localhost:8080/profile \
  -H "Authorization: Bearer {TOKEN_EMPLEADO}" \
  -H "Content-Type: application/json" \
  -d '{
    "telefono": "3001234567",
    "biografia": "Desarrolladora full-stack",
    "direccion": {
      "calle": "Calle 123",
      "ciudad": "Bogotá",
      "codigoPostal": "110111",
      "pais": "Colombia"
    }
  }'
```

---

### Vacaciones

Al programar vacaciones, la cuenta del empleado se desactiva automáticamente.

```bash
curl -X POST http://localhost:8080/vacations \
  -H "Authorization: Bearer {TOKEN_ADMIN}" \
  -H "Content-Type: application/json" \
  -d '{
    "empleadoId": "{EMPLEADO_ID}",
    "email": "ana@empresa.com",
    "nombre": "Ana García",
    "fechaInicio": "2025-08-01",
    "fechaFin": "2025-08-15"
  }'
# El empleado recibe email de confirmación en MailHog
```

**Validaciones aplicadas:**
- `fechaFin` debe ser posterior a `fechaInicio`
- No se permiten períodos solapados para el mismo empleado (retorna 409)
- Formato de fechas: `YYYY-MM-DD`

---

### Recuperacion de Contrasena

El sistema permite recuperar la contrasena sin intervencion del administrador mediante un codigo de 6 digitos enviado por email.

```bash
# Paso 1 — Solicitar codigo de recuperacion (no requiere JWT)
curl -X POST http://localhost:8080/auth/forgot-password \
  -H "Content-Type: application/json" \
  -d '{"email":"ana@empresa.com"}'
# Respuesta (siempre la misma por seguridad):
# {"success":true,"message":"Si el email esta registrado, recibiras un codigo de recuperacion"}

# Paso 2 — Ver el codigo en MailHog (http://localhost:8025)
# Usar el codigo de 6 digitos recibido

# Paso 3 — Restablecer la contrasena con el codigo
curl -X POST http://localhost:8080/auth/reset-password \
  -H "Content-Type: application/json" \
  -d '{"email":"ana@empresa.com","codigo":"482913","newPassword":"NuevaPassword123!"}'
# Respuesta:
# {"success":true,"message":"Contrasena restablecida exitosamente"}
```

### Cambio de Contraseña

```bash
curl -X POST http://localhost:8080/auth/change-password \
  -H "Authorization: Bearer {TOKEN_EMPLEADO}" \
  -H "Content-Type: application/json" \
  -d '{"currentPassword":"{PASSWORD_ACTUAL}","newPassword":"NuevaPassword123!"}'
```

---

### Offboarding — Baja de empleado

```bash
curl -X DELETE http://localhost:8080/employees/{EMPLEADO_ID} \
  -H "Authorization: Bearer {TOKEN_ADMIN}"
# El sistema desactiva credenciales, archiva perfil y envía email de notificación
```

**Lo que ocurre automáticamente:**
- La cuenta de autenticación queda desactivada permanentemente
- El perfil queda archivado (no se elimina — auditoría)
- El empleado recibe un email de notificación
- Se registra en el audit log con fecha y hora

---

## Endpoints del API Gateway

| Método | Path | Auth | Descripción |
|---|---|---|---|
| `POST` | `/auth/login` | ❌ | Login — retorna JWT |
| `POST` | `/auth/change-password` | ✅ | Cambiar contraseña |
| `POST` | `/auth/forgot-password` | ❌ | Solicitar codigo de recuperacion de contrasena (se envia por email) |
| `POST` | `/auth/reset-password` | ❌ | Restablecer contrasena con codigo de 6 digitos |
| `GET` | `/employees` | ✅ | Listar empleados |
| `POST` | `/employees` | ✅ | Crear empleado (dispara onboarding) |
| `GET` | `/employees/{id}` | ✅ | Empleado completo (datos + perfil) |
| `PUT` | `/employees/{id}` | ✅ | Actualizar empleado |
| `DELETE` | `/employees/{id}` | ✅ | Eliminar empleado (offboarding) |
| `GET` | `/profile` | ✅ | Ver mi perfil |
| `PUT` | `/profile` | ✅ | Actualizar mi perfil |
| `POST` | `/vacations` | ✅ | Programar vacaciones |
| `GET` | `/vacations` | ✅ | Listar vacaciones |
| `GET` | `/health` | ❌ | Estado del sistema y dependencias |
| `GET` | `/metrics` | ❌ | Métricas Prometheus |
| `GET` | `/api-docs` | ❌ | Swagger UI |

> ⚠️ No existe endpoint `/auth/register`. Las cuentas se crean **únicamente** de forma automática al registrar un empleado.

**Autenticación:** todas las rutas marcadas con ✅ requieren header:
```
Authorization: Bearer {JWT_TOKEN}
```

---

## Eventos del Sistema

### Exchanges y Routing Keys

| Exchange | Routing Key | Publicado por | Consumido por |
|---|---|---|---|
| `empleados.exchange` | `empleado.creado` | gestion-empleados | autenticacion, gestion-perfiles, notificaciones |
| `empleados.exchange` | `empleado.actualizado` | gestion-empleados | gestion-perfiles |
| `empleados.exchange` | `empleado.eliminado` | gestion-empleados | autenticacion, gestion-perfiles |
| `vacaciones.exchange` | `vacaciones.programadas` | gestion-vacaciones | autenticacion, notificaciones |
| `cuentas.exchange` | `cuenta.activada` | autenticacion | notificaciones |
| `cuentas.exchange` | `cuenta.desactivada` | autenticacion | notificaciones |
| `cuentas.exchange` | `cuenta.reset-solicitado` | autenticacion | notificaciones |

### Queues activas (10)

```
auth.empleado.creado          perfiles.empleado.creado       notif.cuenta.activada
auth.empleado.eliminado       perfiles.empleado.actualizado  notif.cuenta.desactivada
auth.vacaciones.programadas   perfiles.empleado.eliminado    notif.vacaciones.programadas
                                                              notif.cuenta.reset-solicitado
```

### Estados del empleado

```
ACTIVO ──── (programar vacaciones) ──► EN_VACACIONES ──── (fin vacaciones) ──► ACTIVO
  │
  └──── (eliminar / retirar) ──────────────────────────────────────────────► RETIRADO
```

---

## Observabilidad

### Logs Centralizados (ELK)

Todos los microservicios emiten logs en formato JSON a stdout. El flujo es:

```
Microservicio → stdout → Docker → Filebeat → Logstash → Elasticsearch → Kibana
```

**Configurar Kibana (primera vez):**
1. Abrir http://localhost:5601
2. Ir a **Stack Management → Index Patterns → Create index pattern**
3. Pattern: `microservicios-*` — Time field: `@timestamp`
4. Ir a **Discover** para explorar logs

**Campos disponibles en los logs:**
- `service` — nombre del microservicio
- `level` — INFO · WARN · ERROR
- `message` — descripción del evento
- `timestamp` — fecha y hora ISO-8601

**Búsquedas útiles en Kibana (KQL):**
```
level: "ERROR"                          # Solo errores
service: "gestion-empleados"            # Logs de un servicio específico
level: "ERROR" and service: "autenticacion"
```

### Monitoreo (Prometheus + Grafana)

Todos los servicios exponen métricas en `/metrics` o `/actuator/prometheus`.

**Verificar targets en Prometheus:**
- Abrir http://localhost:9091/targets
- Los 7 targets deben estar en estado **UP**: api-gateway, autenticacion, gestion-empleados, gestion-perfiles, gestion-vacaciones, notificaciones, rabbitmq

**Dashboards en Grafana (http://localhost:3000):**
- **Microservicios Overview** — estado general, latencia P95, requests/s, errores, métricas JVM, métricas de negocio
- **Logs Dashboard** — visualización de logs desde Elasticsearch

**Alertas configuradas:**

| Alerta | Condición | Severidad |
|---|---|---|
| `ServicioDown` | Un servicio no responde por > 1 min | Critical |
| `AltaTasaErrores` | Errores 5xx > 5% por > 2 min | Warning |
| `AltaLatencia` | P95 > 2 segundos por > 5 min | Warning |
| `AltaMemoriaJVM` | Heap JVM > 85% por > 5 min | Warning |

---

## Estructura del Proyecto

```
py_microservicios/
│
├── docker-compose.yml              # Orquestación completa — un solo comando levanta todo
├── README.md
│
├── microservicios/
│   ├── api-gateway/                # Node.js 20 / Express — puerto 8080
│   │   ├── Dockerfile              # Multi-stage, node:20-alpine
│   │   ├── Jenkinsfile
│   │   ├── src/
│   │   │   ├── app.js              # Configuración Express (sin listener)
│   │   │   ├── index.js            # Listener del servidor
│   │   │   ├── middleware/         # auth.middleware.js, logger.middleware.js
│   │   │   ├── routes/             # auth, employees, profile, vacations
│   │   │   └── proxy/httpProxy.js  # Forwarding HTTP con axios
│   │   └── tests/                  # 37 tests, 89.68% cobertura
│   │
│   ├── autenticacion/              # Java 21 / Spring Boot — puerto 8081
│   │   ├── Dockerfile              # Multi-stage, bellsoft/liberica-openjre-alpine:21
│   │   ├── Jenkinsfile
│   │   ├── src/main/java/
│   │   │   ├── controller/         # AuthController (login, validate, change-password, forgot/reset-password)
│   │   │   ├── service/            # AuthService, JwtService, PasswordResetService, EmpleadoEventConsumer
│   │   │   ├── model/              # Usuario, PasswordResetToken, AuditLog
│   │   │   └── config/             # SecurityConfig, RabbitMQConfig
│   │   └── src/test/               # 36 tests (21 unitarios + 15 integración)
│   │
│   ├── gestion-empleados/          # Java 21 / Spring Boot — puerto 8082
│   │   ├── Dockerfile              # Multi-stage, bellsoft/liberica-openjre-alpine:21
│   │   ├── Jenkinsfile
│   │   ├── src/main/java/
│   │   │   ├── controller/         # EmpleadoController
│   │   │   ├── service/            # EmpleadoService, EmpleadoEventPublisher
│   │   │   ├── model/              # Empleado (ACTIVO/EN_VACACIONES/RETIRADO)
│   │   │   └── config/             # RabbitMQConfig
│   │   └── src/test/               # 34 tests (17 unitarios + 17 integración)
│   │
│   ├── gestion-perfiles/           # Python 3.12 / FastAPI — puerto 8083
│   │   ├── Dockerfile              # Multi-stage, python:3.12-alpine
│   │   ├── Jenkinsfile
│   │   ├── src/
│   │   │   ├── routers/            # perfil_router.py
│   │   │   ├── services/           # perfil_service.py, rabbit_consumer.py
│   │   │   ├── models/             # perfil.py (documento MongoDB)
│   │   │   └── schemas/            # perfil_schema.py (Pydantic)
│   │   └── tests/                  # 18 tests (9 servicio + 9 router)
│   │
│   ├── gestion-vacaciones/         # Go 1.22 / Gin — puerto 8084
│   │   ├── Dockerfile              # Multi-stage, golang:1.22-alpine → alpine:3.19
│   │   ├── Jenkinsfile
│   │   ├── cmd/main.go             # Entry point con graceful shutdown
│   │   ├── internal/
│   │   │   ├── handlers/           # vacaciones_handler.go
│   │   │   ├── service/            # vacaciones_service.go (AppError)
│   │   │   ├── repository/         # vacaciones_repo.go (interfaz)
│   │   │   └── messaging/          # rabbit_publisher.go
│   │   └── tests/                  # 27 tests (14 servicio + 13 handler)
│   │
│   └── notificaciones/             # Python 3.12 / FastAPI — puerto 8085
│       ├── Dockerfile              # Multi-stage, python:3.12-alpine
│       ├── Jenkinsfile
│       ├── src/
│       │   ├── email_service.py    # aiosmtplib + Jinja2
│   │   ├── consumers/          # cuenta_consumer.py, vacaciones_consumer.py, reset_consumer.py
│   │   └── templates/          # bienvenida.html, vacaciones.html, desactivacion.html, recuperacion.html
│       └── tests/                  # 51 tests, 72.86% cobertura
│
├── observabilidad/
│   ├── logs/
│   │   ├── filebeat.yml            # Captura logs de contenedores Docker
│   │   ├── logstash.conf           # Pipeline: Filebeat → Elasticsearch
│   │   └── logstash-pipeline.yml
│   ├── monitoreo/
│   │   ├── prometheus.yml          # Scrape de los 7 targets
│   │   ├── alertas.yml             # 4 reglas de alerta
│   │   └── grafana-datasources.yml # Prometheus + Elasticsearch
│   └── dashboards/
│       ├── dashboards-provisioning.yml
│       ├── overview.json           # Dashboard principal (14 paneles)
│       └── logs-dashboard.json     # Dashboard de logs
│
└── scripts/
    ├── init-db.sql                 # Esquema inicial de las 3 bases de datos PostgreSQL
    └── seed-data.sql               # Datos de prueba
```

---

## Comandos Docker Esenciales

### Gestion de contenedores

```bash
# Listar todos los contenedores (en ejecucion)
docker ps

# Listar todos los contenedores (incluyendo detenidos)
docker ps -a

# Ver solo los nombres y estado de los servicios del sistema
docker-compose ps

# Ver el estado de salud de cada contenedor
docker-compose ps | grep -E "Name|Up|Exit|healthy|unhealthy"
```

En PowerShell:
```powershell
docker-compose ps
```

### Logs

```bash
# Logs de todos los servicios en tiempo real
docker-compose logs -f

# Logs de un servicio especifico
docker-compose logs -f gestion-vacaciones

# Ultimas N lineas de un servicio
docker-compose logs --tail 50 notificaciones

# Logs de un contenedor por nombre directo
docker logs proyecto-final-microservicios-api-gateway-1 --tail 20
```

### Arrancar y detener

```bash
# Levantar todo el sistema
docker-compose up -d

# Reconstruir y levantar un servicio especifico
docker-compose up -d --build notificaciones

# Detener todo (preserva datos en volumenes)
docker-compose down

# Detener todo y borrar volumenes (BORRA DATOS - reset completo)
docker-compose down -v

# Reiniciar un servicio
docker-compose restart gestion-empleados

# Pausar un servicio (lo deja inaccesible pero sin detenerlo)
docker-compose pause notificaciones

# Reanudar un servicio pausado
docker-compose unpause notificaciones
```

### Informacion del sistema

```bash
# Ver recursos usados por cada contenedor (CPU, memoria, red)
docker stats

# Ver cuanta RAM usa cada servicio
docker stats --no-stream

# Ver redes creadas por docker-compose
docker network ls | Select-String "proyecto-final"

# Ver el IP interno de un contenedor
docker inspect proyecto-final-microservicios-rabbitmq-1 | grep IPAddress

# Ver variables de entorno de un contenedor en ejecucion
docker exec proyecto-final-microservicios-autenticacion-1 env | sort
```

### Ejecutar comandos dentro de un contenedor

```bash
# Shell interactivo dentro de un contenedor (Linux)
docker exec -it proyecto-final-microservicios-db-empleados-1 sh

# Shell en un contenedor de base de datos PostgreSQL
docker exec -it proyecto-final-microservicios-db-auth-1 psql -U auth_user -d auth_db

# Shell en MongoDB
docker exec -it proyecto-final-microservicios-mongo-perfiles-1 mongosh -u perfiles_user -p perfiles_pass perfiles_db

# Ejecutar un comando sin entrar al contenedor
docker exec proyecto-final-microservicios-rabbitmq-1 rabbitmqctl list_queues name consumers messages
```

### Limpieza y mantenimiento

```bash
# Ver imagenes construidas para el proyecto
docker images | Select-String "proyecto-final"

# Eliminar contenedores detenidos (libera espacio)
docker container prune -f

# Eliminar imagenes no usadas
docker image prune -a -f

# Ver volumenes (datos persistentes)
docker volume ls | Select-String "proyecto-final"

# Ver el tamano de cada volumen
docker system df -v | Select-String "proyecto-final" -Context 0,3
```

### Diagnosticos rapidos

```bash
# Verificar que todos los health checks pasan
curl -s http://localhost:8080/health
# Ejemplo de respuesta esperada:
# {"status":"UP","services":{"auth":"UP","empleados":"UP","perfiles":"UP","vacaciones":"UP","notificaciones":"UP"}}

# Verificar RabbitMQ (debe responder con credenciales guest:guest)
curl -s -u guest:guest http://localhost:15672/api/overview | python3 -c "import sys,json; d=json.load(sys.stdin); print(f'RabbitMQ: {d[\"rabbitmq_version\"]} - {d[\"cluster_name\"]}')"

# Ver colas con mensajes acumulados
curl -s -u guest:guest http://localhost:15672/api/queues | python3 -c "
import sys,json
queues = json.load(sys.stdin)
for q in queues:
    if q['messages'] > 0:
        print(f\"  {q['name']}: {q['messages']} msgs - {q['consumers']} consumers\")
"

---

## Solución de Problemas

### El sistema tarda mucho en arrancar
Normal en la primera ejecución. Maven descarga ~500 MB de dependencias Java. Con conexión estándar tarda ~5 minutos. Las siguientes veces usa caché y tarda ~1 minuto.

### Un servicio aparece como `unhealthy`
```bash
# Ver qué está fallando
docker-compose logs nombre-servicio | tail -50

# Reiniciar el servicio problemático
docker-compose restart nombre-servicio
```

### Los emails no llegan a MailHog
1. Verificar que el servicio `notificaciones` está saludable: `curl http://localhost:8085/health`
2. Verificar que los consumers están activos en RabbitMQ: http://localhost:15672 → Queues → columna "Consumers" debe ser ≥ 1 en las queues `notif.*`
3. Si hay mensajes acumulados en las queues con 0 consumidores, reiniciar notificaciones: `docker-compose restart notificaciones`

### Prometheus muestra targets DOWN
Verificar que Prometheus tiene acceso a las redes correctas. Los servicios deben estar en `backend-network` y `observability-network`. Revisar el `docker-compose.yml`.

### Error al validar JWT en el Gateway
El `JWT_SECRET` debe ser idéntico en el servicio de autenticación y en el API Gateway. Verificar las variables de entorno en `docker-compose.yml`.

### En Windows — `curl` no funciona correctamente
PowerShell tiene un alias `curl` que apunta a `Invoke-WebRequest`. Usar siempre `curl.exe` para el curl real, o preferir `Invoke-RestMethod` para requests con body JSON:
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/auth/login" `
  -Method POST -ContentType "application/json" `
  -Body '{"username":"admin@empresa.com","password":"Admin123!"}'
```

---

## Tests por Microservicio

| Servicio | Tests unitarios | Tests integración | Total | Cobertura |
|---|---|---|---|---|
| gestion-empleados | 17 | 17 | 34 | ≥ 70% |
| autenticacion | 21 | 15 | 36 | ≥ 70% |
| gestion-perfiles | 9 | 9 | 18 | ≥ 70% |
| gestion-vacaciones | 14 | 13 | 27 | ≥ 70% |
| notificaciones | 45 | 6 | 51 | 72.86% |
| api-gateway | 34 | 3 | 37 | 89.68% |
| **Total** | **140** | **63** | **203** | **≥ 70% todos** |

**Ejecutar tests de un servicio:**
```bash
# Java
docker-compose run --rm gestion-empleados mvn test

# Python
docker run --rm -v "${PWD}/microservicios/notificaciones:/app" -w /app \
  python:3.12-alpine sh -c "pip install -r requirements.txt -q && pytest --cov=src -q"

# Go
docker run --rm -v "${PWD}/microservicios/gestion-vacaciones:/app" -w /app \
  golang:1.22-alpine go test ./... -cover

# Node.js
docker run --rm -v "${PWD}/microservicios/api-gateway:/app" -w /app \
  node:20-alpine sh -c "npm ci --quiet && npm test -- --coverage"
```