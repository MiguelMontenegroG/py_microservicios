package com.empresa.autenticacion.service;

import com.empresa.autenticacion.exception.CredencialesInvalidasException;
import com.empresa.autenticacion.exception.UsuarioNotFoundException;
import com.empresa.autenticacion.model.PasswordResetToken;
import com.empresa.autenticacion.model.Usuario;
import com.empresa.autenticacion.repository.PasswordResetTokenRepository;
import com.empresa.autenticacion.repository.UsuarioRepository;
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

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private CuentaEventPublisher cuentaEventPublisher;

    @Captor
    private ArgumentCaptor<PasswordResetToken> tokenCaptor;

    @Captor
    private ArgumentCaptor<Usuario> usuarioCaptor;

    private PasswordResetService passwordResetService;
    private BCryptPasswordEncoder passwordEncoder;
    private UUID empleadoId;
    private Usuario usuarioActivo;
    private Usuario usuarioInactivo;
    private String email;
    private String codigo;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(12);
        passwordResetService = new PasswordResetService(
                usuarioRepository,
                tokenRepository,
                cuentaEventPublisher,
                5,   // token-ttl-minutes
                12   // bcrypt strength
        );

        empleadoId = UUID.randomUUID();
        email = "test@empresa.com";
        codigo = "123456";

        usuarioActivo = Usuario.builder()
                .id(UUID.randomUUID())
                .empleadoId(empleadoId)
                .username(email)
                .passwordHash(passwordEncoder.encode("password123"))
                .activo(true)
                .rol("EMPLEADO")
                .esPrimerAcceso(false)
                .build();

        usuarioInactivo = Usuario.builder()
                .id(UUID.randomUUID())
                .empleadoId(empleadoId)
                .username("inactivo@empresa.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .activo(false)
                .rol("EMPLEADO")
                .esPrimerAcceso(false)
                .build();
    }

    @Nested
    @DisplayName("Solicitar recuperacion")
    class SolicitarRecuperacionTests {

        @Test
        @DisplayName("Solicitar recuperacion exitosamente genera codigo y publica evento")
        void solicitarRecuperacionExitoso() {
            when(usuarioRepository.findByUsername(email)).thenReturn(Optional.of(usuarioActivo));

            passwordResetService.solicitarRecuperacion(email);

            verify(tokenRepository).save(tokenCaptor.capture());
            PasswordResetToken tokenGuardado = tokenCaptor.getValue();

            assertNotNull(tokenGuardado.getCodigo());
            assertEquals(6, tokenGuardado.getCodigo().length());
            assertEquals(email, tokenGuardado.getEmail());
            assertEquals(empleadoId, tokenGuardado.getEmpleadoId());
            assertFalse(tokenGuardado.getUtilizado());
            assertFalse(tokenGuardado.isExpirado());

            verify(cuentaEventPublisher).publicarResetSolicitado(
                    eq(empleadoId), eq(email), anyString(), anyString(), eq(5));
        }

        @Test
        @DisplayName("Solicitar recuperacion con email no registrado no hace nada (seguridad)")
        void solicitarRecuperacionEmailNoRegistrado() {
            when(usuarioRepository.findByUsername("noexiste@empresa.com")).thenReturn(Optional.empty());

            passwordResetService.solicitarRecuperacion("noexiste@empresa.com");

            verify(tokenRepository, never()).save(any());
            verify(cuentaEventPublisher, never()).publicarResetSolicitado(any(), any(), any(), any(), anyInt());
        }

        @Test
        @DisplayName("Solicitar recuperacion con cuenta desactivada no hace nada")
        void solicitarRecuperacionCuentaDesactivada() {
            when(usuarioRepository.findByUsername("inactivo@empresa.com")).thenReturn(Optional.of(usuarioInactivo));

            passwordResetService.solicitarRecuperacion("inactivo@empresa.com");

            verify(tokenRepository, never()).save(any());
            verify(cuentaEventPublisher, never()).publicarResetSolicitado(any(), any(), any(), any(), anyInt());
        }

        @Test
        @DisplayName("Solicitar recuperacion invalida tokens previos no utilizados")
        void solicitarRecuperacionInvalidaTokensPrevios() {
            when(usuarioRepository.findByUsername(email)).thenReturn(Optional.of(usuarioActivo));

            LocalDateTime ahora = LocalDateTime.now();
            PasswordResetToken tokenExpirado = new PasswordResetToken(empleadoId, email, "111111", ahora.minusMinutes(1));
            tokenExpirado.setUtilizado(false);
            when(tokenRepository.findByEmailAndUtilizadoFalseAndExpiraEnBefore(eq(email), any(LocalDateTime.class)))
                    .thenReturn(java.util.List.of(tokenExpirado));

            passwordResetService.solicitarRecuperacion(email);

            verify(tokenRepository).save(tokenExpirado);
            assertTrue(tokenExpirado.getUtilizado());
        }
    }

    @Nested
    @DisplayName("Restablecer contrasena")
    class RestablecerContrasenaTests {

        @Test
        @DisplayName("Restablecer contrasena exitosamente con codigo valido")
        void restablecerContrasenaExitoso() {
            PasswordResetToken tokenValido = new PasswordResetToken(
                    empleadoId, email, codigo, LocalDateTime.now().plusMinutes(5));

            when(tokenRepository.findTopByEmailAndCodigoAndUtilizadoFalseOrderByCreatedAtDesc(email, codigo))
                    .thenReturn(Optional.of(tokenValido));
            when(usuarioRepository.findByEmpleadoId(empleadoId)).thenReturn(Optional.of(usuarioActivo));

            passwordResetService.restablecerContrasena(email, codigo, "nuevaPass123!");

            // Verificar que el token se marco como utilizado
            assertTrue(tokenValido.getUtilizado());
            verify(tokenRepository).save(tokenValido);

            // Verificar que la contrasena se actualizo
            verify(usuarioRepository).save(usuarioCaptor.capture());
            Usuario usuarioGuardado = usuarioCaptor.getValue();
            assertTrue(passwordEncoder.matches("nuevaPass123!", usuarioGuardado.getPasswordHash()));
            assertFalse(usuarioGuardado.getEsPrimerAcceso());
        }

        @Test
        @DisplayName("Restablecer contrasena falla con codigo invalido")
        void restablecerContrasenaCodigoInvalido() {
            when(tokenRepository.findTopByEmailAndCodigoAndUtilizadoFalseOrderByCreatedAtDesc(email, "000000"))
                    .thenReturn(Optional.empty());

            assertThrows(CredencialesInvalidasException.class,
                    () -> passwordResetService.restablecerContrasena(email, "000000", "nuevaPass123!"));
        }

        @Test
        @DisplayName("Restablecer contrasena falla con codigo expirado")
        void restablecerContrasenaCodigoExpirado() {
            PasswordResetToken tokenExpirado = new PasswordResetToken(
                    empleadoId, email, codigo, LocalDateTime.now().minusMinutes(1));

            when(tokenRepository.findTopByEmailAndCodigoAndUtilizadoFalseOrderByCreatedAtDesc(email, codigo))
                    .thenReturn(Optional.of(tokenExpirado));

            assertThrows(CredencialesInvalidasException.class,
                    () -> passwordResetService.restablecerContrasena(email, codigo, "nuevaPass123!"));

            // Verificar que el token expirado se marco como utilizado
            assertTrue(tokenExpirado.getUtilizado());
            verify(tokenRepository).save(tokenExpirado);
        }

        @Test
        @DisplayName("Restablecer contrasena falla si el usuario no existe")
        void restablecerContrasenaUsuarioNoExistente() {
            PasswordResetToken tokenValido = new PasswordResetToken(
                    empleadoId, email, codigo, LocalDateTime.now().plusMinutes(5));

            when(tokenRepository.findTopByEmailAndCodigoAndUtilizadoFalseOrderByCreatedAtDesc(email, codigo))
                    .thenReturn(Optional.of(tokenValido));
            when(usuarioRepository.findByEmpleadoId(empleadoId)).thenReturn(Optional.empty());

            assertThrows(UsuarioNotFoundException.class,
                    () -> passwordResetService.restablecerContrasena(email, codigo, "nuevaPass123!"));
        }
    }
}
