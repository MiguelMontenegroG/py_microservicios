package com.empresa.autenticacion.controller;

import com.empresa.autenticacion.model.Usuario;
import com.empresa.autenticacion.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthControllerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("test_autenticacion_db")
            .withUsername("test")
            .withPassword("test");

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.12-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
        registry.add("app.jwt.secret", () -> "TestSecretKeyParaIntegracionQueDebeTener32Caracteres!");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private BCryptPasswordEncoder passwordEncoder;
    private UUID empleadoId;
    private String username;
    private String password;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(12);
        usuarioRepository.deleteAll();

        empleadoId = UUID.randomUUID();
        username = "test.integration@empresa.com";
        password = "testPassword123";
    }

    @Test
    @DisplayName("GET /health retorna 200 con status UP")
    void healthCheck() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("autenticacion"))
                .andExpect(jsonPath("$.dependencies.database").value("UP"));
    }

    @Test
    @DisplayName("POST /auth/login retorna 200 con token cuando las credenciales son correctas")
    void loginExitoso() throws Exception {
        // Crear usuario directamente en BD
        Usuario usuario = Usuario.builder()
                .empleadoId(empleadoId)
                .username(username)
                .passwordHash(passwordEncoder.encode(password))
                .activo(true)
                .rol("EMPLEADO")
                .esPrimerAcceso(true)
                .build();
        usuarioRepository.save(usuario);

        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", username);
        loginRequest.put("password", password);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.expiresIn").value(86400000))
                .andExpect(jsonPath("$.rol").value("EMPLEADO"))
                .andExpect(jsonPath("$.esPrimerAcceso").value(true));
    }

    @Test
    @DisplayName("POST /auth/login retorna 401 con credenciales incorrectas")
    void loginFallido() throws Exception {
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", "noexiste@empresa.com");
        loginRequest.put("password", "wrongPassword");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CREDENCIALES_INVALIDAS"));
    }

    @Test
    @DisplayName("POST /auth/login retorna 403 con cuenta desactivada")
    void loginCuentaDesactivada() throws Exception {
        Usuario usuario = Usuario.builder()
                .empleadoId(empleadoId)
                .username("desactivado@empresa.com")
                .passwordHash(passwordEncoder.encode(password))
                .activo(false)
                .rol("EMPLEADO")
                .esPrimerAcceso(false)
                .build();
        usuarioRepository.save(usuario);

        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", "desactivado@empresa.com");
        loginRequest.put("password", password);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CUENTA_DESACTIVADA"));
    }

    @Test
    @DisplayName("POST /auth/validate retorna datos validos con token correcto")
    void validateTokenExitoso() throws Exception {
        // Crear usuario y hacer login para obtener token
        Usuario usuario = Usuario.builder()
                .empleadoId(empleadoId)
                .username(username)
                .passwordHash(passwordEncoder.encode(password))
                .activo(true)
                .rol("EMPLEADO")
                .esPrimerAcceso(false)
                .build();
        usuarioRepository.save(usuario);

        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", username);
        loginRequest.put("password", password);

        String loginResponse = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = objectMapper.readTree(loginResponse).get("token").asText();

        Map<String, String> validateRequest = new HashMap<>();
        validateRequest.put("token", token);

        mockMvc.perform(post("/auth/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.empleadoId").value(empleadoId.toString()))
                .andExpect(jsonPath("$.rol").value("EMPLEADO"))
                .andExpect(jsonPath("$.username").value(username));
    }

    @Test
    @DisplayName("POST /auth/change-password retorna 200 con contrasena correcta")
    void changePasswordExitoso() throws Exception {
        // Crear usuario y hacer login
        Usuario usuario = Usuario.builder()
                .empleadoId(empleadoId)
                .username(username)
                .passwordHash(passwordEncoder.encode(password))
                .activo(true)
                .rol("EMPLEADO")
                .esPrimerAcceso(true)
                .build();
        usuarioRepository.save(usuario);

        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", username);
        loginRequest.put("password", password);

        String loginResponse = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = objectMapper.readTree(loginResponse).get("token").asText();

        Map<String, String> changePasswordRequest = new HashMap<>();
        changePasswordRequest.put("currentPassword", password);
        changePasswordRequest.put("newPassword", "newSecurePass789!");

        mockMvc.perform(post("/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changePasswordRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /auth/change-password retorna 401 sin token JWT")
    void changePasswordSinToken() throws Exception {
        Map<String, String> changePasswordRequest = new HashMap<>();
        changePasswordRequest.put("currentPassword", "oldPass");
        changePasswordRequest.put("newPassword", "newPass123!");

        mockMvc.perform(post("/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changePasswordRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /auth/login retorna 400 con username vacio")
    void loginDatosInvalidos() throws Exception {
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", "");
        loginRequest.put("password", "");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ERROR_VALIDACION"));
    }

    @Test
    @DisplayName("PUT /accounts/{empleadoId}/activate activa una cuenta")
    void activateAccount() throws Exception {
        Usuario usuario = Usuario.builder()
                .empleadoId(empleadoId)
                .username("to-activate@empresa.com")
                .passwordHash(passwordEncoder.encode(password))
                .activo(false)
                .rol("EMPLEADO")
                .esPrimerAcceso(false)
                .build();
        usuario = usuarioRepository.save(usuario);

        // Usar el token de un admin - para el test creamos un usuario admin primero
        Usuario admin = Usuario.builder()
                .empleadoId(UUID.randomUUID())
                .username("admin@empresa.com")
                .passwordHash(passwordEncoder.encode("adminPass123"))
                .activo(true)
                .rol("ADMIN")
                .esPrimerAcceso(false)
                .build();
        usuarioRepository.save(admin);

        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", "admin@empresa.com");
        loginRequest.put("password", "adminPass123");

        String loginResponse = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = objectMapper.readTree(loginResponse).get("token").asText();

        mockMvc.perform(put("/accounts/{empleadoId}/activate", empleadoId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
