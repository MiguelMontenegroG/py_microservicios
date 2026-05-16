package com.empresa.empleados.controller;

import com.empresa.empleados.dto.EmpleadoRequest;
import com.empresa.empleados.dto.EmpleadoResponse;
import com.empresa.empleados.dto.EstadoRequest;
import com.empresa.empleados.model.EstadoEmpleado;
import com.empresa.empleados.service.EmpleadoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping
@Tag(name = "Empleados", description = "API de gestión de empleados")
public class EmpleadoController {

    private final EmpleadoService empleadoService;

    public EmpleadoController(EmpleadoService empleadoService) {
        this.empleadoService = empleadoService;
    }

    @Operation(summary = "Health Check", description = "Verifica el estado del servicio")
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "gestion-empleados",
                "version", "1.0.0",
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    @Operation(summary = "Listar empleados", description = "Devuelve una lista paginada de empleados activos")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de empleados obtenida correctamente")
    })
    @GetMapping("/employees")
    public ResponseEntity<Page<EmpleadoResponse>> listarEmpleados(
            @Parameter(description = "Número de página (desde 0)")
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(empleadoService.listarEmpleados(pageable));
    }

    @Operation(summary = "Obtener empleado por ID", description = "Devuelve los datos de un empleado específico")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Empleado encontrado"),
            @ApiResponse(responseCode = "404", description = "Empleado no encontrado", content = @Content)
    })
    @GetMapping("/employees/{id}")
    public ResponseEntity<EmpleadoResponse> obtenerEmpleado(
            @Parameter(description = "ID del empleado")
            @PathVariable UUID id) {
        return ResponseEntity.ok(empleadoService.obtenerEmpleado(id));
    }

    @Operation(summary = "Crear empleado", description = "Crea un nuevo empleado y publica el evento correspondiente")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Empleado creado correctamente"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content),
            @ApiResponse(responseCode = "409", description = "Conflicto (email o número de empleado duplicado)", content = @Content)
    })
    @PostMapping("/employees")
    public ResponseEntity<EmpleadoResponse> crearEmpleado(
            @Valid @RequestBody EmpleadoRequest request) {
        EmpleadoResponse response = empleadoService.crearEmpleado(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Actualizar empleado", description = "Actualiza los datos de un empleado existente y publica el evento")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Empleado actualizado correctamente"),
            @ApiResponse(responseCode = "404", description = "Empleado no encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Conflicto (email o número de empleado duplicado)", content = @Content)
    })
    @PutMapping("/employees/{id}")
    public ResponseEntity<EmpleadoResponse> actualizarEmpleado(
            @Parameter(description = "ID del empleado")
            @PathVariable UUID id,
            @Valid @RequestBody EmpleadoRequest request) {
        return ResponseEntity.ok(empleadoService.actualizarEmpleado(id, request));
    }

    @Operation(summary = "Eliminar empleado (soft delete)", description = "Marca un empleado como RETIRADO y publica el evento")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Empleado eliminado correctamente"),
            @ApiResponse(responseCode = "404", description = "Empleado no encontrado", content = @Content)
    })
    @DeleteMapping("/employees/{id}")
    public ResponseEntity<Void> eliminarEmpleado(
            @Parameter(description = "ID del empleado")
            @PathVariable UUID id) {
        empleadoService.eliminarEmpleado(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Obtener estado de empleado", description = "Devuelve solo el estado actual del empleado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado obtenido correctamente"),
            @ApiResponse(responseCode = "404", description = "Empleado no encontrado", content = @Content)
    })
    @GetMapping("/employees/{id}/status")
    public ResponseEntity<Map<String, EstadoEmpleado>> obtenerEstado(
            @Parameter(description = "ID del empleado")
            @PathVariable UUID id) {
        EstadoEmpleado estado = empleadoService.obtenerEstado(id);
        return ResponseEntity.ok(Map.of("estado", estado));
    }

    @Operation(summary = "Cambiar estado de empleado", description = "Cambia el estado de un empleado y publica el evento correspondiente")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estado actualizado correctamente"),
            @ApiResponse(responseCode = "400", description = "Estado inválido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Empleado no encontrado", content = @Content)
    })
    @PutMapping("/employees/{id}/status")
    public ResponseEntity<EmpleadoResponse> cambiarEstado(
            @Parameter(description = "ID del empleado")
            @PathVariable UUID id,
            @Valid @RequestBody EstadoRequest request) {
        return ResponseEntity.ok(empleadoService.cambiarEstado(id, request.getEstado()));
    }
}
