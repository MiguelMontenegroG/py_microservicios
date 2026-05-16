package com.empresa.empleados.exception;

import java.util.UUID;

public class EmpleadoNotFoundException extends RuntimeException {

    public EmpleadoNotFoundException(UUID id) {
        super("El empleado con id " + id + " no existe");
    }

    public EmpleadoNotFoundException(String message) {
        super(message);
    }
}
