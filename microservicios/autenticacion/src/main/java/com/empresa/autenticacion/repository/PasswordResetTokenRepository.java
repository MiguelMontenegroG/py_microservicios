package com.empresa.autenticacion.repository;

import com.empresa.autenticacion.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findTopByEmailAndCodigoAndUtilizadoFalseOrderByCreatedAtDesc(
            String email, String codigo);

    List<PasswordResetToken> findByEmailAndUtilizadoFalseAndExpiraEnBefore(
            String email, LocalDateTime now);

    List<PasswordResetToken> findByExpiraEnBefore(LocalDateTime now);
}
