# Observabilidad — Stack Completo

Sistema de observabilidad centralizado para monitorear, registrar y alertar sobre el estado de todos los microservicios.

## Stack de Observabilidad

| Herramienta | Propósito | URL | Credenciales |
|---|---|---|---|
| **Elasticsearch** | Almacenamiento y búsqueda de logs | http://localhost:9200 | — |
| **Logstash** | Procesamiento y transformación de logs | — | — |
| **Filebeat** | Recolección de logs desde contenedores Docker | — | — |
| **Kibana** | Visualización y análisis de logs | http://localhost:5601 | — |
| **Prometheus** | Almacenamiento de métricas | http://localhost:9091 | — |
| **Grafana** | Dashboards de monitoreo | http://localhost:3000 | admin / admin |

## Logs Centralizados (ELK)

### Flujo de Datos

```
Microservicio (stdout JSON)
    → Docker (stdout/stderr)
    → Filebeat (recolecta logs de contenedores)
    → Logstash (parsea y transforma)
    → Elasticsearch (indexa y almacena)
    → Kibana (visualiza)
```

### Cómo Crear el Index Pattern en Kibana

1. Ir a http://localhost:5601
2. Ir a **Stack Management → Index Patterns**
3. Click en **Create index pattern**
4. En **Index pattern name** escribir: `microservicios-*`
5. En **Time field** seleccionar: `@timestamp`
6. Click en **Create index pattern**

### Cómo Buscar Errores

En el **Discover** de Kibana, usar la siguiente consulta KQL:

```
level: "ERROR"
```

O si el campo es `severity`:

```
severity: "ERROR"
```

También se puede filtrar por servicio:

```
service: "gestion-empleados" AND level: "ERROR"
```

## Monitoreo (Prometheus + Grafana)

### Prometheus

Configuración de scrape en `monitoreo/prometheus.yml`. Targets configurados:

| Job | Endpoint |
|---|---|
| `api-gateway` | `api-gateway:8080/metrics` |
| `autenticacion` | `autenticacion:8081/actuator/prometheus` |
| `gestion-empleados` | `gestion-empleados:8082/actuator/prometheus` |
| `gestion-perfiles` | `gestion-perfiles:8083/metrics` |
| `gestion-vacaciones` | `gestion-vacaciones:8084/metrics` |
| `notificaciones` | `notificaciones:8085/metrics` |
| `rabbitmq` | `rabbitmq:15692/metrics` |

Para verificar targets en Prometheus:

```
http://localhost:9091/targets
```

### Grafana

Dashboards disponibles:

| Dashboard | Descripción |
|---|---|
| **Microservicios Overview** | Estado general del sistema (UP/DOWN de cada servicio, tasa de requests, latencia) |
| **Logs Dashboard** | Visualización de logs desde Elasticsearch |

Los dashboards se provisionan automáticamente desde la carpeta `dashboards/`.

### Alertas Configuradas

| Alerta | Condición | Severidad |
|---|---|---|
| `ServicioDown` | Servicio sin respuesta por más de 1 minuto | critical |
| `AltaTasaErrores` | Más del 5% de requests con error (5xx) en 5 min | warning |
| `AltaLatencia` | Latencia P95 superior a 2 segundos por 5 min | warning |
| `AltaMemoriaJVM` | Heap de JVM superior al 85% por 5 min | warning |

## Métricas Expuestas por Cada Servicio

| Servicio | Path | Tipo | Descripción |
|---|---|---|---|
| API Gateway | `/metrics` | Prometheus text | Métricas de requests (duración, totales por método/ruta/status) |
| Autenticación | `/actuator/prometheus` | Prometheus (Micrometer) | Métricas JVM, HTTP, base de datos, RabbitMQ |
| Gestión Empleados | `/actuator/prometheus` | Prometheus (Micrometer) | Métricas JVM, HTTP, base de datos, RabbitMQ |
| Gestión Perfiles | `/metrics` | Prometheus (prometheus-fastapi-instrumentator) | Métricas HTTP, tiempo de respuesta, conteo |
| Gestión Vacaciones | `/metrics` | Prometheus (client_golang) | Contadores: `vacaciones_creadas_total`, `vacaciones_errores_total`, HTTP, Go runtime |
| Notificaciones | `/metrics` | Prometheus (prometheus-fastapi-instrumentator) | Métricas HTTP, tiempo de respuesta |
