package com.empresa.autenticacion.service;

import com.empresa.autenticacion.dto.CuentaActivadaEvent;
import com.empresa.autenticacion.dto.CuentaDesactivadaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class CuentaEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(CuentaEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final String cuentasExchange;

    public CuentaEventPublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${app.rabbitmq.exchange.cuentas}") String cuentasExchange) {
        this.rabbitTemplate = rabbitTemplate;
        this.cuentasExchange = cuentasExchange;
    }

    public void publicarCuentaActivada(CuentaActivadaEvent evento) {
        Map<String, Object> envelope = buildEnvelope("cuenta.activada", "autenticacion", Map.of(
                "empleadoId", evento.getEmpleadoId().toString(),
                "email", evento.getEmail(),
                "nombre", evento.getNombre(),
                "passwordTemporal", evento.getPasswordTemporal(),
                "esPrimerAcceso", evento.isEsPrimerAcceso()
        ));

        rabbitTemplate.convertAndSend(cuentasExchange, "cuenta.activada", envelope);
        log.info("Evento cuenta.activada publicado para empleadoId: {}", evento.getEmpleadoId());
    }

    public void publicarCuentaDesactivada(CuentaDesactivadaEvent evento) {
        Map<String, Object> envelope = buildEnvelope("cuenta.desactivada", "autenticacion", Map.of(
                "empleadoId", evento.getEmpleadoId().toString(),
                "email", evento.getEmail(),
                "nombre", evento.getNombre(),
                "motivo", evento.getMotivo(),
                "timestamp", evento.getTimestamp()
        ));

        rabbitTemplate.convertAndSend(cuentasExchange, "cuenta.desactivada", envelope);
        log.info("Evento cuenta.desactivada publicado para empleadoId: {}", evento.getEmpleadoId());
    }

    private Map<String, Object> buildEnvelope(String eventType, String source, Map<String, Object> payload) {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("eventType", eventType);
        envelope.put("timestamp", Instant.now().toString());
        envelope.put("source", source);
        envelope.put("version", "1.0");
        envelope.put("payload", payload);
        return envelope;
    }
}
