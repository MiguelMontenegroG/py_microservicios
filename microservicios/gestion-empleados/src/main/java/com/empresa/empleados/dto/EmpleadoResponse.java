package com.empresa.empleados.dto;

import com.empresa.empleados.model.Empleado;
import com.empresa.empleados.model.EstadoEmpleado;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Respuesta con datos del empleado")
public class EmpleadoResponse {

    @Schema(description = "ID del empleado")
    private UUID id;

    @Schema(description = "Nombre del empleado")
    private String nombre;

    @Schema(description = "Apellido del empleado")
    private String apellido;

    @Schema(description = "Email del empleado")
    private String email;

    @Schema(description = "Número único de empleado")
    private String numeroEmpleado;

    @Schema(description = "Fecha de ingreso")
    private LocalDate fechaIngreso;

    @Schema(description = "Cargo del empleado")
    private String cargo;

    @Schema(description = "Área del empleado")
    private String area;

    @Schema(description = "Estado del empleado", example = "ACTIVO")
    private EstadoEmpleado estado;

    @Schema(description = "Fecha de creación")
    private LocalDateTime createdAt;

    @Schema(description = "Fecha de última actualización")
    private LocalDateTime updatedAt;

    public static EmpleadoResponse fromEntity(Empleado empleado) {
        return EmpleadoResponse.builder()
                .id(empleado.getId())
                .nombre(empleado.getNombre())
                .apellido(empleado.getApellido())
                .email(empleado.getEmail())
                .numeroEmpleado(empleado.getNumeroEmpleado())
                .fechaIngreso(empleado.getFechaIngreso())
                .cargo(empleado.getCargo())
                .area(empleado.getArea())
                .estado(empleado.getEstado())
                .createdAt(empleado.getCreatedAt())
                .updatedAt(empleado.getUpdatedAt())
                .build();
    }
}
