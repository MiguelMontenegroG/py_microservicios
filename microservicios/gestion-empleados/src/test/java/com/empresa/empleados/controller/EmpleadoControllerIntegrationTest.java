package com.empresa.empleados.controller;

import com.empresa.empleados.EmpleadosApplication;
import com.empresa.empleados.model.Empleado;
import com.empresa.empleados.model.EstadoEmpleado;
import com.empresa.empleados.repository.EmpleadoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = EmpleadosApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmpleadoControllerIntegrationTest {

    // NOTA: Este test usa el perfil "test" con H2 en modo PostgreSQL.
    // Ver src/test/resources/application-test.properties para la configuracion.
    // No se necesita PostgreSQL ni RabbitMQ reales.

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmpleadoRepository empleadoRepository;

    private Empleado empleado;
    private UUID empleadoId;

    @BeforeEach
    void setUp() {
        empleadoRepository.deleteAll();

        empleado = Empleado.builder()
                .nombre("Juan")
                .apellido("Perez")
                .email("juan.perez@empresa.com")
                .numeroEmpleado("EMP-001")
                .fechaIngreso(LocalDate.of(2024, 1, 15))
                .cargo("Desarrollador Senior")
                .area("Tecnologia")
                .estado(EstadoEmpleado.ACTIVO)
                .build();
        empleado = empleadoRepository.save(empleado);
        empleadoId = empleado.getId();
    }

    @Test
    @DisplayName("GET /health - debe retornar UP")
    void healthEndpoint() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.service", is("gestion-empleados")));
    }

    @Test
    @DisplayName("GET /employees - debe listar empleados paginados")
    void listarEmpleados() throws Exception {
        mockMvc.perform(get("/employees")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].nombre", is("Juan")));
    }

    @Test
    @DisplayName("GET /employees - debe retornar pagina vacia si no hay empleados")
    void listarEmpleadosSinDatos() throws Exception {
        empleadoRepository.deleteAll();

        mockMvc.perform(get("/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    @DisplayName("GET /employees/{id} - debe retornar empleado por ID")
    void obtenerEmpleado() throws Exception {
        mockMvc.perform(get("/employees/{id}", empleadoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(empleadoId.toString())))
                .andExpect(jsonPath("$.email", is("juan.perez@empresa.com")))
                .andExpect(jsonPath("$.estado", is("ACTIVO")));
    }

    @Test
    @DisplayName("GET /employees/{id} - debe retornar 404 si no existe")
    void obtenerEmpleadoNoEncontrado() throws Exception {
        UUID idInexistente = UUID.randomUUID();

        mockMvc.perform(get("/employees/{id}", idInexistente))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("EMPLEADO_NO_ENCONTRADO")));
    }

    @Test
    @DisplayName("POST /employees - debe crear empleado y retornar 201")
    void crearEmpleado() throws Exception {
        Map<String, Object> request = Map.of(
                "nombre", "María",
                "apellido", "García",
                "email", "maria.garcia@empresa.com",
                "numeroEmpleado", "EMP-002",
                "fechaIngreso", "2024-02-01",
                "cargo", "Analista",
                "area", "Finanzas"
        );

        mockMvc.perform(post("/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre", is("María")))
                .andExpect(jsonPath("$.email", is("maria.garcia@empresa.com")))
                .andExpect(jsonPath("$.estado", is("ACTIVO")));
    }

    @Test
    @DisplayName("POST /employees - debe retornar 409 si email duplicado")
    void crearEmpleadoEmailDuplicado() throws Exception {
        Map<String, Object> request = Map.of(
                "nombre", "Otro",
                "apellido", "Nombre",
                "email", "juan.perez@empresa.com",
                "numeroEmpleado", "EMP-003",
                "fechaIngreso", "2024-03-01",
                "cargo", "Test",
                "area", "Test"
        );

        mockMvc.perform(post("/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("EMAIL_DUPLICADO")));
    }

    @Test
    @DisplayName("POST /employees - debe retornar 409 si numero empleado duplicado")
    void crearEmpleadoNumeroDuplicado() throws Exception {
        Map<String, Object> request = Map.of(
                "nombre", "Otro",
                "apellido", "Nombre",
                "email", "otro@empresa.com",
                "numeroEmpleado", "EMP-001",
                "fechaIngreso", "2024-03-01",
                "cargo", "Test",
                "area", "Test"
        );

        mockMvc.perform(post("/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("NUMERO_EMPLEADO_DUPLICADO")));
    }

    @Test
    @DisplayName("POST /employees - debe retornar 400 si datos inválidos")
    void crearEmpleadoDatosInvalidos() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("nombre", "");
        request.put("apellido", "Garcia");
        request.put("email", "email-invalido");
        request.put("numeroEmpleado", "");
        request.put("fechaIngreso", null);

        mockMvc.perform(post("/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("ERROR_VALIDACION")));
    }

    @Test
    @DisplayName("PUT /employees/{id} - debe actualizar empleado")
    void actualizarEmpleado() throws Exception {
        Map<String, Object> request = Map.of(
                "nombre", "Juan Carlos",
                "apellido", "Pérez López",
                "email", "juancarlos.perez@empresa.com",
                "numeroEmpleado", "EMP-001",
                "fechaIngreso", "2024-01-15",
                "cargo", "Tech Lead",
                "area", "Tecnología"
        );

        mockMvc.perform(put("/employees/{id}", empleadoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre", is("Juan Carlos")))
                .andExpect(jsonPath("$.cargo", is("Tech Lead")));
    }

    @Test
    @DisplayName("PUT /employees/{id} - debe retornar 404 si no existe")
    void actualizarEmpleadoNoEncontrado() throws Exception {
        UUID idInexistente = UUID.randomUUID();

        Map<String, Object> request = Map.of(
                "nombre", "Test",
                "apellido", "Test",
                "email", "test@empresa.com",
                "numeroEmpleado", "EMP-999",
                "fechaIngreso", "2024-01-01"
        );

        mockMvc.perform(put("/employees/{id}", idInexistente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /employees/{id} - debe eliminar (soft delete)")
    void eliminarEmpleado() throws Exception {
        mockMvc.perform(delete("/employees/{id}", empleadoId))
                .andExpect(status().isNoContent());

        // Verificar que el estado cambió a RETIRADO
        Empleado empleadoRetirado = empleadoRepository.findById(empleadoId).orElseThrow();
        assert empleadoRetirado.getEstado() == EstadoEmpleado.RETIRADO : "El empleado debería estar RETIRADO";
    }

    @Test
    @DisplayName("DELETE /employees/{id} - debe retornar 404 si no existe")
    void eliminarEmpleadoNoEncontrado() throws Exception {
        UUID idInexistente = UUID.randomUUID();

        mockMvc.perform(delete("/employees/{id}", idInexistente))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /employees/{id}/status - debe retornar estado")
    void obtenerEstado() throws Exception {
        mockMvc.perform(get("/employees/{id}/status", empleadoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("ACTIVO")));
    }

    @Test
    @DisplayName("PUT /employees/{id}/status - debe cambiar estado")
    void cambiarEstado() throws Exception {
        Map<String, Object> request = Map.of("estado", "EN_VACACIONES");

        mockMvc.perform(put("/employees/{id}/status", empleadoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado", is("EN_VACACIONES")));
    }

    @Test
    @DisplayName("PUT /employees/{id}/status - debe retornar 400 si estado invalido")
    void cambiarEstadoInvalido() throws Exception {
        Map<String, Object> request = Map.of("estado", "INVALIDO");

        mockMvc.perform(put("/employees/{id}/status", empleadoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /employees - debe retornar solo activos despues de soft delete")
    void listarEmpleadosDespuesDeEliminar() throws Exception {
        // Crear otro empleado
        Empleado otroEmpleado = Empleado.builder()
                .nombre("Ana")
                .apellido("Lopez")
                .email("ana.lopez@empresa.com")
                .numeroEmpleado("EMP-003")
                .fechaIngreso(LocalDate.of(2024, 3, 1))
                .cargo("Disenadora")
                .area("Marketing")
                .estado(EstadoEmpleado.ACTIVO)
                .build();
        empleadoRepository.save(otroEmpleado);

        // Eliminar el primero via API
        mockMvc.perform(delete("/employees/{id}", empleadoId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].nombre", is("Ana")));
    }
}
