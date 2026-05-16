package com.empresa.empleados.dto;

import com.empresa.empleados.model.EstadoEmpleado;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Solicitud para cambiar el estado de un empleado")
public class EstadoRequest {

    @NotNull(message = "El estado es obligatorio")
    @Schema(description = "Nuevo estado del empleado", example = "EN_VACACIONES", allowableValues = {"ACTIVO", "EN_VACACIONES", "RETIRADO"})
    private EstadoEmpleado estado;
}
