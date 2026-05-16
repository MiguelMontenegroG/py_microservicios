package com.empresa.empleados.service;

import com.empresa.empleados.dto.EmpleadoRequest;
import com.empresa.empleados.dto.EmpleadoResponse;
import com.empresa.empleados.exception.EmpleadoNotFoundException;
import com.empresa.empleados.exception.EstadoInvalidoException;
import com.empresa.empleados.exception.EmailDuplicadoException;
import com.empresa.empleados.exception.NumeroEmpleadoDuplicadoException;
import com.empresa.empleados.model.Empleado;
import com.empresa.empleados.model.EstadoEmpleado;
import com.empresa.empleados.repository.EmpleadoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmpleadoServiceTest {

    @Mock
    private EmpleadoRepository empleadoRepository;

    @Mock
    private EmpleadoEventPublisher eventPublisher;

    @Captor
    private ArgumentCaptor<Empleado> empleadoCaptor;

    private EmpleadoService empleadoService;

    private Empleado empleado;
    private UUID empleadoId;

    @BeforeEach
    void setUp() {
        empleadoService = new EmpleadoService(empleadoRepository, eventPublisher);
        empleadoId = UUID.randomUUID();

        empleado = Empleado.builder()
                .id(empleadoId)
                .nombre("Juan")
                .apellido("Pérez")
                .email("juan.perez@empresa.com")
                .numeroEmpleado("EMP-001")
                .fechaIngreso(LocalDate.of(2024, 1, 15))
                .cargo("Desarrollador Senior")
                .area("Tecnología")
                .estado(EstadoEmpleado.ACTIVO)
                .build();
    }

    @Nested
    @DisplayName("Listar empleados")
    class ListarEmpleados {

        @Test
        @DisplayName("Debe retornar página de empleados activos")
        void debeRetornarPaginaDeEmpleados() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Empleado> page = new PageImpl<>(List.of(empleado));

            when(empleadoRepository.findByEstadoNot(EstadoEmpleado.RETIRADO, pageable))
                    .thenReturn(page);

            Page<EmpleadoResponse> resultado = empleadoService.listarEmpleados(pageable);

            assertThat(resultado).hasSize(1);
            assertThat(resultado.getContent().get(0).getNombre()).isEqualTo("Juan");
            verify(empleadoRepository).findByEstadoNot(EstadoEmpleado.RETIRADO, pageable);
        }

        @Test
        @DisplayName("Debe retornar página vacía si no hay empleados")
        void debeRetornarPaginaVacia() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Empleado> emptyPage = Page.empty();

            when(empleadoRepository.findByEstadoNot(EstadoEmpleado.RETIRADO, pageable))
                    .thenReturn(emptyPage);

            Page<EmpleadoResponse> resultado = empleadoService.listarEmpleados(pageable);

            assertThat(resultado).isEmpty();
        }
    }

    @Nested
    @DisplayName("Obtener empleado por ID")
    class ObtenerEmpleado {

        @Test
        @DisplayName("Debe retornar empleado cuando existe")
        void debeRetornarEmpleado() {
            when(empleadoRepository.findByIdAndEstadoNot(empleadoId, EstadoEmpleado.RETIRADO))
                    .thenReturn(Optional.of(empleado));

            EmpleadoResponse resultado = empleadoService.obtenerEmpleado(empleadoId);

            assertThat(resultado).isNotNull();
            assertThat(resultado.getId()).isEqualTo(empleadoId);
            assertThat(resultado.getEmail()).isEqualTo("juan.perez@empresa.com");
        }

        @Test
        @DisplayName("Debe lanzar excepción cuando no existe")
        void debeLanzarExcepcionCuandoNoExiste() {
            when(empleadoRepository.findByIdAndEstadoNot(empleadoId, EstadoEmpleado.RETIRADO))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> empleadoService.obtenerEmpleado(empleadoId))
                    .isInstanceOf(EmpleadoNotFoundException.class)
                    .hasMessageContaining(empleadoId.toString());
        }
    }

    @Nested
    @DisplayName("Crear empleado")
    class CrearEmpleado {

        private EmpleadoRequest request;

        @BeforeEach
        void setUp() {
            request = EmpleadoRequest.builder()
                    .nombre("María")
                    .apellido("García")
                    .email("maria.garcia@empresa.com")
                    .numeroEmpleado("EMP-002")
                    .fechaIngreso(LocalDate.of(2024, 2, 1))
                    .cargo("Analista")
                    .area("Finanzas")
                    .build();
        }

        @Test
        @DisplayName("Debe crear empleado correctamente y publicar evento")
        void debeCrearEmpleadoYPublicarEvento() {
            Empleado nuevoEmpleado = Empleado.builder()
                    .id(UUID.randomUUID())
                    .nombre(request.getNombre())
                    .apellido(request.getApellido())
                    .email(request.getEmail())
                    .numeroEmpleado(request.getNumeroEmpleado())
                    .fechaIngreso(request.getFechaIngreso())
                    .cargo(request.getCargo())
                    .area(request.getArea())
                    .estado(EstadoEmpleado.ACTIVO)
                    .build();

            when(empleadoRepository.existsByEmail(request.getEmail())).thenReturn(false);
            when(empleadoRepository.existsByNumeroEmpleado(request.getNumeroEmpleado())).thenReturn(false);
            when(empleadoRepository.save(any(Empleado.class))).thenReturn(nuevoEmpleado);

            EmpleadoResponse resultado = empleadoService.crearEmpleado(request);

            assertThat(resultado).isNotNull();
            assertThat(resultado.getNombre()).isEqualTo("María");
            assertThat(resultado.getEstado()).isEqualTo(EstadoEmpleado.ACTIVO);

            verify(empleadoRepository).save(empleadoCaptor.capture());
            Empleado empleadoGuardado = empleadoCaptor.getValue();
            assertThat(empleadoGuardado.getNombre()).isEqualTo("María");
            assertThat(empleadoGuardado.getEstado()).isEqualTo(EstadoEmpleado.ACTIVO);

            verify(eventPublisher).publicarCreado(nuevoEmpleado);
        }

        @Test
        @DisplayName("Debe lanzar excepción si el email ya existe")
        void debeLanzarExcepcionSiEmailDuplicado() {
            when(empleadoRepository.existsByEmail(request.getEmail())).thenReturn(true);

            assertThatThrownBy(() -> empleadoService.crearEmpleado(request))
                    .isInstanceOf(EmailDuplicadoException.class)
                    .hasMessageContaining(request.getEmail());

            verify(empleadoRepository, never()).save(any());
            verify(eventPublisher, never()).publicarCreado(any());
        }

        @Test
        @DisplayName("Debe lanzar excepción si el número de empleado ya existe")
        void debeLanzarExcepcionSiNumeroEmpleadoDuplicado() {
            when(empleadoRepository.existsByEmail(request.getEmail())).thenReturn(false);
            when(empleadoRepository.existsByNumeroEmpleado(request.getNumeroEmpleado())).thenReturn(true);

            assertThatThrownBy(() -> empleadoService.crearEmpleado(request))
                    .isInstanceOf(NumeroEmpleadoDuplicadoException.class)
                    .hasMessageContaining(request.getNumeroEmpleado());

            verify(empleadoRepository, never()).save(any());
            verify(eventPublisher, never()).publicarCreado(any());
        }
    }

    @Nested
    @DisplayName("Actualizar empleado")
    class ActualizarEmpleado {

        private EmpleadoRequest request;

        @BeforeEach
        void setUp() {
            request = EmpleadoRequest.builder()
                    .nombre("Juan Carlos")
                    .apellido("Pérez López")
                    .email("juancarlos.perez@empresa.com")
                    .numeroEmpleado("EMP-001")
                    .fechaIngreso(LocalDate.of(2024, 1, 15))
                    .cargo("Tech Lead")
                    .area("Tecnología")
                    .build();
        }

        @Test
        @DisplayName("Debe actualizar empleado correctamente")
        void debeActualizarEmpleado() {
            when(empleadoRepository.findByIdAndEstadoNot(empleadoId, EstadoEmpleado.RETIRADO))
                    .thenReturn(Optional.of(empleado));
            when(empleadoRepository.save(any(Empleado.class))).thenReturn(empleado);

            EmpleadoResponse resultado = empleadoService.actualizarEmpleado(empleadoId, request);

            assertThat(resultado).isNotNull();
            assertThat(resultado.getNombre()).isEqualTo("Juan Carlos");
            assertThat(resultado.getCargo()).isEqualTo("Tech Lead");

            verify(eventPublisher).publicarActualizado(any(Empleado.class));
        }

        @Test
        @DisplayName("Debe lanzar excepción si el empleado no existe")
        void debeLanzarExcepcionSiNoExiste() {
            when(empleadoRepository.findByIdAndEstadoNot(empleadoId, EstadoEmpleado.RETIRADO))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> empleadoService.actualizarEmpleado(empleadoId, request))
                    .isInstanceOf(EmpleadoNotFoundException.class);
        }

        @Test
        @DisplayName("Debe lanzar excepción si el nuevo email ya existe")
        void debeLanzarExcepcionSiEmailDuplicado() {
            when(empleadoRepository.findByIdAndEstadoNot(empleadoId, EstadoEmpleado.RETIRADO))
                    .thenReturn(Optional.of(empleado));
            when(empleadoRepository.existsByEmail(request.getEmail())).thenReturn(true);

            assertThatThrownBy(() -> empleadoService.actualizarEmpleado(empleadoId, request))
                    .isInstanceOf(EmailDuplicadoException.class);
        }
    }

    @Nested
    @DisplayName("Eliminar empleado (soft delete)")
    class EliminarEmpleado {

        @Test
        @DisplayName("Debe marcar como RETIRADO y publicar evento")
        void debeMarcarComoRetirado() {
            when(empleadoRepository.findByIdAndEstadoNot(empleadoId, EstadoEmpleado.RETIRADO))
                    .thenReturn(Optional.of(empleado));

            empleadoService.eliminarEmpleado(empleadoId);

            assertThat(empleado.getEstado()).isEqualTo(EstadoEmpleado.RETIRADO);
            verify(empleadoRepository).save(empleado);
            verify(eventPublisher).publicarEliminado(empleado);
        }

        @Test
        @DisplayName("Debe lanzar excepción si el empleado no existe")
        void debeLanzarExcepcionSiNoExiste() {
            when(empleadoRepository.findByIdAndEstadoNot(empleadoId, EstadoEmpleado.RETIRADO))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> empleadoService.eliminarEmpleado(empleadoId))
                    .isInstanceOf(EmpleadoNotFoundException.class);

            verify(empleadoRepository, never()).save(any());
            verify(eventPublisher, never()).publicarEliminado(any());
        }
    }

    @Nested
    @DisplayName("Obtener estado")
    class ObtenerEstado {

        @Test
        @DisplayName("Debe retornar el estado del empleado")
        void debeRetornarEstado() {
            when(empleadoRepository.findByIdAndEstadoNot(empleadoId, EstadoEmpleado.RETIRADO))
                    .thenReturn(Optional.of(empleado));

            EstadoEmpleado estado = empleadoService.obtenerEstado(empleadoId);

            assertThat(estado).isEqualTo(EstadoEmpleado.ACTIVO);
        }

        @Test
        @DisplayName("Debe lanzar excepción si no existe")
        void debeLanzarExcepcion() {
            when(empleadoRepository.findByIdAndEstadoNot(empleadoId, EstadoEmpleado.RETIRADO))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> empleadoService.obtenerEstado(empleadoId))
                    .isInstanceOf(EmpleadoNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Cambiar estado")
    class CambiarEstado {

        @Test
        @DisplayName("Debe cambiar a EN_VACACIONES")
        void debeCambiarAVacaciones() {
            when(empleadoRepository.findByIdAndEstadoNot(empleadoId, EstadoEmpleado.RETIRADO))
                    .thenReturn(Optional.of(empleado));
            when(empleadoRepository.save(any(Empleado.class))).thenReturn(empleado);

            EmpleadoResponse resultado = empleadoService.cambiarEstado(empleadoId, EstadoEmpleado.EN_VACACIONES);

            assertThat(resultado.getEstado()).isEqualTo(EstadoEmpleado.EN_VACACIONES);
            verify(eventPublisher).publicarActualizado(any(Empleado.class));
        }

        @Test
        @DisplayName("Debe marcar como RETIRADO y publicar evento de eliminado")
        void debeCambiarARetirado() {
            when(empleadoRepository.findByIdAndEstadoNot(empleadoId, EstadoEmpleado.RETIRADO))
                    .thenReturn(Optional.of(empleado));
            when(empleadoRepository.save(any(Empleado.class))).thenReturn(empleado);

            EmpleadoResponse resultado = empleadoService.cambiarEstado(empleadoId, EstadoEmpleado.RETIRADO);

            assertThat(resultado.getEstado()).isEqualTo(EstadoEmpleado.RETIRADO);
            verify(eventPublisher).publicarEliminado(any(Empleado.class));
        }

        @Test
        @DisplayName("Debe lanzar excepción si el empleado no existe")
        void debeLanzarExcepcion() {
            when(empleadoRepository.findByIdAndEstadoNot(empleadoId, EstadoEmpleado.RETIRADO))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> empleadoService.cambiarEstado(empleadoId, EstadoEmpleado.EN_VACACIONES))
                    .isInstanceOf(EmpleadoNotFoundException.class);
        }
    }
}
