package com.empresa.empleados.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Solicitud para crear o actualizar un empleado")
public class EmpleadoRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Schema(description = "Nombre del empleado", example = "Juan")
    private String nombre;

    @NotBlank(message = "El apellido es obligatorio")
    @Schema(description = "Apellido del empleado", example = "Pérez")
    private String apellido;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "Formato de email inválido")
    @Schema(description = "Email del empleado", example = "juan.perez@empresa.com")
    private String email;

    @NotBlank(message = "El número de empleado es obligatorio")
    @Schema(description = "Número único de empleado", example = "EMP-001")
    private String numeroEmpleado;

    @NotNull(message = "La fecha de ingreso es obligatoria")
    @PastOrPresent(message = "La fecha de ingreso no puede ser futura")
    @Schema(description = "Fecha de ingreso", example = "2024-01-15")
    private LocalDate fechaIngreso;

    @Schema(description = "Cargo del empleado", example = "Desarrollador Senior")
    private String cargo;

    @Schema(description = "Área del empleado", example = "Tecnología")
    private String area;
}
