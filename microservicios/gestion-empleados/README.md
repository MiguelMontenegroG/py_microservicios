# gestion-empleados — Java/Spring Boot

## Imagen Docker base: `bellsoft/liberica-openjre-alpine:21` (runtime)
## Build con: `maven:3.9-eclipse-temurin-21-alpine`

## Puerto: 8082

## Ver instrucciones completas de implementación en:
## `docs/guias/AGENTE_IA_INSTRUCCIONES.md` → sección "MICROSERVICIO 1"

## Estructura esperada:
```
src/main/java/com/empresa/empleados/
├── EmpleadosApplication.java
├── config/
│   ├── RabbitMQConfig.java
│   └── SwaggerConfig.java
├── controller/
│   └── EmpleadoController.java
├── service/
│   ├── EmpleadoService.java
│   └── EmpleadoEventPublisher.java
├── repository/
│   └── EmpleadoRepository.java
├── model/
│   ├── Empleado.java          (entidad JPA)
│   └── EstadoEmpleado.java    (enum: ACTIVO, EN_VACACIONES, RETIRADO)
├── dto/
│   ├── EmpleadoRequest.java
│   ├── EmpleadoResponse.java
│   └── EmpleadoEventPayload.java
└── exception/
    ├── EmpleadoNotFoundException.java
    └── GlobalExceptionHandler.java

src/test/java/com/empresa/empleados/
├── service/
│   └── EmpleadoServiceTest.java
└── controller/
    └── EmpleadoControllerIntegrationTest.java
```
