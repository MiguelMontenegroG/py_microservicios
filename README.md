# Sistema de Gestión de Empleados — Arquitectura de Microservicios

## Descripción General

Sistema empresarial para automatizar el ciclo de vida del empleado: **onboarding, gestión de perfil, vacaciones y offboarding**. Basado en arquitectura de microservicios con comunicación asíncrona mediante message broker.

---

## Arquitectura de Alto Nivel

```
[Cliente] → [API Gateway :8080]
               ↓ REST
   ┌───────────┬────────────┬────────────┬────────────┬─────────────┐
   │Auth :8081 │Empleados   │Perfiles    │Vacaciones  │Notificaciones│
   │           │:8082       │:8083       │:8084       │:8085        │
   └───────────┴────────────┴────────────┴────────────┴─────────────┘
               ↓ Eventos asíncronos
          [RabbitMQ :5672 / UI :15672]
               ↓
   ┌───────────────────────────────────────────────┐
   │ ELK Stack + Prometheus + Grafana (Observabilidad)│
   └───────────────────────────────────────────────┘
```

---

## Stack Tecnológico — 4 Lenguajes Obligatorios

| Microservicio        | Lenguaje         | Framework      | Base de Datos      | Puerto |
|---------------------|------------------|----------------|--------------------|--------|
| API Gateway          | **Node.js**      | Express        | —                  | 8080   |
| Autenticación        | **Java**         | Spring Boot    | PostgreSQL         | 8081   |
| Gestión de Empleados | **Java**         | Spring Boot    | PostgreSQL         | 8082   |
| Gestión de Perfiles  | **Python**       | FastAPI        | MongoDB            | 8083   |
| Gestión de Vacaciones| **Go**           | Gin            | PostgreSQL         | 8084   |
| Notificaciones       | **Python**       | FastAPI        | —                  | 8085   |

> ✅ Cumple la restricción de **al menos 4 lenguajes**: Java, Node.js, Python, Go

---

## Message Broker — Eventos del Sistema

| Evento                  | Publicado por       | Consumido por                   |
|------------------------|---------------------|---------------------------------|
| `empleado.creado`       | gestion-empleados   | autenticacion, notificaciones   |
| `empleado.actualizado`  | gestion-empleados   | gestion-perfiles                |
| `empleado.eliminado`    | gestion-empleados   | autenticacion, gestion-perfiles |
| `vacaciones.programadas`| gestion-vacaciones  | autenticacion, notificaciones   |
| `cuenta.activada`       | autenticacion       | notificaciones                  |
| `cuenta.desactivada`    | autenticacion       | notificaciones                  |

---

## Requisitos Previos

- Docker >= 24.x
- Docker Compose >= 2.x
- (Opcional) IntelliJ IDEA para desarrollo Java

---

## Cómo Levantar el Sistema

```bash
# Descomprimir y entrar al directorio
cd proyecto-final-microservicios

# Levantar todo el sistema (primera vez tarda ~3-5 min)
docker-compose up --build

# Levantar en background
docker-compose up --build -d

# Detener todo
docker-compose down

# Limpiar volúmenes (reset total)
docker-compose down -v
```

## URLs de Acceso

| Servicio         | URL                              | Credenciales por defecto |
|-----------------|----------------------------------|--------------------------|
| API Gateway      | http://localhost:8080            | —                        |
| Swagger UI       | http://localhost:8080/api-docs   | —                        |
| RabbitMQ UI      | http://localhost:15672           | guest / guest            |
| Kibana (logs)    | http://localhost:5601            | —                        |
| Grafana          | http://localhost:3000            | admin / admin            |
| Jenkins          | http://localhost:9090            | admin / admin            |
| Prometheus       | http://localhost:9091            | —                        |

---

## Endpoints del API Gateway

### Autenticación
```
POST /auth/login              → Login, retorna JWT
POST /auth/change-password    → Cambiar contraseña (requiere JWT)
```

### Empleados (requiere JWT de RRHH/admin)
```
GET    /employees             → Listar todos
POST   /employees             → Crear empleado (dispara onboarding automático)
GET    /employees/{id}        → Empleado completo (datos + perfil)
PUT    /employees/{id}        → Actualizar empleado
DELETE /employees/{id}        → Eliminar empleado (offboarding)
```

### Perfil (requiere JWT del empleado autenticado)
```
GET  /profile                 → Ver mi perfil
PUT  /profile                 → Actualizar mi perfil
```

### Vacaciones (requiere JWT de RRHH/admin)
```
POST /vacations               → Programar vacaciones
GET  /vacations               → Consultar vacaciones
```

---

## Flujos de Negocio Críticos

### 1. Onboarding
```
RRHH: POST /employees
  → gestion-empleados guarda empleado con estado ACTIVO
  → publica evento: empleado.creado
  → autenticacion consume → crea usuario/contraseña → publica cuenta.activada
  → notificaciones consume empleado.creado → envía email con credenciales
```

### 2. Vacaciones
```
RRHH: POST /vacations
  → gestion-vacaciones guarda período
  → publica evento: vacaciones.programadas
  → autenticacion consume → desactiva cuenta en fecha inicio, reactiva en fecha fin
  → notificaciones consume → envía email de confirmación
```

### 3. Offboarding
```
RRHH: DELETE /employees/{id}  o  PUT /employees/{id} con estado=RETIRADO
  → gestion-empleados publica evento: empleado.eliminado
  → autenticacion consume → desactiva permanentemente → publica cuenta.desactivada
  → gestion-perfiles consume → archiva perfil
  → notificaciones consume cuenta.desactivada → envía notificación de salida
```

---

## Estructura del Proyecto

Ver archivo `docs/arquitectura.md` para diagramas detallados.

```
proyecto-final-microservicios/
├── README.md
├── docker-compose.yml
├── microservicios/
│   ├── api-gateway/          (Node.js)
│   ├── autenticacion/        (Java/Spring Boot)
│   ├── gestion-empleados/    (Java/Spring Boot)
│   ├── gestion-perfiles/     (Python/FastAPI)
│   ├── gestion-vacaciones/   (Go/Gin)
│   └── notificaciones/       (Python/FastAPI)
├── observabilidad/
│   ├── logs/                 (ELK Stack config)
│   ├── monitoreo/            (Prometheus config)
│   └── dashboards/           (Grafana dashboards)
├── docs/
│   ├── arquitectura.md
│   ├── eventos.md
│   └── guias/
└── scripts/
    ├── init-db.sql
    └── seed-data.sql
```
