package models

import (
	"time"

	"github.com/google/uuid"
)

const (
	EstadoProgramada  = "PROGRAMADA"
	EstadoActiva      = "ACTIVA"
	EstadoCompletada  = "COMPLETADA"
	EstadoCancelada   = "CANCELADA"
)

type Vacacion struct {
	ID          uuid.UUID `json:"id" db:"id"`
	EmpleadoID  uuid.UUID `json:"empleadoId" db:"empleado_id"`
	FechaInicio time.Time `json:"fechaInicio" db:"fecha_inicio"`
	FechaFin    time.Time `json:"fechaFin" db:"fecha_fin"`
	Estado      string    `json:"estado" db:"estado"`
	CreatedAt   time.Time `json:"createdAt" db:"created_at"`
}

type CreateVacacionRequest struct {
	EmpleadoID  uuid.UUID `json:"empleadoId" binding:"required"`
	Email       string    `json:"email" binding:"required,email"`
	Nombre      string    `json:"nombre" binding:"required"`
	FechaInicio string    `json:"fechaInicio" binding:"required"`
	FechaFin    string    `json:"fechaFin" binding:"required"`
}

type VacacionResponse struct {
	Success bool     `json:"success"`
	Data    Vacacion `json:"data,omitempty"`
	Error   *ErrorInfo `json:"error,omitempty"`
	Timestamp string `json:"timestamp"`
}

type ListVacacionesResponse struct {
	Success    bool       `json:"success"`
	Data       []Vacacion `json:"data,omitempty"`
	Error      *ErrorInfo `json:"error,omitempty"`
	Timestamp  string     `json:"timestamp"`
}

type ErrorInfo struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}

type HealthResponse struct {
	Status       string            `json:"status"`
	Service      string            `json:"service"`
	Timestamp    string            `json:"timestamp"`
	Dependencies map[string]string `json:"dependencies"`
}

type VacacionEventEnvelope struct {
	EventID   string      `json:"eventId"`
	EventType string      `json:"eventType"`
	Timestamp string      `json:"timestamp"`
	Source    string      `json:"source"`
	Version   string      `json:"version"`
	Payload   interface{} `json:"payload"`
}

type VacacionEventPayload struct {
	VacacionID  string `json:"vacacionId"`
	EmpleadoID  string `json:"empleadoId"`
	Email       string `json:"email"`
	Nombre      string `json:"nombre"`
	FechaInicio string `json:"fechaInicio"`
	FechaFin    string `json:"fechaFin"`
}
