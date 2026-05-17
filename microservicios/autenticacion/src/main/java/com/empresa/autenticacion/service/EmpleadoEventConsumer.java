package com.empresa.autenticacion.service;

import com.empresa.autenticacion.dto.EventoEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class EmpleadoEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EmpleadoEventConsumer.class);

    private final AuthService authService;

    public EmpleadoEventConsumer(AuthService authService) {
        this.authService = authService;
    }

    @RabbitListener(queues = "${app.rabbitmq.queue.auth-empleado-creado}")
    public void consumirEmpleadoCreado(EventoEnvelope evento) {
        log.info("Recibido evento empleado.creado: {}", evento.getEventId());

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) evento.getPayload();
            authService.crearCuentaDesdeEvento(payload);
            log.info("Cuenta creada exitosamente a partir de evento empleado.creado: {}", evento.getEventId());
        } catch (Exception e) {
            log.error("Error procesando evento empleado.creado: {}", e.getMessage(), e);
        }
    }

    @RabbitListener(queues = "${app.rabbitmq.queue.auth-empleado-eliminado}")
    public void consumirEmpleadoEliminado(EventoEnvelope evento) {
        log.info("Recibido evento empleado.eliminado: {}", evento.getEventId());

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) evento.getPayload();
            authService.desactivarCuentaDesdeEvento(payload);
            log.info("Cuenta desactivada exitosamente a partir de evento empleado.eliminado: {}", evento.getEventId());
        } catch (Exception e) {
            log.error("Error procesando evento empleado.eliminado: {}", e.getMessage(), e);
        }
    }

    @RabbitListener(queues = "${app.rabbitmq.queue.auth-vacaciones-programadas}")
    public void consumirVacacionesProgramadas(EventoEnvelope evento) {
        log.info("Recibido evento vacaciones.programadas: {}", evento.getEventId());

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) evento.getPayload();
            authService.manejarVacacionesProgramadas(payload);

            String fechaInicio = (String) payload.get("fechaInicio");
            String fechaFin = (String) payload.get("fechaFin");
            log.info("Vacaciones programadas para empleadoId: {} desde {} hasta {}",
                    payload.get("empleadoId"), fechaInicio, fechaFin);

            // Nota: Para la reactivacion automatica al finalizar vacaciones,
            // se usaria un scheduler que verifique fechas. Por simplicidad,
            // el evento se registra y se maneja manualmente o con un job externo.
        } catch (Exception e) {
            log.error("Error procesando evento vacaciones.programadas: {}", e.getMessage(), e);
        }
    }
}
