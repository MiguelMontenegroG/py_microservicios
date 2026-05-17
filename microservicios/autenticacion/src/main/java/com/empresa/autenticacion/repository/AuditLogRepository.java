package com.empresa.autenticacion.repository;

import com.empresa.autenticacion.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByEmpleadoIdOrderByTimestampDesc(UUID empleadoId);
}
