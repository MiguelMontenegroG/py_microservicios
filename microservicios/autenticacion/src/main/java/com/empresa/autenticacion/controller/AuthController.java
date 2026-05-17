package com.empresa.autenticacion.controller;

import com.empresa.autenticacion.dto.ChangePasswordRequest;
import com.empresa.autenticacion.dto.ErrorResponse;
import com.empresa.autenticacion.dto.HealthResponse;
import com.empresa.autenticacion.dto.LoginRequest;
import com.empresa.autenticacion.dto.LoginResponse;
import com.empresa.autenticacion.dto.ValidateTokenRequest;
import com.empresa.autenticacion.dto.ValidateTokenResponse;
import com.empresa.autenticacion.model.Usuario;
import com.empresa.autenticacion.repository.UsuarioRepository;
import com.empresa.autenticacion.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping
@Tag(name = "Autenticacion", description = "Endpoints de autenticacion y gestion de cuentas")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final DataSource dataSource;
    private final UsuarioRepository usuarioRepository;

    public AuthController(AuthService authService, DataSource dataSource, UsuarioRepository usuarioRepository) {
        this.authService = authService;
        this.dataSource = dataSource;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping("/health")
    @Operation(summary = "Health check del servicio")
    public ResponseEntity<HealthResponse> health() {
        Map<String, String> dependencies = new HashMap<>();

        // Verificar base de datos
        try {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.queryForObject("SELECT 1", Integer.class);
            dependencies.put("database", "UP");
        } catch (Exception e) {
            dependencies.put("database", "DOWN");
        }

        // RabbitMQ se verifica via Actuator, aqui solo indicamos
        dependencies.put("rabbitmq", "UP (ver actuator)");

        boolean allUp = dependencies.values().stream().allMatch(v -> v.startsWith("UP"));

        HealthResponse response = HealthResponse.builder()
                .status(allUp ? "UP" : "DEGRADED")
                .service("autenticacion")
                .version("1.0.0")
                .timestamp(LocalDateTime.now())
                .dependencies(dependencies)
                .build();

        return ResponseEntity.status(allUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @PostMapping("/auth/login")
    @Operation(summary = "Iniciar sesion", description = "Autentica un usuario y devuelve un token JWT")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login exitoso"),
        @ApiResponse(responseCode = "401", description = "Credenciales invalidas"),
        @ApiResponse(responseCode = "403", description = "Cuenta desactivada")
    })
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request.getUsername(), request.getPassword());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/auth/change-password")
    @Operation(summary = "Cambiar contrasena", description = "Cambia la contrasena del usuario autenticado")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Contrasena cambiada exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos invalidos"),
        @ApiResponse(responseCode = "401", description = "Contrasena actual incorrecta")
    })
    public ResponseEntity<Map<String, Object>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {

        UUID empleadoId = (UUID) httpRequest.getAttribute("empleadoId");
        authService.changePassword(empleadoId, request.getCurrentPassword(), request.getNewPassword());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Contrasena cambiada exitosamente");
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/auth/validate")
    @Operation(summary = "Validar token JWT", description = "Valida un token JWT y devuelve la informacion del usuario (uso interno entre servicios)")
    public ResponseEntity<ValidateTokenResponse> validateToken(@Valid @RequestBody ValidateTokenRequest request) {
        ValidateTokenResponse response = authService.validateToken(request.getToken());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/accounts/{empleadoId}/activate")
    @Operation(summary = "Activar cuenta", description = "Activa la cuenta de un empleado")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Cuenta activada exitosamente"),
        @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    public ResponseEntity<Map<String, Object>> activateAccount(@PathVariable UUID empleadoId) {
        authService.activarCuenta(empleadoId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Cuenta activada exitosamente");
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.ok(response);
    }

    @PutMapping("/accounts/{empleadoId}/deactivate")
    @Operation(summary = "Desactivar cuenta", description = "Desactiva la cuenta de un empleado")
    public ResponseEntity<Map<String, Object>> deactivateAccount(@PathVariable UUID empleadoId) {
        authService.desactivarCuenta(empleadoId, "MANUAL");

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Cuenta desactivada exitosamente");
        response.put("timestamp", LocalDateTime.now().toString());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/auth/seed")
    @Operation(summary = "SEMILLA - Crear usuario admin de prueba (solo desarrollo)")
    public ResponseEntity<Map<String, Object>> seedAdmin() {
        if (usuarioRepository.existsByUsername("admin@empresa.com")) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "El usuario admin@empresa.com ya existe");
            return ResponseEntity.ok(response);
        }

        UUID empleadoId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        String passwordPlano = "Admin123!";
        String hash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12).encode(passwordPlano);

        Usuario admin = Usuario.builder()
                .empleadoId(empleadoId)
                .username("admin@empresa.com")
                .passwordHash(hash)
                .activo(true)
                .rol("ADMIN")
                .esPrimerAcceso(false)
                .build();

        usuarioRepository.save(admin);

        log.info("Usuario admin creado: admin@empresa.com / {}", passwordPlano);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Usuario admin creado exitosamente");
        response.put("username", "admin@empresa.com");
        response.put("password", passwordPlano);
        response.put("rol", "ADMIN");
        return ResponseEntity.ok(response);
    }
}
