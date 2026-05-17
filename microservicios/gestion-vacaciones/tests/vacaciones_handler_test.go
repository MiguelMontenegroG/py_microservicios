package tests

import (
	"bytes"
	"database/sql"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	"github.com/rs/zerolog"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/mock"

	"github.com/empresa/gestion-vacaciones/internal/handlers"
	"github.com/empresa/gestion-vacaciones/internal/models"
	"github.com/empresa/gestion-vacaciones/internal/service"
)

func setupRouter(svc service.VacacionesService, mockPub *MockPublisher) *gin.Engine {
	gin.SetMode(gin.TestMode)
	logger := zerolog.Nop()

	// Usamos una DB mockeada para el health check que no falle
	var db *sql.DB = nil

	handler := handlers.NewVacacionesHandler(svc, db, logger, mockPub)

	router := gin.New()
	router.GET("/health", handler.Health)
	router.POST("/vacations", handler.CreateVacacion)
	router.GET("/vacations", handler.ListVacaciones)
	router.GET("/vacations/:id", handler.GetVacacion)
	router.DELETE("/vacations/:id", handler.CancelVacacion)

	return router
}

func TestHealthEndpoint(t *testing.T) {
	mockRepo := new(MockRepository)
	mockPub := new(MockPublisher)
	logger := zerolog.Nop()
	svc := service.NewVacacionesService(mockRepo, mockPub, logger)

	// Configurar Gin en modo test
	gin.SetMode(gin.TestMode)

	var db *sql.DB = nil
	handler := handlers.NewVacacionesHandler(svc, db, logger, mockPub)

	router := gin.New()
	router.GET("/health", handler.Health)

	// Mock: RabbitMQ no conectado (no hay mock para IsConnected esperado)
	mockPub.On("IsConnected").Return(false)

	req := httptest.NewRequest(http.MethodGet, "/health", nil)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var response models.HealthResponse
	err := json.Unmarshal(w.Body.Bytes(), &response)
	assert.NoError(t, err)
	assert.Equal(t, "gestion-vacaciones", response.Service)
	// Dependencias deberian estar DOWN porque no hay BD real
	assert.Contains(t, response.Dependencies, "database")
	assert.Contains(t, response.Dependencies, "rabbitmq")
}

func TestHandlerCreateVacacionExitosoOld(t *testing.T) {
	mockRepo := new(MockRepository)
	mockPub := new(MockPublisher)
	logger := zerolog.Nop()
	svc := service.NewVacacionesService(mockRepo, mockPub, logger)

	router := setupRouter(svc, mockPub)

	empleadoID := uuid.New()

	reqBody := map[string]interface{}{
		"empleadoId":  empleadoID.String(),
		"email":       "test@empresa.com",
		"nombre":      "Juan Perez",
		"fechaInicio": "2024-07-01",
		"fechaFin":    "2024-07-15",
	}

	body, _ := json.Marshal(reqBody)

	// Mock: no hay solapamiento
	mockRepo.On("ExistsOverlap", empleadoID,
		mock.MatchedBy(func(t time.Time) bool { return true }),
		mock.MatchedBy(func(t time.Time) bool { return true }),
		(*uuid.UUID)(nil),
	).Return(false, nil)

	// Mock: crear vacacion
	mockRepo.On("Create", mock.MatchedBy(func(v *models.Vacacion) bool {
		return v.EmpleadoID == empleadoID
	})).Return(nil)

	// Mock: publicar evento
	mockPub.On("PublishVacacionProgramada",
		mock.MatchedBy(func(v *models.Vacacion) bool {
			return v.EmpleadoID == empleadoID
		}),
		"test@empresa.com",
		"Juan Perez",
	).Return(nil)

	req := httptest.NewRequest(http.MethodPost, "/vacations", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusCreated, w.Code)

	var response models.VacacionResponse
	err := json.Unmarshal(w.Body.Bytes(), &response)
	assert.NoError(t, err)
	assert.True(t, response.Success)
	assert.NotNil(t, response.Data)
	assert.Equal(t, empleadoID, response.Data.EmpleadoID)

	mockRepo.AssertExpectations(t)
	mockPub.AssertExpectations(t)
}

func TestHandlerCreateVacacionSolicitudInvalidaOld(t *testing.T) {
	mockRepo := new(MockRepository)
	mockPub := new(MockPublisher)
	logger := zerolog.Nop()
	svc := service.NewVacacionesService(mockRepo, mockPub, logger)

	router := setupRouter(svc, mockPub)

	// Request vacio (falta empleadoId, email, nombre, fechas)
	reqBody := map[string]interface{}{}
	body, _ := json.Marshal(reqBody)

	req := httptest.NewRequest(http.MethodPost, "/vacations", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)

	var response models.VacacionResponse
	err := json.Unmarshal(w.Body.Bytes(), &response)
	assert.NoError(t, err)
	assert.False(t, response.Success)
	assert.NotNil(t, response.Error)
}

func TestHandlerCreateVacacionSolapamiento(t *testing.T) {
	mockRepo := new(MockRepository)
	mockPub := new(MockPublisher)
	logger := zerolog.Nop()
	svc := service.NewVacacionesService(mockRepo, mockPub, logger)

	router := setupRouter(svc, mockPub)

	empleadoID := uuid.New()

	reqBody := map[string]interface{}{
		"empleadoId":  empleadoID.String(),
		"email":       "test@empresa.com",
		"nombre":      "Juan Perez",
		"fechaInicio": "2024-07-01",
		"fechaFin":    "2024-07-15",
	}

	body, _ := json.Marshal(reqBody)

	// Mock: hay solapamiento
	mockRepo.On("ExistsOverlap", empleadoID,
		mock.MatchedBy(func(t time.Time) bool { return true }),
		mock.MatchedBy(func(t time.Time) bool { return true }),
		(*uuid.UUID)(nil),
	).Return(true, nil)

	req := httptest.NewRequest(http.MethodPost, "/vacations", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusConflict, w.Code)

	var response models.VacacionResponse
	err := json.Unmarshal(w.Body.Bytes(), &response)
	assert.NoError(t, err)
	assert.False(t, response.Success)
	assert.NotNil(t, response.Error)
	assert.Equal(t, "VACACIONES_SOLAPADAS", response.Error.Code)

	mockRepo.AssertExpectations(t)
}

func TestHandlerGetVacacionExitosoOld(t *testing.T) {
	mockRepo := new(MockRepository)
	mockPub := new(MockPublisher)
	logger := zerolog.Nop()
	svc := service.NewVacacionesService(mockRepo, mockPub, logger)

	router := setupRouter(svc, mockPub)

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

	req := httptest.NewRequest(http.MethodGet, "/vacations/"+vacacionID.String(), nil)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var response models.VacacionResponse
	err := json.Unmarshal(w.Body.Bytes(), &response)
	assert.NoError(t, err)
	assert.True(t, response.Success)
	assert.Equal(t, vacacionID, response.Data.ID)
	assert.Equal(t, empleadoID, response.Data.EmpleadoID)

	mockRepo.AssertExpectations(t)
}

func TestHandlerGetVacacionNoEncontradaOld(t *testing.T) {
	mockRepo := new(MockRepository)
	mockPub := new(MockPublisher)
	logger := zerolog.Nop()
	svc := service.NewVacacionesService(mockRepo, mockPub, logger)

	router := setupRouter(svc, mockPub)

	vacacionID := uuid.New()

	mockRepo.On("FindByID", vacacionID).Return(nil, nil)

	req := httptest.NewRequest(http.MethodGet, "/vacations/"+vacacionID.String(), nil)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)

	var response models.VacacionResponse
	err := json.Unmarshal(w.Body.Bytes(), &response)
	assert.NoError(t, err)
	assert.False(t, response.Success)
	assert.NotNil(t, response.Error)
	assert.Equal(t, "VACACION_NO_ENCONTRADA", response.Error.Code)

	mockRepo.AssertExpectations(t)
}

func TestHandlerGetVacacionIDInvalido(t *testing.T) {
	mockRepo := new(MockRepository)
	mockPub := new(MockPublisher)
	logger := zerolog.Nop()
	svc := service.NewVacacionesService(mockRepo, mockPub, logger)

	router := setupRouter(svc, mockPub)

	req := httptest.NewRequest(http.MethodGet, "/vacations/id-invalido", nil)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestHandlerListVacaciones(t *testing.T) {
	mockRepo := new(MockRepository)
	mockPub := new(MockPublisher)
	logger := zerolog.Nop()
	svc := service.NewVacacionesService(mockRepo, mockPub, logger)

	router := setupRouter(svc, mockPub)

	now := time.Now().UTC()
	vacaciones := []models.Vacacion{
		{ID: uuid.New(), EmpleadoID: uuid.New(), FechaInicio: now, FechaFin: now.Add(7 * 24 * time.Hour), Estado: models.EstadoProgramada, CreatedAt: now},
		{ID: uuid.New(), EmpleadoID: uuid.New(), FechaInicio: now.Add(10 * 24 * time.Hour), FechaFin: now.Add(17 * 24 * time.Hour), Estado: models.EstadoProgramada, CreatedAt: now},
	}

	mockRepo.On("FindAll").Return(vacaciones, nil)

	req := httptest.NewRequest(http.MethodGet, "/vacations", nil)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var response models.ListVacacionesResponse
	err := json.Unmarshal(w.Body.Bytes(), &response)
	assert.NoError(t, err)
	assert.True(t, response.Success)
	assert.Len(t, response.Data, 2)

	mockRepo.AssertExpectations(t)
}

func TestHandlerListVacacionesPorEmpleadoOld(t *testing.T) {
	mockRepo := new(MockRepository)
	mockPub := new(MockPublisher)
	logger := zerolog.Nop()
	svc := service.NewVacacionesService(mockRepo, mockPub, logger)

	router := setupRouter(svc, mockPub)

	empleadoID := uuid.New()
	now := time.Now().UTC()
	vacaciones := []models.Vacacion{
		{ID: uuid.New(), EmpleadoID: empleadoID, FechaInicio: now, FechaFin: now.Add(7 * 24 * time.Hour), Estado: models.EstadoProgramada, CreatedAt: now},
	}

	mockRepo.On("FindByEmpleadoID", empleadoID).Return(vacaciones, nil)

	req := httptest.NewRequest(http.MethodGet, "/vacations?empleadoId="+empleadoID.String(), nil)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var response models.ListVacacionesResponse
	err := json.Unmarshal(w.Body.Bytes(), &response)
	assert.NoError(t, err)
	assert.True(t, response.Success)
	assert.Len(t, response.Data, 1)
	assert.Equal(t, empleadoID, response.Data[0].EmpleadoID)

	mockRepo.AssertExpectations(t)
}

func TestHandlerCancelVacacionExitosoOld(t *testing.T) {
	mockRepo := new(MockRepository)
	mockPub := new(MockPublisher)
	logger := zerolog.Nop()
	svc := service.NewVacacionesService(mockRepo, mockPub, logger)

	router := setupRouter(svc, mockPub)

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

	req := httptest.NewRequest(http.MethodDelete, "/vacations/"+vacacionID.String(), nil)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	mockRepo.AssertExpectations(t)
}

func TestHandlerCancelVacacionNoEncontradaOld(t *testing.T) {
	mockRepo := new(MockRepository)
	mockPub := new(MockPublisher)
	logger := zerolog.Nop()
	svc := service.NewVacacionesService(mockRepo, mockPub, logger)

	router := setupRouter(svc, mockPub)

	vacacionID := uuid.New()

	mockRepo.On("FindByID", vacacionID).Return(nil, nil)

	req := httptest.NewRequest(http.MethodDelete, "/vacations/"+vacacionID.String(), nil)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)

	mockRepo.AssertExpectations(t)
}
