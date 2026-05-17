package com.empresa.autenticacion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventoEnvelope {

    private String eventId;
    private String eventType;
    private String timestamp;
    private String source;
    private String version;

    @JsonProperty("payload")
    private Map<String, Object> payload;
}
