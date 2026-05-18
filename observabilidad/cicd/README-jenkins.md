# Jenkins CI/CD — Guia de Operacion

## Acceso

- URL: http://localhost:9090
- Usuario: admin / Contrasena: admin

## Primer arranque

1. `docker-compose up -d jenkins`
2. Esperar ~2 minutos a que Jenkins instale los plugins
3. Acceder a http://localhost:9090
4. Los 6 pipelines se crean automaticamente via JCasC al arrancar
5. Los triggers SCM se activan en ~5 min o puedes ejecutar manualmente
## Ejecutar un pipeline manualmente

1. Ir al job del microservicio (ej: gestion-empleados)
2. Click en "Build Now"
3. Ver progreso en "Stage View"

## Estructura de jobs

Los 6 pipelines se definen en `jenkins-casc.yml` y se crean al iniciar Jenkins:

- gestion-empleados, autenticacion: Java/Maven (compilar, test, calidad, empaquetar)
- gestion-perfiles, notificaciones: Python/pytest (instalar, test, calidad, empaquetar)
- gestion-vacaciones: Go (mod download, build, test, calidad, empaquetar)
- api-gateway: Node.js/Jest (npm ci, test, calidad, empaquetar)

## Pipeline stages comunes

Todos los pipelines siguen esta estructura:
1. Checkout del repositorio
2. Build/compilacion
3. Test (con reporte JUnit)
4. Quality (cobertura minima 70%)
5. Package (docker build + tag localhost:5050/{imagen}:{BUILD_NUMBER} y latest)
## Credenciales del Registry

- Registry local: localhost:5050
- Las imagenes se tagean como localhost:5050/{servicio}:{BUILD_NUMBER} y localhost:5050/{servicio}:latest
- Actualmente el push no esta habilitado (registry puede no estar disponible)

## Notas

- Si necesitas resetear Jenkins completamente: `docker-compose down jenkins && docker volume rm nombre-proyecto_jenkins-data && docker-compose up -d jenkins`
- No se usa seed-job separado; los pipelines se configuran directamente en JCasC
