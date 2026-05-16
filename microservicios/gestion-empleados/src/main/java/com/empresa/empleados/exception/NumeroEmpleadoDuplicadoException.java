package com.empresa.empleados.exception;

public class NumeroEmpleadoDuplicadoException extends RuntimeException {

    public NumeroEmpleadoDuplicadoException(String numeroEmpleado) {
        super("El número de empleado '" + numeroEmpleado + "' ya está registrado");
    }
}
