package com.empresa.autenticacion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CuentaActivadaEvent {

    private UUID empleadoId;
    private String email;
    private String nombre;
    private String passwordTemporal;
    private boolean esPrimerAcceso;
}
