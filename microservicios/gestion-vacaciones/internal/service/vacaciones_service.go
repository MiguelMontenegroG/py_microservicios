package service

import (
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/rs/zerolog"

	"github.com/empresa/gestion-vacaciones/internal/messaging"
	"github.com/empresa/gestion-vacaciones/internal/models"
	"github.com/empresa/gestion-vacaciones/internal/repository"
)

const (
	dateFormat = "2006-01-02"
)

type VacacionesService interface {
	Create(req *models.CreateVacacionRequest) (*models.Vacacion, error)
	GetByID(id uuid.UUID) (*models.Vacacion, error)
	List(empleadoID *uuid.UUID) ([]models.Vacacion, error)
	Cancel(id uuid.UUID) error
}

type vacacionesService struct {
	repo      repository.VacacionesRepository
	publisher messaging.RabbitPublisher
	logger    zerolog.Logger
}

func NewVacacionesService(
	repo repository.VacacionesRepository,
	publisher messaging.RabbitPublisher,
	logger zerolog.Logger,
) VacacionesService {
	return &vacacionesService{
		repo:      repo,
		publisher: publisher,
		logger:    logger,
	}
}

func (s *vacacionesService) Create(req *models.CreateVacacionRequest) (*models.Vacacion, error) {
	fechaInicio, err := time.Parse(dateFormat, req.FechaInicio)
	if err != nil {
		return nil, &AppError{
			Code:    "FECHA_INVALIDA",
			Message: "Formato de fechaInicio invalido. Use YYYY-MM-DD",
		}
	}

	fechaFin, err := time.Parse(dateFormat, req.FechaFin)
	if err != nil {
		return nil, &AppError{
			Code:    "FECHA_INVALIDA",
			Message: "Formato de fechaFin invalido. Use YYYY-MM-DD",
		}
	}

	// Normalizar a UTC
	fechaInicio = time.Date(fechaInicio.Year(), fechaInicio.Month(), fechaInicio.Day(), 0, 0, 0, 0, time.UTC)
	fechaFin = time.Date(fechaFin.Year(), fechaFin.Month(), fechaFin.Day(), 0, 0, 0, 0, time.UTC)

	// Validar que fechaFin >= fechaInicio
	if fechaFin.Before(fechaInicio) {
		return nil, &AppError{
			Code:    "FECHAS_INVALIDAS",
			Message: "La fecha fin debe ser mayor o igual a la fecha de inicio",
		}
	}

	// Validar que no existan períodos solapados
	overlap, err := s.repo.ExistsOverlap(req.EmpleadoID, fechaInicio, fechaFin, nil)
	if err != nil {
		s.logger.Error().Err(err).Msg("Error verificando solapamiento")
		return nil, &AppError{
			Code:    "ERROR_INTERNO",
			Message: "Error verificando disponibilidad de fechas",
		}
	}

	if overlap {
		return nil, &AppError{
			Code:    "VACACIONES_SOLAPADAS",
			Message: "Ya existe un periodo de vacaciones programado que se solapa con las fechas solicitadas",
		}
	}

	now := time.Now().UTC()
	vacacion := &models.Vacacion{
		ID:          uuid.New(),
		EmpleadoID:  req.EmpleadoID,
		FechaInicio: fechaInicio,
		FechaFin:    fechaFin,
		Estado:      models.EstadoProgramada,
		CreatedAt:   now,
	}

	if err := s.repo.Create(vacacion); err != nil {
		s.logger.Error().Err(err).Msg("Error creando vacacion en BD")
		return nil, &AppError{
			Code:    "ERROR_INTERNO",
			Message: "Error al crear la solicitud de vacaciones",
		}
	}

	// Publicar evento en RabbitMQ
	if err := s.publisher.PublishVacacionProgramada(vacacion, req.Email, req.Nombre); err != nil {
		s.logger.Error().
			Err(err).
			Str("vacacionId", vacacion.ID.String()).
			Msg("Error publicando evento vacaciones.programadas, la vacacion fue creada pero el evento no se publico")
		// No retornamos error - la vacacion ya fue creada
	}

	s.logger.Info().
		Str("vacacionId", vacacion.ID.String()).
		Str("empleadoId", vacacion.EmpleadoID.String()).
		Str("fechaInicio", fechaInicio.Format(dateFormat)).
		Str("fechaFin", fechaFin.Format(dateFormat)).
		Msg("Vacacion creada exitosamente")

	return vacacion, nil
}

func (s *vacacionesService) GetByID(id uuid.UUID) (*models.Vacacion, error) {
	vacacion, err := s.repo.FindByID(id)
	if err != nil {
		s.logger.Error().Err(err).Str("id", id.String()).Msg("Error obteniendo vacacion")
		return nil, &AppError{
			Code:    "ERROR_INTERNO",
			Message: "Error al obtener la vacacion",
		}
	}

	if vacacion == nil {
		return nil, &AppError{
			Code:    "VACACION_NO_ENCONTRADA",
			Message: fmt.Sprintf("No se encontro vacacion con id %s", id.String()),
		}
	}

	return vacacion, nil
}

func (s *vacacionesService) List(empleadoID *uuid.UUID) ([]models.Vacacion, error) {
	var vacaciones []models.Vacacion
	var err error

	if empleadoID != nil {
		vacaciones, err = s.repo.FindByEmpleadoID(*empleadoID)
	} else {
		vacaciones, err = s.repo.FindAll()
	}

	if err != nil {
		s.logger.Error().Err(err).Msg("Error listando vacaciones")
		return nil, &AppError{
			Code:    "ERROR_INTERNO",
			Message: "Error al listar las vacaciones",
		}
	}

	if vacaciones == nil {
		vacaciones = []models.Vacacion{}
	}

	return vacaciones, nil
}

func (s *vacacionesService) Cancel(id uuid.UUID) error {
	vacacion, err := s.repo.FindByID(id)
	if err != nil {
		s.logger.Error().Err(err).Str("id", id.String()).Msg("Error buscando vacacion para cancelar")
		return &AppError{
			Code:    "ERROR_INTERNO",
			Message: "Error al cancelar la vacacion",
		}
	}

	if vacacion == nil {
		return &AppError{
			Code:    "VACACION_NO_ENCONTRADA",
			Message: fmt.Sprintf("No se encontro vacacion con id %s", id.String()),
		}
	}

	if vacacion.Estado == models.EstadoCancelada {
		return &AppError{
			Code:    "VACACION_YA_CANCELADA",
			Message: "La vacacion ya se encuentra cancelada",
		}
	}

	if vacacion.Estado == models.EstadoCompletada {
		return &AppError{
			Code:    "VACACION_COMPLETADA",
			Message: "No se puede cancelar una vacacion que ya ha sido completada",
		}
	}

	if err := s.repo.UpdateEstado(id, models.EstadoCancelada); err != nil {
		s.logger.Error().Err(err).Str("id", id.String()).Msg("Error actualizando estado a CANCELADA")
		return &AppError{
			Code:    "ERROR_INTERNO",
			Message: "Error al cancelar la vacacion",
		}
	}

	s.logger.Info().
		Str("vacacionId", id.String()).
		Str("estadoAnterior", vacacion.Estado).
		Msg("Vacacion cancelada exitosamente")

	return nil
}

// AppError representa un error de negocio con código y mensaje
type AppError struct {
	Code    string
	Message string
}

func (e *AppError) Error() string {
	return fmt.Sprintf("%s: %s", e.Code, e.Message)
}
