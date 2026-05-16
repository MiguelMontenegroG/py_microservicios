package com.empresa.empleados.exception;

import com.empresa.empleados.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EmpleadoNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEmpleadoNotFound(EmpleadoNotFoundException ex) {
        log.warn("Empleado no encontrado: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.builder()
                        .success(false)
                        .error(ErrorResponse.ErrorDetail.builder()
                                .code("EMPLEADO_NO_ENCONTRADO")
                                .message(ex.getMessage())
                                .build())
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    @ExceptionHandler(EmailDuplicadoException.class)
    public ResponseEntity<ErrorResponse> handleEmailDuplicado(EmailDuplicadoException ex) {
        log.warn("Email duplicado: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.builder()
                        .success(false)
                        .error(ErrorResponse.ErrorDetail.builder()
                                .code("EMAIL_DUPLICADO")
                                .message(ex.getMessage())
                                .build())
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    @ExceptionHandler(NumeroEmpleadoDuplicadoException.class)
    public ResponseEntity<ErrorResponse> handleNumeroEmpleadoDuplicado(NumeroEmpleadoDuplicadoException ex) {
        log.warn("Número de empleado duplicado: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.builder()
                        .success(false)
                        .error(ErrorResponse.ErrorDetail.builder()
                                .code("NUMERO_EMPLEADO_DUPLICADO")
                                .message(ex.getMessage())
                                .build())
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    @ExceptionHandler(EstadoInvalidoException.class)
    public ResponseEntity<ErrorResponse> handleEstadoInvalido(EstadoInvalidoException ex) {
        log.warn("Estado inválido: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .success(false)
                        .error(ErrorResponse.ErrorDetail.builder()
                                .code("ESTADO_INVALIDO")
                                .message(ex.getMessage())
                                .build())
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("Error de validación: {}", details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .success(false)
                        .error(ErrorResponse.ErrorDetail.builder()
                                .code("ERROR_VALIDACION")
                                .message("Datos inválidos: " + details)
                                .build())
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Error de lectura del mensaje HTTP: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .success(false)
                        .error(ErrorResponse.ErrorDetail.builder()
                                .code("ERROR_LECTURA_JSON")
                                .message("Formato de solicitud invalido: " + ex.getMostSpecificCause().getMessage())
                                .build())
                        .timestamp(LocalDateTime.now())
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Error interno del servidor", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.builder()
                        .success(false)
                        .error(ErrorResponse.ErrorDetail.builder()
                                .code("ERROR_INTERNO")
                                .message("Error interno del servidor")
                                .build())
                        .timestamp(LocalDateTime.now())
                        .build());
    }
}
