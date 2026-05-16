package com.empresa.empleados.repository;

import com.empresa.empleados.model.Empleado;
import com.empresa.empleados.model.EstadoEmpleado;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmpleadoRepository extends JpaRepository<Empleado, UUID> {

    Page<Empleado> findByEstadoNot(EstadoEmpleado estado, Pageable pageable);

    Optional<Empleado> findByIdAndEstadoNot(UUID id, EstadoEmpleado estado);

    boolean existsByEmail(String email);

    boolean existsByNumeroEmpleado(String numeroEmpleado);
}
