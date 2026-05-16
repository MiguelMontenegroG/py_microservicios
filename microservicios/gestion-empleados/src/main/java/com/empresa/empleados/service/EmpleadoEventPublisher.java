package com.empresa.empleados.service;

import com.empresa.empleados.dto.EmpleadoEventPayload;
import com.empresa.empleados.model.Empleado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class EmpleadoEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EmpleadoEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange.empleados}")
    private String empleadosExchange;

    public EmpleadoEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publicarCreado(Empleado empleado) {
        var payload = EmpleadoEventPayload.fromEmpleado(empleado);
        var evento = construirEvento("empleado.creado", payload);
        log.info("Publicando evento empleado.creado para empleado: {}", empleado.getId());
        rabbitTemplate.convertAndSend(empleadosExchange, "empleado.creado", evento);
    }

    public void publicarActualizado(Empleado empleado) {
        var payload = EmpleadoEventPayload.fromEmpleado(empleado);
        var evento = construirEvento("empleado.actualizado", payload);
        log.info("Publicando evento empleado.actualizado para empleado: {}", empleado.getId());
        rabbitTemplate.convertAndSend(empleadosExchange, "empleado.actualizado", evento);
    }

    public void publicarEliminado(Empleado empleado) {
        var payload = EmpleadoEventPayload.fromEmpleado(empleado);
        payload.setFechaRetiro(LocalDateTime.now().toString());
        var evento = construirEvento("empleado.eliminado", payload);
        log.info("Publicando evento empleado.eliminado para empleado: {}", empleado.getId());
        rabbitTemplate.convertAndSend(empleadosExchange, "empleado.eliminado", evento);
    }

    private Evento construirEvento(String eventType, EmpleadoEventPayload payload) {
        Evento evento = new Evento();
        evento.setEventId(UUID.randomUUID());
        evento.setEventType(eventType);
        evento.setTimestamp(LocalDateTime.now().toString());
        evento.setSource("gestion-empleados");
        evento.setVersion("1.0");
        evento.setPayload(payload);
        return evento;
    }

    public static class Evento {
        private UUID eventId;
        private String eventType;
        private String timestamp;
        private String source;
        private String version;
        private EmpleadoEventPayload payload;

        public UUID getEventId() { return eventId; }
        public void setEventId(UUID eventId) { this.eventId = eventId; }
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public EmpleadoEventPayload getPayload() { return payload; }
        public void setPayload(EmpleadoEventPayload payload) { this.payload = payload; }
    }
}
