package com.empresa.autenticacion.repository;

import com.empresa.autenticacion.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByUsername(String username);

    Optional<Usuario> findByEmpleadoId(UUID empleadoId);

    boolean existsByUsername(String username);

    boolean existsByEmpleadoId(UUID empleadoId);
}
