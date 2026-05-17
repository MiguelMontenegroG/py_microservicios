package com.empresa.autenticacion.service;

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
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private CuentaEventPublisher cuentaEventPublisher;

    @Mock
    private JwtService jwtService;

    @Captor
    private ArgumentCaptor<Usuario> usuarioCaptor;

    @Captor
    private ArgumentCaptor<AuditLog> auditLogCaptor;

    private AuthService authService;
    private BCryptPasswordEncoder passwordEncoder;
    private UUID empleadoId;
    private Usuario usuarioActivo;
    private Usuario usuarioInactivo;
    private String passwordPlano;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(12);
        authService = new AuthService(usuarioRepository, auditLogRepository, jwtService,
                cuentaEventPublisher, 12);

        empleadoId = UUID.randomUUID();
        passwordPlano = "password123";

        usuarioActivo = Usuario.builder()
                .id(UUID.randomUUID())
                .empleadoId(empleadoId)
                .username("test@empresa.com")
                .passwordHash(passwordEncoder.encode(passwordPlano))
                .activo(true)
                .rol("EMPLEADO")
                .esPrimerAcceso(true)
                .build();

        usuarioInactivo = Usuario.builder()
                .id(UUID.randomUUID())
                .empleadoId(empleadoId)
                .username("inactivo@empresa.com")
                .passwordHash(passwordEncoder.encode(passwordPlano))
                .activo(false)
                .rol("EMPLEADO")
                .esPrimerAcceso(false)
                .build();
    }

    @Nested
    @DisplayName("Login")
    class LoginTests {

        @Test
        @DisplayName("Login exitoso con credenciales correctas")
        void loginExitoso() {
            when(usuarioRepository.findByUsername("test@empresa.com"))
                    .thenReturn(Optional.of(usuarioActivo));
            when(jwtService.generateToken(empleadoId, "test@empresa.com", "EMPLEADO"))
                    .thenReturn("token-jwt");
            when(jwtService.getExpirationMs()).thenReturn(86400000L);

            LoginResponse response = authService.login("test@empresa.com", passwordPlano);

            assertNotNull(response);
            assertEquals("token-jwt", response.getToken());
            assertEquals(86400000L, response.getExpiresIn());
            assertEquals("EMPLEADO", response.getRol());
            assertTrue(response.isEsPrimerAcceso());

            verify(auditLogRepository).save(any(AuditLog.class));
        }

        @Test
        @DisplayName("Login falla con username incorrecto")
        void loginUsuarioNoEncontrado() {
            when(usuarioRepository.findByUsername("noexiste@empresa.com"))
                    .thenReturn(Optional.empty());

            assertThrows(CredencialesInvalidasException.class,
                    () -> authService.login("noexiste@empresa.com", "password"));
        }

        @Test
        @DisplayName("Login falla con password incorrecta")
        void loginPasswordIncorrecta() {
            when(usuarioRepository.findByUsername("test@empresa.com"))
                    .thenReturn(Optional.of(usuarioActivo));

            assertThrows(CredencialesInvalidasException.class,
                    () -> authService.login("test@empresa.com", "wrong-password"));
        }

        @Test
        @DisplayName("Login falla con cuenta desactivada")
        void loginCuentaDesactivada() {
            when(usuarioRepository.findByUsername("inactivo@empresa.com"))
                    .thenReturn(Optional.of(usuarioInactivo));

            assertThrows(CuentaDesactivadaException.class,
                    () -> authService.login("inactivo@empresa.com", passwordPlano));
        }
    }

    @Nested
    @DisplayName("Cambio de contrasena")
    class ChangePasswordTests {

        @Test
        @DisplayName("Cambio de contrasena exitoso")
        void changePasswordExitoso() {
            when(usuarioRepository.findByEmpleadoId(empleadoId))
                    .thenReturn(Optional.of(usuarioActivo));

            authService.changePassword(empleadoId, passwordPlano, "nuevaPass123!");

            verify(usuarioRepository).save(usuarioCaptor.capture());
            Usuario usuarioGuardado = usuarioCaptor.getValue();
            assertTrue(passwordEncoder.matches("nuevaPass123!", usuarioGuardado.getPasswordHash()));
            assertFalse(usuarioGuardado.getEsPrimerAcceso());

            verify(auditLogRepository).save(any(AuditLog.class));
        }

        @Test
        @DisplayName("Cambio de contrasena falla con contrasena actual incorrecta")
        void changePasswordFalla() {
            when(usuarioRepository.findByEmpleadoId(empleadoId))
                    .thenReturn(Optional.of(usuarioActivo));

            assertThrows(CredencialesInvalidasException.class,
                    () -> authService.changePassword(empleadoId, "wrong-password", "nuevaPass123!"));
        }

        @Test
        @DisplayName("Cambio de contrasena falla si usuario no existe")
        void changePasswordUsuarioNoExistente() {
            when(usuarioRepository.findByEmpleadoId(empleadoId))
                    .thenReturn(Optional.empty());

            assertThrows(UsuarioNotFoundException.class,
                    () -> authService.changePassword(empleadoId, passwordPlano, "nuevaPass123!"));
        }
    }

    @Nested
    @DisplayName("Validacion de token")
    class ValidateTokenTests {

        @Test
        @DisplayName("Validacion de token exitosa")
        void validateTokenExitoso() {
            SecretKey secretKey = Keys.hmacShaKeyFor(
                    "MiClaveSecretaParaJWTDeAutenticacionDebeSerLarga32Chars!".getBytes(StandardCharsets.UTF_8));

            String token = Jwts.builder()
                    .subject(empleadoId.toString())
                    .claim("username", "test@empresa.com")
                    .claim("rol", "EMPLEADO")
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + 3600000))
                    .signWith(secretKey)
                    .compact();

            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            when(jwtService.validateToken(token)).thenReturn(claims);

            ValidateTokenResponse response = authService.validateToken(token);

            assertTrue(response.isValid());
            assertEquals(empleadoId, response.getEmpleadoId());
            assertEquals("EMPLEADO", response.getRol());
            assertEquals("test@empresa.com", response.getUsername());
        }

        @Test
        @DisplayName("Validacion de token invalido retorna valid=false")
        void validateTokenInvalido() {
            when(jwtService.validateToken("token-invalido"))
                    .thenThrow(new RuntimeException("Token invalido"));

            ValidateTokenResponse response = authService.validateToken("token-invalido");

            assertFalse(response.isValid());
        }
    }

    @Nested
    @DisplayName("Creacion de cuenta desde evento")
    class CrearCuentaDesdeEventoTests {

        @Test
        @DisplayName("Crear cuenta exitosamente desde evento empleado.creado")
        void crearCuentaExitoso() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("empleadoId", empleadoId.toString());
            payload.put("email", "nuevo@empresa.com");
            payload.put("nombre", "Juan");
            payload.put("apellido", "Perez");
            payload.put("numeroEmpleado", "EMP-002");
            payload.put("cargo", "Desarrollador");
            payload.put("area", "TI");
            payload.put("fechaIngreso", "2024-01-15");

            when(usuarioRepository.existsByEmpleadoId(empleadoId)).thenReturn(false);
            when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> {
                Usuario u = invocation.getArgument(0);
                u.setId(UUID.randomUUID());
                return u;
            });

            Usuario creado = authService.crearCuentaDesdeEvento(payload);

            assertNotNull(creado);
            assertEquals("nuevo@empresa.com", creado.getUsername());
            assertEquals(empleadoId, creado.getEmpleadoId());
            assertTrue(creado.getActivo());
            assertEquals("EMPLEADO", creado.getRol());
            assertTrue(creado.getEsPrimerAcceso());

            verify(usuarioRepository).save(any(Usuario.class));
            verify(auditLogRepository).save(any(AuditLog.class));
            verify(cuentaEventPublisher).publicarCuentaActivada(any());
        }

        @Test
        @DisplayName("No duplicar cuenta si ya existe el empleadoId")
        void noDuplicarCuenta() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("empleadoId", empleadoId.toString());
            payload.put("email", "test@empresa.com");
            payload.put("nombre", "Test");
            payload.put("apellido", "User");

            when(usuarioRepository.existsByEmpleadoId(empleadoId)).thenReturn(true);
            when(usuarioRepository.findByEmpleadoId(empleadoId)).thenReturn(Optional.of(usuarioActivo));

            Usuario resultado = authService.crearCuentaDesdeEvento(payload);

            assertEquals(usuarioActivo.getId(), resultado.getId());
        }
    }

    @Nested
    @DisplayName("Desactivacion de cuenta")
    class DesactivarCuentaTests {

        @Test
        @DisplayName("Desactivar cuenta exitosamente")
        void desactivarCuentaExitoso() {
            when(usuarioRepository.findByEmpleadoId(empleadoId))
                    .thenReturn(Optional.of(usuarioActivo));

            authService.desactivarCuenta(empleadoId, "TEST");

            verify(usuarioRepository).save(usuarioCaptor.capture());
            assertFalse(usuarioCaptor.getValue().getActivo());

            verify(auditLogRepository).save(any(AuditLog.class));
            verify(cuentaEventPublisher).publicarCuentaDesactivada(any());
        }

        @Test
        @DisplayName("Desactivar cuenta desde evento empleado.eliminado")
        void desactivarCuentaDesdeEvento() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("empleadoId", empleadoId.toString());
            payload.put("email", "test@empresa.com");
            payload.put("nombre", "Test User");

            when(usuarioRepository.findByEmpleadoId(empleadoId))
                    .thenReturn(Optional.of(usuarioActivo));

            authService.desactivarCuentaDesdeEvento(payload);

            verify(usuarioRepository).save(usuarioCaptor.capture());
            assertFalse(usuarioCaptor.getValue().getActivo());

            verify(cuentaEventPublisher).publicarCuentaDesactivada(any());
        }
    }

    @Nested
    @DisplayName("Activacion de cuenta")
    class ActivarCuentaTests {

        @Test
        @DisplayName("Activar cuenta exitosamente")
        void activarCuentaExitoso() {
            when(usuarioRepository.findByEmpleadoId(empleadoId))
                    .thenReturn(Optional.of(usuarioInactivo));

            authService.activarCuenta(empleadoId);

            verify(usuarioRepository).save(usuarioCaptor.capture());
            assertTrue(usuarioCaptor.getValue().getActivo());

            verify(cuentaEventPublisher).publicarCuentaActivada(any());
        }

        @Test
        @DisplayName("Activar cuenta falla si no existe el usuario")
        void activarCuentaNoExistente() {
            when(usuarioRepository.findByEmpleadoId(empleadoId))
                    .thenReturn(Optional.empty());

            assertThrows(UsuarioNotFoundException.class,
                    () -> authService.activarCuenta(empleadoId));
        }
    }
}
