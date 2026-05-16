package com.empresa.empleados.service;

import com.empresa.empleados.dto.EmpleadoRequest;
import com.empresa.empleados.dto.EmpleadoResponse;
import com.empresa.empleados.exception.EmpleadoNotFoundException;
import com.empresa.empleados.exception.EstadoInvalidoException;
import com.empresa.empleados.exception.EmailDuplicadoException;
import com.empresa.empleados.exception.NumeroEmpleadoDuplicadoException;
import com.empresa.empleados.model.Empleado;
import com.empresa.empleados.model.EstadoEmpleado;
import com.empresa.empleados.repository.EmpleadoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class EmpleadoService {

    private static final Logger log = LoggerFactory.getLogger(EmpleadoService.class);

    private final EmpleadoRepository empleadoRepository;
    private final EmpleadoEventPublisher eventPublisher;

    public EmpleadoService(EmpleadoRepository empleadoRepository, EmpleadoEventPublisher eventPublisher) {
        this.empleadoRepository = empleadoRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public Page<EmpleadoResponse> listarEmpleados(Pageable pageable) {
        log.debug("Listando empleados con paginación: page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());
        return empleadoRepository.findByEstadoNot(EstadoEmpleado.RETIRADO, pageable)
                .map(EmpleadoResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public EmpleadoResponse obtenerEmpleado(UUID id) {
        log.debug("Obteniendo empleado por id: {}", id);
        Empleado empleado = empleadoRepository.findByIdAndEstadoNot(id, EstadoEmpleado.RETIRADO)
                .orElseThrow(() -> new EmpleadoNotFoundException(id));
        return EmpleadoResponse.fromEntity(empleado);
    }

    @Transactional
    public EmpleadoResponse crearEmpleado(EmpleadoRequest request) {
        log.info("Creando empleado con email: {}", request.getEmail());

        if (empleadoRepository.existsByEmail(request.getEmail())) {
            throw new EmailDuplicadoException(request.getEmail());
        }
        if (empleadoRepository.existsByNumeroEmpleado(request.getNumeroEmpleado())) {
            throw new NumeroEmpleadoDuplicadoException(request.getNumeroEmpleado());
        }

        Empleado empleado = Empleado.builder()
                .nombre(request.getNombre())
                .apellido(request.getApellido())
                .email(request.getEmail())
                .numeroEmpleado(request.getNumeroEmpleado())
                .fechaIngreso(request.getFechaIngreso())
                .cargo(request.getCargo())
                .area(request.getArea())
                .estado(EstadoEmpleado.ACTIVO)
                .build();

        empleado = empleadoRepository.save(empleado);
        log.info("Empleado creado con id: {}", empleado.getId());

        eventPublisher.publicarCreado(empleado);
        log.debug("Evento empleado.creado publicado para empleado: {}", empleado.getId());

        return EmpleadoResponse.fromEntity(empleado);
    }

    @Transactional
    public EmpleadoResponse actualizarEmpleado(UUID id, EmpleadoRequest request) {
        log.info("Actualizando empleado con id: {}", id);

        Empleado empleado = empleadoRepository.findByIdAndEstadoNot(id, EstadoEmpleado.RETIRADO)
                .orElseThrow(() -> new EmpleadoNotFoundException(id));

        if (!empleado.getEmail().equals(request.getEmail())
                && empleadoRepository.existsByEmail(request.getEmail())) {
            throw new EmailDuplicadoException(request.getEmail());
        }
        if (!empleado.getNumeroEmpleado().equals(request.getNumeroEmpleado())
                && empleadoRepository.existsByNumeroEmpleado(request.getNumeroEmpleado())) {
            throw new NumeroEmpleadoDuplicadoException(request.getNumeroEmpleado());
        }

        empleado.setNombre(request.getNombre());
        empleado.setApellido(request.getApellido());
        empleado.setEmail(request.getEmail());
        empleado.setNumeroEmpleado(request.getNumeroEmpleado());
        empleado.setFechaIngreso(request.getFechaIngreso());
        empleado.setCargo(request.getCargo());
        empleado.setArea(request.getArea());

        empleado = empleadoRepository.save(empleado);
        log.info("Empleado actualizado: {}", empleado.getId());

        eventPublisher.publicarActualizado(empleado);

        return EmpleadoResponse.fromEntity(empleado);
    }

    @Transactional
    public void eliminarEmpleado(UUID id) {
        log.info("Eliminando (soft delete) empleado con id: {}", id);

        Empleado empleado = empleadoRepository.findByIdAndEstadoNot(id, EstadoEmpleado.RETIRADO)
                .orElseThrow(() -> new EmpleadoNotFoundException(id));

        empleado.setEstado(EstadoEmpleado.RETIRADO);
        empleadoRepository.save(empleado);
        log.info("Empleado marcado como RETIRADO: {}", id);

        eventPublisher.publicarEliminado(empleado);
    }

    @Transactional(readOnly = true)
    public EstadoEmpleado obtenerEstado(UUID id) {
        Empleado empleado = empleadoRepository.findByIdAndEstadoNot(id, EstadoEmpleado.RETIRADO)
                .orElseThrow(() -> new EmpleadoNotFoundException(id));
        return empleado.getEstado();
    }

    @Transactional
    public EmpleadoResponse cambiarEstado(UUID id, EstadoEmpleado nuevoEstado) {
        log.info("Cambiando estado de empleado {} a {}", id, nuevoEstado);

        Empleado empleado = empleadoRepository.findByIdAndEstadoNot(id, EstadoEmpleado.RETIRADO)
                .orElseThrow(() -> new EmpleadoNotFoundException(id));

        if (empleado.getEstado() == EstadoEmpleado.RETIRADO) {
            throw new EstadoInvalidoException("No se puede cambiar el estado de un empleado retirado");
        }

        if (nuevoEstado == EstadoEmpleado.RETIRADO) {
            empleado.setEstado(EstadoEmpleado.RETIRADO);
            empleado = empleadoRepository.save(empleado);
            eventPublisher.publicarEliminado(empleado);
        } else {
            empleado.setEstado(nuevoEstado);
            empleado = empleadoRepository.save(empleado);
            eventPublisher.publicarActualizado(empleado);
        }

        return EmpleadoResponse.fromEntity(empleado);
    }
}
