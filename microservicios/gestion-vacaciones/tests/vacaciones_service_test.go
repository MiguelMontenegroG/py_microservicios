package tests

import (
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/rs/zerolog"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/mock"

	"github.com/empresa/gestion-vacaciones/internal/models"
	"github.com/empresa/gestion-vacaciones/internal/service"
)

// MockRepository implementa repository.VacacionesRepository
type MockRepository struct {
	mock.Mock
}

func (m *MockRepository) Create(vacacion *models.Vacacion) error {
	args := m.Called(vacacion)
	return args.Error(0)
}

func (m *MockRepository) FindByID(id uuid.UUID) (*models.Vacacion, error) {
	args := m.Called(id)
	if args.Get(0) == nil {
		return nil, args.Error(1)
	}
	return args.Get(0).(*models.Vacacion), args.Error(1)
}

func (m *MockRepository) FindByEmpleadoID(empleadoID uuid.UUID) ([]models.Vacacion, error) {
	args := m.Called(empleadoID)
	return args.Get(0).([]models.Vacacion), args.Error(1)
}

func (m *MockRepository) FindAll() ([]models.Vacacion, error) {
	args := m.Called()
	return args.Get(0).([]models.Vacacion), args.Error(1)
}

func (m *MockRepository) UpdateEstado(id uuid.UUID, estado string) error {
	args := m.Called(id, estado)
	return args.Error(0)
}

func (m *MockRepository) ExistsOverlap(empleadoID uuid.UUID, fechaInicio, fechaFin time.Time, excludeID *uuid.UUID) (bool, error) {
	args := m.Called(empleadoID, fechaInicio, fechaFin, excludeID)
	return args.Bool(0), args.Error(1)
}

func (m *MockRepository) Ping() error {
	args := m.Called()
	return args.Error(0)
}

// MockPublisher implementa messaging.RabbitPublisher
type MockPublisher struct {
	mock.Mock
}

func (m *MockPublisher) PublishVacacionProgramada(vacacion *models.Vacacion, email, nombre string) error {
	args := m.Called(vacacion, email, nombre)
	return args.Error(0)
}

func (m *MockPublisher) IsConnected() bool {
	args := m.Called()
	return args.Bool(0)
}

func (m *MockPublisher) Close() error {
	args := m.Called()
	return args.Error(0)
}

func setupServiceTest() (*MockRepository, *MockPublisher, service.VacacionesService) {
	mockRepo := new(MockRepository)
	mockPub := new(MockPublisher)
	logger := zerolog.Nop()
	svc := service.NewVacacionesService(mockRepo, mockPub, logger)
	return mockRepo, mockPub, svc
}

func TestCreateVacacion_Exitoso(t *testing.T) {
	mockRepo, mockPub, svc := setupServiceTest()

	empleadoID := uuid.New()

	req := &models.CreateVacacionRequest{
		EmpleadoID:  empleadoID,
		Email:       "test@empresa.com",
		Nombre:      "Juan Perez",
		FechaInicio: "2024-07-01",
		FechaFin:    "2024-07-15",
	}

	// Mock: no hay solapamiento
	mockRepo.On("ExistsOverlap", empleadoID,
		mock.MatchedBy(func(t time.Time) bool { return true }),
		mock.MatchedBy(func(t time.Time) bool { return true }),
		(*uuid.UUID)(nil),
	).Return(false, nil)

	// Mock: crear vacacion
	mockRepo.On("Create", mock.MatchedBy(func(v *models.Vacacion) bool {
		return v.EmpleadoID == empleadoID &&
			v.Estado == models.EstadoProgramada &&
			!v.FechaInicio.IsZero() &&
			!v.FechaFin.IsZero()
	})).Return(nil)

	// Mock: publicar evento
	mockPub.On("PublishVacacionProgramada",
		mock.MatchedBy(func(v *models.Vacacion) bool {
			return v.EmpleadoID == empleadoID
		}),
		"test@empresa.com",
		"Juan Perez",
	).Return(nil)

	vacacion, err := svc.Create(req)

	assert.NoError(t, err)
	assert.NotNil(t, vacacion)
	assert.Equal(t, empleadoID, vacacion.EmpleadoID)
	assert.Equal(t, models.EstadoProgramada, vacacion.Estado)
	mockRepo.AssertExpectations(t)
	mockPub.AssertExpectations(t)
}

func TestCreateVacacion_FechaInicioInvalida(t *testing.T) {
	_, _, svc := setupServiceTest()

	req := &models.CreateVacacionRequest{
		EmpleadoID:  uuid.New(),
		Email:       "test@empresa.com",
		Nombre:      "Juan Perez",
		FechaInicio: "01-07-2024", // formato incorrecto
		FechaFin:    "2024-07-15",
	}

	vacacion, err := svc.Create(req)

	assert.Error(t, err)
	assert.Nil(t, vacacion)
	assert.Contains(t, err.Error(), "FECHA_INVALIDA")
}

func TestCreateVacacion_FechaFinInvalida(t *testing.T) {
	_, _, svc := setupServiceTest()

	req := &models.CreateVacacionRequest{
		EmpleadoID:  uuid.New(),
		Email:       "test@empresa.com",
		Nombre:      "Juan Perez",
		FechaInicio: "2024-07-01",
		FechaFin:    "15-07-2024", // formato incorrecto
	}

	vacacion, err := svc.Create(req)

	assert.Error(t, err)
	assert.Nil(t, vacacion)
	assert.Contains(t, err.Error(), "FECHA_INVALIDA")
}

func TestCreateVacacion_FechaFinMenorQueFechaInicio(t *testing.T) {
	_, _, svc := setupServiceTest()

	req := &models.CreateVacacionRequest{
		EmpleadoID:  uuid.New(),
		Email:       "test@empresa.com",
		Nombre:      "Juan Perez",
		FechaInicio: "2024-07-15",
		FechaFin:    "2024-07-01", // fecha fin < fecha inicio
	}

	vacacion, err := svc.Create(req)

	assert.Error(t, err)
	assert.Nil(t, vacacion)
	assert.Contains(t, err.Error(), "FECHAS_INVALIDAS")
}

func TestCreateVacacion_Solapamiento(t *testing.T) {
	mockRepo, _, svc := setupServiceTest()

	empleadoID := uuid.New()

	req := &models.CreateVacacionRequest{
		EmpleadoID:  empleadoID,
		Email:       "test@empresa.com",
		Nombre:      "Juan Perez",
		FechaInicio: "2024-07-01",
		FechaFin:    "2024-07-15",
	}

	// Mock: hay solapamiento
	mockRepo.On("ExistsOverlap", empleadoID,
		mock.MatchedBy(func(t time.Time) bool { return true }),
		mock.MatchedBy(func(t time.Time) bool { return true }),
		(*uuid.UUID)(nil),
	).Return(true, nil)

	vacacion, err := svc.Create(req)

	assert.Error(t, err)
	assert.Nil(t, vacacion)
	assert.Contains(t, err.Error(), "VACACIONES_SOLAPADAS")
	mockRepo.AssertExpectations(t)
}

func TestGetVacacion_Exitoso(t *testing.T) {
	mockRepo, _, svc := setupServiceTest()

	vacacionID := uuid.New()
	empleadoID := uuid.New()
	now := time.Now().UTC()

	vacacion := &models.Vacacion{
		ID:          vacacionID,
		EmpleadoID:  empleadoID,
		FechaInicio: now,
		FechaFin:    now.Add(7 * 24 * time.Hour),
		Estado:      models.EstadoProgramada,
		CreatedAt:   now,
	}

	mockRepo.On("FindByID", vacacionID).Return(vacacion, nil)

	result, err := svc.GetByID(vacacionID)

	assert.NoError(t, err)
	assert.NotNil(t, result)
	assert.Equal(t, vacacionID, result.ID)
	assert.Equal(t, empleadoID, result.EmpleadoID)
	mockRepo.AssertExpectations(t)
}

func TestGetVacacion_NoEncontrada(t *testing.T) {
	mockRepo, _, svc := setupServiceTest()

	vacacionID := uuid.New()

	mockRepo.On("FindByID", vacacionID).Return(nil, nil)

	result, err := svc.GetByID(vacacionID)

	assert.Error(t, err)
	assert.Nil(t, result)
	assert.Contains(t, err.Error(), "VACACION_NO_ENCONTRADA")
	mockRepo.AssertExpectations(t)
}

func TestListVacaciones_Todas(t *testing.T) {
	mockRepo, _, svc := setupServiceTest()

	now := time.Now().UTC()
	vacaciones := []models.Vacacion{
		{ID: uuid.New(), EmpleadoID: uuid.New(), FechaInicio: now, FechaFin: now.Add(7 * 24 * time.Hour), Estado: models.EstadoProgramada, CreatedAt: now},
		{ID: uuid.New(), EmpleadoID: uuid.New(), FechaInicio: now.Add(10 * 24 * time.Hour), FechaFin: now.Add(17 * 24 * time.Hour), Estado: models.EstadoProgramada, CreatedAt: now},
	}

	mockRepo.On("FindAll").Return(vacaciones, nil)

	result, err := svc.List(nil)

	assert.NoError(t, err)
	assert.Len(t, result, 2)
	mockRepo.AssertExpectations(t)
}

func TestListVacaciones_PorEmpleado(t *testing.T) {
	mockRepo, _, svc := setupServiceTest()

	empleadoID := uuid.New()
	now := time.Now().UTC()
	vacaciones := []models.Vacacion{
		{ID: uuid.New(), EmpleadoID: empleadoID, FechaInicio: now, FechaFin: now.Add(7 * 24 * time.Hour), Estado: models.EstadoProgramada, CreatedAt: now},
	}

	mockRepo.On("FindByEmpleadoID", empleadoID).Return(vacaciones, nil)

	result, err := svc.List(&empleadoID)

	assert.NoError(t, err)
	assert.Len(t, result, 1)
	assert.Equal(t, empleadoID, result[0].EmpleadoID)
	mockRepo.AssertExpectations(t)
}

func TestListVacaciones_Vacio(t *testing.T) {
	mockRepo, _, svc := setupServiceTest()

	mockRepo.On("FindAll").Return([]models.Vacacion{}, nil)

	result, err := svc.List(nil)

	assert.NoError(t, err)
	assert.Len(t, result, 0)
	mockRepo.AssertExpectations(t)
}

func TestCancelVacacion_Exitoso(t *testing.T) {
	mockRepo, _, svc := setupServiceTest()

	vacacionID := uuid.New()
	now := time.Now().UTC()

	vacacion := &models.Vacacion{
		ID:          vacacionID,
		EmpleadoID:  uuid.New(),
		FechaInicio: now,
		FechaFin:    now.Add(7 * 24 * time.Hour),
		Estado:      models.EstadoProgramada,
		CreatedAt:   now,
	}

	mockRepo.On("FindByID", vacacionID).Return(vacacion, nil)
	mockRepo.On("UpdateEstado", vacacionID, models.EstadoCancelada).Return(nil)

	err := svc.Cancel(vacacionID)

	assert.NoError(t, err)
	mockRepo.AssertExpectations(t)
}

func TestCancelVacacion_NoEncontrada(t *testing.T) {
	mockRepo, _, svc := setupServiceTest()

	vacacionID := uuid.New()

	mockRepo.On("FindByID", vacacionID).Return(nil, nil)

	err := svc.Cancel(vacacionID)

	assert.Error(t, err)
	assert.Contains(t, err.Error(), "VACACION_NO_ENCONTRADA")
	mockRepo.AssertExpectations(t)
}

func TestCancelVacacion_YaCancelada(t *testing.T) {
	mockRepo, _, svc := setupServiceTest()

	vacacionID := uuid.New()
	now := time.Now().UTC()

	vacacion := &models.Vacacion{
		ID:          vacacionID,
		EmpleadoID:  uuid.New(),
		FechaInicio: now,
		FechaFin:    now.Add(7 * 24 * time.Hour),
		Estado:      models.EstadoCancelada,
		CreatedAt:   now,
	}

	mockRepo.On("FindByID", vacacionID).Return(vacacion, nil)

	err := svc.Cancel(vacacionID)

	assert.Error(t, err)
	assert.Contains(t, err.Error(), "VACACION_YA_CANCELADA")
	mockRepo.AssertExpectations(t)
}

func TestCancelVacacion_Completada(t *testing.T) {
	mockRepo, _, svc := setupServiceTest()

	vacacionID := uuid.New()
	now := time.Now().UTC()

	vacacion := &models.Vacacion{
		ID:          vacacionID,
		EmpleadoID:  uuid.New(),
		FechaInicio: now,
		FechaFin:    now.Add(7 * 24 * time.Hour),
		Estado:      models.EstadoCompletada,
		CreatedAt:   now,
	}

	mockRepo.On("FindByID", vacacionID).Return(vacacion, nil)

	err := svc.Cancel(vacacionID)

	assert.Error(t, err)
	assert.Contains(t, err.Error(), "VACACION_COMPLETADA")
	mockRepo.AssertExpectations(t)
}
