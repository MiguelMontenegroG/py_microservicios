package com.empresa.empleados.dto;

import com.empresa.empleados.model.Empleado;
import com.empresa.empleados.model.EstadoEmpleado;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmpleadoEventPayload {

    private UUID empleadoId;
    private String nombre;
    private String apellido;
    private String email;
    private String numeroEmpleado;
    private String cargo;
    private String area;
    private EstadoEmpleado estado;
    private LocalDate fechaIngreso;
    private String fechaRetiro;
    private String motivo;

    public static EmpleadoEventPayload fromEmpleado(Empleado empleado) {
        return EmpleadoEventPayload.builder()
                .empleadoId(empleado.getId())
                .nombre(empleado.getNombre())
                .apellido(empleado.getApellido())
                .email(empleado.getEmail())
                .numeroEmpleado(empleado.getNumeroEmpleado())
                .cargo(empleado.getCargo())
                .area(empleado.getArea())
                .estado(empleado.getEstado())
                .fechaIngreso(empleado.getFechaIngreso())
                .build();
    }
}
