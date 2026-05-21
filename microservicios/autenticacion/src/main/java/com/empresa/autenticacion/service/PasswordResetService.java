package com.empresa.autenticacion.service;

import com.empresa.autenticacion.exception.CredencialesInvalidasException;
import com.empresa.autenticacion.exception.UsuarioNotFoundException;
import com.empresa.autenticacion.model.PasswordResetToken;
import com.empresa.autenticacion.model.Usuario;
import com.empresa.autenticacion.repository.AuditLogRepository;
import com.empresa.autenticacion.repository.PasswordResetTokenRepository;
import com.empresa.autenticacion.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final CuentaEventPublisher cuentaEventPublisher;
    private final BCryptPasswordEncoder passwordEncoder;
    private final int tokenTtlMinutes;

    private static final SecureRandom RANDOM = new SecureRandom();

    public PasswordResetService(
            UsuarioRepository usuarioRepository,
            PasswordResetTokenRepository tokenRepository,
            CuentaEventPublisher cuentaEventPublisher,
            @Value("${app.password-reset.token-ttl-minutes:5}") int tokenTtlMinutes,
            @Value("${app.bcrypt.strength:12}") int bcryptStrength) {
        this.usuarioRepository = usuarioRepository;
        this.tokenRepository = tokenRepository;
        this.cuentaEventPublisher = cuentaEventPublisher;
        this.passwordEncoder = new BCryptPasswordEncoder(bcryptStrength);
        this.tokenTtlMinutes = tokenTtlMinutes;
    }

    /**
     * Solicita un codigo de recuperacion de contrasena para el email dado.
     * Si el email existe, genera un codigo de 6 digitos, lo guarda en BD
     * y publica un evento para que notificaciones envie el email.
     */
    @Transactional
    public void solicitarRecuperacion(String email) {
        log.info("Solicitud de recuperacion de contrasena para email: {}", email);

        // Buscar usuario por email (username es el email)
        Usuario usuario = usuarioRepository.findByUsername(email).orElse(null);

        if (usuario == null) {
            // Por seguridad, no revelamos si el email existe o no
            log.warn("Solicitud de recuperacion para email no registrado: {}", email);
            return;
        }

        if (!usuario.getActivo()) {
            log.warn("Solicitud de recuperacion para cuenta desactivada: {}", email);
            return;
        }

        // Invalidar tokens anteriores no utilizados del mismo email
        invalidarTokensPrevios(email);

        // Generar codigo de 6 digitos
        String codigo = generarCodigo();

        // Guardar token en BD
        PasswordResetToken token = new PasswordResetToken(
                usuario.getEmpleadoId(),
                email,
                codigo,
                LocalDateTime.now().plusMinutes(tokenTtlMinutes)
        );
        tokenRepository.save(token);

        log.info("Codigo de recuperacion generado para email: {} (expira en {} min)", email, tokenTtlMinutes);

        // Publicar evento para que notificaciones envie el email
        cuentaEventPublisher.publicarResetSolicitado(
                usuario.getEmpleadoId(),
                email,
                usuario.getUsername(),
                codigo,
                tokenTtlMinutes
        );
    }

    /**
     * Restablece la contrasena usando el codigo de recuperacion.
     */
    @Transactional
    public void restablecerContrasena(String email, String codigo, String newPassword) {
        log.info("Intento de restablecer contrasena para email: {}", email);

        // Buscar token valido
        PasswordResetToken token = tokenRepository
                .findTopByEmailAndCodigoAndUtilizadoFalseOrderByCreatedAtDesc(email, codigo)
                .orElseThrow(() -> new CredencialesInvalidasException("Codigo de recuperacion invalido"));

        if (token.isExpirado()) {
            token.setUtilizado(true);
            tokenRepository.save(token);
            throw new CredencialesInvalidasException("El codigo de recuperacion ha expirado");
        }

        // Marcar token como utilizado
        token.setUtilizado(true);
        tokenRepository.save(token);

        // Buscar usuario y actualizar contrasena
        Usuario usuario = usuarioRepository.findByEmpleadoId(token.getEmpleadoId())
                .orElseThrow(() -> new UsuarioNotFoundException("Usuario no encontrado"));

        usuario.setPasswordHash(passwordEncoder.encode(newPassword));
        usuario.setEsPrimerAcceso(false);
        usuarioRepository.save(usuario);

        log.info("Contrasena restablecida exitosamente para email: {}", email);
    }

    private void invalidarTokensPrevios(String email) {
        var tokensExpirados = tokenRepository.findByEmailAndUtilizadoFalseAndExpiraEnBefore(
                email, LocalDateTime.now());
        for (var t : tokensExpirados) {
            t.setUtilizado(true);
            tokenRepository.save(t);
        }
    }

    private String generarCodigo() {
        int codigo = 100000 + RANDOM.nextInt(900000);
        return String.valueOf(codigo);
    }
}
