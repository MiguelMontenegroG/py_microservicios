package handlers

import (
	"database/sql"
	"net/http"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promhttp"
	"github.com/rs/zerolog"

	"github.com/empresa/gestion-vacaciones/internal/messaging"
	"github.com/empresa/gestion-vacaciones/internal/models"
	"github.com/empresa/gestion-vacaciones/internal/service"
)

var (
	vacacionesCreadas = prometheus.NewCounter(prometheus.CounterOpts{
		Name: "vacaciones_creadas_total",
		Help: "Numero total de vacaciones creadas",
	})
	vacacionesErrores = prometheus.NewCounter(prometheus.CounterOpts{
		Name: "vacaciones_errores_total",
		Help: "Numero total de errores en vacaciones",
	})
)

func init() {
	prometheus.MustRegister(vacacionesCreadas)
	prometheus.MustRegister(vacacionesErrores)
}

type VacacionesHandler struct {
	svc       service.VacacionesService
	db        *sql.DB
	logger    zerolog.Logger
	publisher messaging.RabbitPublisher
}

func NewVacacionesHandler(
	svc service.VacacionesService,
	db *sql.DB,
	logger zerolog.Logger,
	publisher messaging.RabbitPublisher,
) *VacacionesHandler {
	return &VacacionesHandler{
		svc:       svc,
		db:        db,
		logger:    logger,
		publisher: publisher,
	}
}

// Health godoc
// @Summary Health check
// @Description Verifica el estado del servicio y sus dependencias
// @Tags health
// @Produce json
// @Success 200 {object} models.HealthResponse
// @Router /health [get]
func (h *VacacionesHandler) Health(c *gin.Context) {
	deps := map[string]string{
		"database": "DOWN",
		"rabbitmq": "DOWN",
	}

	// Ping a PostgreSQL
	if h.db != nil {
		if err := h.db.Ping(); err != nil {
			h.logger.Error().Err(err).Msg("Health check: database DOWN")
		} else {
			deps["database"] = "UP"
		}
	} else {
		h.logger.Warn().Msg("Health check: database no configurada (nil)")
	}

	// Verificar RabbitMQ
	if h.publisher.IsConnected() {
		deps["rabbitmq"] = "UP"
	} else {
		h.logger.Warn().Msg("Health check: rabbitmq DOWN")
	}

	status := "UP"
	for _, v := range deps {
		if v == "DOWN" {
			status = "DEGRADED"
			break
		}
	}

	c.JSON(http.StatusOK, models.HealthResponse{
		Status:       status,
		Service:      "gestion-vacaciones",
		Timestamp:    time.Now().UTC().Format(time.RFC3339),
		Dependencies: deps,
	})
}

// CreateVacacion godoc
// @Summary Programar vacaciones
// @Description Crea una nueva solicitud de vacaciones
// @Tags vacaciones
// @Accept json
// @Produce json
// @Param request body models.CreateVacacionRequest true "Datos de la vacacion"
// @Success 201 {object} models.VacacionResponse
// @Failure 400 {object} models.VacacionResponse
// @Failure 409 {object} models.VacacionResponse
// @Failure 500 {object} models.VacacionResponse
// @Router /vacations [post]
func (h *VacacionesHandler) CreateVacacion(c *gin.Context) {
	var req models.CreateVacacionRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		vacacionesErrores.Inc()
		h.logger.Warn().Err(err).Msg("Error validando request de creacion de vacacion")
		c.JSON(http.StatusBadRequest, models.VacacionResponse{
			Success:   false,
			Error: &models.ErrorInfo{
				Code:    "SOLICITUD_INVALIDA",
				Message: "Datos de solicitud invalidos: " + err.Error(),
			},
			Timestamp: time.Now().UTC().Format(time.RFC3339),
		})
		return
	}

	vacacion, err := h.svc.Create(&req)
	if err != nil {
		vacacionesErrores.Inc()

		if appErr, ok := err.(*service.AppError); ok {
			statusCode := http.StatusInternalServerError
			switch appErr.Code {
			case "FECHA_INVALIDA", "FECHAS_INVALIDAS", "SOLICITUD_INVALIDA":
				statusCode = http.StatusBadRequest
			case "VACACIONES_SOLAPADAS":
				statusCode = http.StatusConflict
			}

			c.JSON(statusCode, models.VacacionResponse{
				Success: false,
				Error: &models.ErrorInfo{
					Code:    appErr.Code,
					Message: appErr.Message,
				},
				Timestamp: time.Now().UTC().Format(time.RFC3339),
			})
			return
		}

		c.JSON(http.StatusInternalServerError, models.VacacionResponse{
			Success: false,
			Error: &models.ErrorInfo{
				Code:    "ERROR_INTERNO",
				Message: "Error interno del servidor",
			},
			Timestamp: time.Now().UTC().Format(time.RFC3339),
		})
		return
	}

	vacacionesCreadas.Inc()

	c.JSON(http.StatusCreated, models.VacacionResponse{
		Success:   true,
		Data:      *vacacion,
		Timestamp: time.Now().UTC().Format(time.RFC3339),
	})
}

// GetVacacion godoc
// @Summary Obtener vacacion por ID
// @Description Obtiene los detalles de una vacacion especifica
// @Tags vacaciones
// @Produce json
// @Param id path string true "ID de la vacacion"
// @Success 200 {object} models.VacacionResponse
// @Failure 404 {object} models.VacacionResponse
// @Failure 500 {object} models.VacacionResponse
// @Router /vacations/{id} [get]
func (h *VacacionesHandler) GetVacacion(c *gin.Context) {
	idStr := c.Param("id")
	id, err := uuid.Parse(idStr)
	if err != nil {
		vacacionesErrores.Inc()
		c.JSON(http.StatusBadRequest, models.VacacionResponse{
			Success: false,
			Error: &models.ErrorInfo{
				Code:    "ID_INVALIDO",
				Message: "El ID proporcionado no es un UUID valido",
			},
			Timestamp: time.Now().UTC().Format(time.RFC3339),
		})
		return
	}

	vacacion, err := h.svc.GetByID(id)
	if err != nil {
		vacacionesErrores.Inc()

		if appErr, ok := err.(*service.AppError); ok && appErr.Code == "VACACION_NO_ENCONTRADA" {
			c.JSON(http.StatusNotFound, models.VacacionResponse{
				Success: false,
				Error: &models.ErrorInfo{
					Code:    appErr.Code,
					Message: appErr.Message,
				},
				Timestamp: time.Now().UTC().Format(time.RFC3339),
			})
			return
		}

		c.JSON(http.StatusInternalServerError, models.VacacionResponse{
			Success: false,
			Error: &models.ErrorInfo{
				Code:    "ERROR_INTERNO",
				Message: "Error interno del servidor",
			},
			Timestamp: time.Now().UTC().Format(time.RFC3339),
		})
		return
	}

	c.JSON(http.StatusOK, models.VacacionResponse{
		Success:   true,
		Data:      *vacacion,
		Timestamp: time.Now().UTC().Format(time.RFC3339),
	})
}

// ListVacaciones godoc
// @Summary Listar vacaciones
// @Description Lista todas las vacaciones o filtra por empleado
// @Tags vacaciones
// @Produce json
// @Param empleadoId query string false "Filtrar por ID de empleado"
// @Success 200 {object} models.ListVacacionesResponse
// @Failure 500 {object} models.ListVacacionesResponse
// @Router /vacations [get]
func (h *VacacionesHandler) ListVacaciones(c *gin.Context) {
	var empleadoID *uuid.UUID

	if empleadoIDStr := c.Query("empleadoId"); empleadoIDStr != "" {
		parsedID, err := uuid.Parse(empleadoIDStr)
		if err != nil {
			vacacionesErrores.Inc()
			c.JSON(http.StatusBadRequest, models.ListVacacionesResponse{
				Success: false,
				Error: &models.ErrorInfo{
					Code:    "ID_INVALIDO",
					Message: "El empleadoId proporcionado no es un UUID valido",
				},
				Timestamp: time.Now().UTC().Format(time.RFC3339),
			})
			return
		}
		empleadoID = &parsedID
	}

	vacaciones, err := h.svc.List(empleadoID)
	if err != nil {
		vacacionesErrores.Inc()
		c.JSON(http.StatusInternalServerError, models.ListVacacionesResponse{
			Success: false,
			Error: &models.ErrorInfo{
				Code:    "ERROR_INTERNO",
				Message: "Error al listar las vacaciones",
			},
			Timestamp: time.Now().UTC().Format(time.RFC3339),
		})
		return
	}

	c.JSON(http.StatusOK, models.ListVacacionesResponse{
		Success:   true,
		Data:      vacaciones,
		Timestamp: time.Now().UTC().Format(time.RFC3339),
	})
}

// CancelVacacion godoc
// @Summary Cancelar vacacion
// @Description Cancela una vacacion programada
// @Tags vacaciones
// @Produce json
// @Param id path string true "ID de la vacacion"
// @Success 200 {object} models.VacacionResponse
// @Failure 404 {object} models.VacacionResponse
// @Failure 409 {object} models.VacacionResponse
// @Failure 500 {object} models.VacacionResponse
// @Router /vacations/{id} [delete]
func (h *VacacionesHandler) CancelVacacion(c *gin.Context) {
	idStr := c.Param("id")
	id, err := uuid.Parse(idStr)
	if err != nil {
		vacacionesErrores.Inc()
		c.JSON(http.StatusBadRequest, models.VacacionResponse{
			Success: false,
			Error: &models.ErrorInfo{
				Code:    "ID_INVALIDO",
				Message: "El ID proporcionado no es un UUID valido",
			},
			Timestamp: time.Now().UTC().Format(time.RFC3339),
		})
		return
	}

	if err := h.svc.Cancel(id); err != nil {
		vacacionesErrores.Inc()

		if appErr, ok := err.(*service.AppError); ok {
			statusCode := http.StatusInternalServerError
			switch appErr.Code {
			case "VACACION_NO_ENCONTRADA":
				statusCode = http.StatusNotFound
			case "VACACION_YA_CANCELADA", "VACACION_COMPLETADA":
				statusCode = http.StatusConflict
			}

			c.JSON(statusCode, models.VacacionResponse{
				Success: false,
				Error: &models.ErrorInfo{
					Code:    appErr.Code,
					Message: appErr.Message,
				},
				Timestamp: time.Now().UTC().Format(time.RFC3339),
			})
			return
		}

		c.JSON(http.StatusInternalServerError, models.VacacionResponse{
			Success: false,
			Error: &models.ErrorInfo{
				Code:    "ERROR_INTERNO",
				Message: "Error interno del servidor",
			},
			Timestamp: time.Now().UTC().Format(time.RFC3339),
		})
		return
	}

	c.JSON(http.StatusOK, models.VacacionResponse{
		Success:   true,
		Data:      models.Vacacion{}, // empty data, just success
		Timestamp: time.Now().UTC().Format(time.RFC3339),
	})
}

// PrometheusHandler godoc
// @Summary Prometheus metrics
// @Description Exposes Prometheus metrics
// @Tags metrics
// @Produce plain
// @Router /metrics [get]
func (h *VacacionesHandler) PrometheusHandler() gin.HandlerFunc {
	return gin.WrapH(promhttp.Handler())
}
