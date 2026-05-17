package com.empresa.autenticacion.service;

import com.empresa.autenticacion.dto.CuentaActivadaEvent;
import com.empresa.autenticacion.dto.CuentaDesactivadaEvent;
import com.empresa.autenticacion.dto.LoginResponse;
import com.empresa.autenticacion.dto.ValidateTokenResponse;
import com.empresa.autenticacion.exception.CredencialesInvalidasException;
import com.empresa.autenticacion.exception.CuentaDesactivadaException;
import com.empresa.autenticacion.exception.UsuarioNotFoundException;
import com.empresa.autenticacion.model.AuditLog;
import com.empresa.autenticacion.model.Usuario;
import com.empresa.autenticacion.repository.AuditLogRepository;
import com.empresa.autenticacion.repository.UsuarioRepository;
import io.jsonwebtoken.Claims;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UsuarioRepository usuarioRepository;
    private final AuditLogRepository auditLogRepository;
    private final JwtService jwtService;
    private final CuentaEventPublisher cuentaEventPublisher;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthService(
            UsuarioRepository usuarioRepository,
            AuditLogRepository auditLogRepository,
            JwtService jwtService,
            CuentaEventPublisher cuentaEventPublisher,
            @Value("${app.bcrypt.strength}") int bcryptStrength) {
        this.usuarioRepository = usuarioRepository;
        this.auditLogRepository = auditLogRepository;
        this.jwtService = jwtService;
        this.cuentaEventPublisher = cuentaEventPublisher;
        this.passwordEncoder = new BCryptPasswordEncoder(bcryptStrength);
    }

    public LoginResponse login(String username, String password) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new CredencialesInvalidasException("Usuario o contrasena incorrectos"));

        if (!usuario.getActivo()) {
            throw new CuentaDesactivadaException("La cuenta esta desactivada");
        }

        if (!passwordEncoder.matches(password, usuario.getPasswordHash())) {
            throw new CredencialesInvalidasException("Usuario o contrasena incorrectos");
        }

        String token = jwtService.generateToken(usuario.getEmpleadoId(), usuario.getUsername(), usuario.getRol());

        registrarAudit(usuario.getEmpleadoId(), "LOGIN", "Inicio de sesion exitoso");

        log.info("Login exitoso para usuario: {}", usuario.getUsername());

        return LoginResponse.builder()
                .token(token)
                .expiresIn(jwtService.getExpirationMs())
                .rol(usuario.getRol())
                .esPrimerAcceso(usuario.getEsPrimerAcceso())
                .build();
    }

    @Transactional
    public void changePassword(UUID empleadoId, String currentPassword, String newPassword) {
        Usuario usuario = usuarioRepository.findByEmpleadoId(empleadoId)
                .orElseThrow(() -> new UsuarioNotFoundException("Usuario no encontrado con empleadoId: " + empleadoId));

        if (!passwordEncoder.matches(currentPassword, usuario.getPasswordHash())) {
            throw new CredencialesInvalidasException("La contrasena actual no es correcta");
        }

        usuario.setPasswordHash(passwordEncoder.encode(newPassword));
        usuario.setEsPrimerAcceso(false);
        usuarioRepository.save(usuario);

        registrarAudit(empleadoId, "CAMBIO_PASSWORD", "Cambio de contrasena exitoso");

        log.info("Password cambiada exitosamente para empleadoId: {}", empleadoId);
    }

    public ValidateTokenResponse validateToken(String token) {
        try {
            Claims claims = jwtService.validateToken(token);
            UUID empleadoId = UUID.fromString(claims.getSubject());
            String username = claims.get("username", String.class);
            String rol = claims.get("rol", String.class);

            return ValidateTokenResponse.builder()
                    .valid(true)
                    .empleadoId(empleadoId)
                    .rol(rol)
                    .username(username)
                    .build();
        } catch (Exception e) {
            log.warn("Validacion de token fallida: {}", e.getMessage());
            return ValidateTokenResponse.builder()
                    .valid(false)
                    .build();
        }
    }

    @Transactional
    public Usuario crearCuentaDesdeEvento(Map<String, Object> payload) {
        UUID empleadoId = UUID.fromString((String) payload.get("empleadoId"));
        String email = (String) payload.get("email");
        String nombre = (String) payload.get("nombre");
        String apellido = (String) payload.get("apellido");

        if (usuarioRepository.existsByEmpleadoId(empleadoId)) {
            log.warn("La cuenta para empleadoId {} ya existe, omitiendo", empleadoId);
            return usuarioRepository.findByEmpleadoId(empleadoId).orElseThrow();
        }

        String passwordPlano = RandomStringUtils.randomAlphanumeric(10);
        String hash = passwordEncoder.encode(passwordPlano);

        Usuario usuario = Usuario.builder()
                .empleadoId(empleadoId)
                .username(email)
                .passwordHash(hash)
                .activo(true)
                .rol("EMPLEADO")
                .esPrimerAcceso(true)
                .build();

        usuario = usuarioRepository.save(usuario);

        registrarAudit(empleadoId, "CUENTA_CREADA", "Cuenta creada a partir de evento empleado.creado");

        // Publicar evento cuenta.activada con la password temporal
        CuentaActivadaEvent evento = CuentaActivadaEvent.builder()
                .empleadoId(empleadoId)
                .email(email)
                .nombre(nombre + " " + apellido)
                .passwordTemporal(passwordPlano)
                .esPrimerAcceso(true)
                .build();

        cuentaEventPublisher.publicarCuentaActivada(evento);

        log.info("Cuenta creada para empleadoId: {}, email: {}", empleadoId, email);

        return usuario;
    }

    @Transactional
    public void desactivarCuentaDesdeEvento(Map<String, Object> payload) {
        UUID empleadoId = UUID.fromString((String) payload.get("empleadoId"));
        String email = (String) payload.get("email");
        String nombre = (String) payload.get("nombre");

        Usuario usuario = usuarioRepository.findByEmpleadoId(empleadoId)
                .orElseThrow(() -> new UsuarioNotFoundException("Usuario no encontrado con empleadoId: " + empleadoId));

        usuario.setActivo(false);
        usuarioRepository.save(usuario);

        registrarAudit(empleadoId, "CUENTA_DESACTIVADA", "Cuenta desactivada por retiro de empleado");

        CuentaDesactivadaEvent evento = CuentaDesactivadaEvent.builder()
                .empleadoId(empleadoId)
                .email(email)
                .nombre(nombre)
                .motivo("RETIRO")
                .timestamp(java.time.Instant.now().toString())
                .build();

        cuentaEventPublisher.publicarCuentaDesactivada(evento);

        log.info("Cuenta desactivada para empleadoId: {}, email: {}", empleadoId, email);
    }

    @Transactional
    public void activarCuenta(UUID empleadoId) {
        Usuario usuario = usuarioRepository.findByEmpleadoId(empleadoId)
                .orElseThrow(() -> new UsuarioNotFoundException("Usuario no encontrado con empleadoId: " + empleadoId));

        usuario.setActivo(true);
        usuarioRepository.save(usuario);

        registrarAudit(empleadoId, "CUENTA_ACTIVADA", "Cuenta activada manualmente");

        CuentaActivadaEvent evento = CuentaActivadaEvent.builder()
                .empleadoId(empleadoId)
                .email(usuario.getUsername())
                .nombre("")
                .passwordTemporal("")
                .esPrimerAcceso(false)
                .build();

        cuentaEventPublisher.publicarCuentaActivada(evento);

        log.info("Cuenta activada para empleadoId: {}", empleadoId);
    }

    @Transactional
    public void desactivarCuenta(UUID empleadoId, String motivo) {
        Usuario usuario = usuarioRepository.findByEmpleadoId(empleadoId)
                .orElseThrow(() -> new UsuarioNotFoundException("Usuario no encontrado con empleadoId: " + empleadoId));

        usuario.setActivo(false);
        usuarioRepository.save(usuario);

        registrarAudit(empleadoId, "CUENTA_DESACTIVADA", "Cuenta desactivada. Motivo: " + motivo);

        CuentaDesactivadaEvent evento = CuentaDesactivadaEvent.builder()
                .empleadoId(empleadoId)
                .email(usuario.getUsername())
                .nombre("")
                .motivo(motivo)
                .timestamp(java.time.Instant.now().toString())
                .build();

        cuentaEventPublisher.publicarCuentaDesactivada(evento);

        log.info("Cuenta desactivada para empleadoId: {}, motivo: {}", empleadoId, motivo);
    }

    @Transactional
    public void manejarVacacionesProgramadas(Map<String, Object> payload) {
        UUID empleadoId = UUID.fromString((String) payload.get("empleadoId"));

        // Desactivar cuenta de forma temporal (se activara al finalizar vacaciones)
        desactivarCuenta(empleadoId, "VACACIONES");

        log.info("Cuenta desactivada por vacaciones para empleadoId: {}", empleadoId);
    }

    private void registrarAudit(UUID empleadoId, String accion, String detalle) {
        AuditLog auditLog = AuditLog.builder()
                .empleadoId(empleadoId)
                .accion(accion)
                .detalle(detalle)
                .build();
        auditLogRepository.save(auditLog);
    }
}
