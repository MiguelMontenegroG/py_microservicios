package repository

import (
	"database/sql"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/rs/zerolog"

	"github.com/empresa/gestion-vacaciones/internal/models"
)

type VacacionesRepository interface {
	Create(vacacion *models.Vacacion) error
	FindByID(id uuid.UUID) (*models.Vacacion, error)
	FindByEmpleadoID(empleadoID uuid.UUID) ([]models.Vacacion, error)
	FindAll() ([]models.Vacacion, error)
	UpdateEstado(id uuid.UUID, estado string) error
	ExistsOverlap(empleadoID uuid.UUID, fechaInicio, fechaFin time.Time, excludeID *uuid.UUID) (bool, error)
	Ping() error
}

type vacacionesRepo struct {
	db     *sql.DB
	logger zerolog.Logger
}

func NewVacacionesRepository(db *sql.DB, logger zerolog.Logger) VacacionesRepository {
	return &vacacionesRepo{
		db:     db,
		logger: logger,
	}
}

func (r *vacacionesRepo) Ping() error {
	return r.db.Ping()
}

func (r *vacacionesRepo) Create(vacacion *models.Vacacion) error {
	query := `
		INSERT INTO vacaciones (id, empleado_id, fecha_inicio, fecha_fin, estado, created_at)
		VALUES ($1, $2, $3, $4, $5, $6)
	`
	_, err := r.db.Exec(query,
		vacacion.ID,
		vacacion.EmpleadoID,
		vacacion.FechaInicio,
		vacacion.FechaFin,
		vacacion.Estado,
		vacacion.CreatedAt,
	)
	if err != nil {
		r.logger.Error().Err(err).Msg("Error insertando vacacion en BD")
		return fmt.Errorf("error creando vacacion: %w", err)
	}
	return nil
}

func (r *vacacionesRepo) FindByID(id uuid.UUID) (*models.Vacacion, error) {
	query := `
		SELECT id, empleado_id, fecha_inicio, fecha_fin, estado, created_at
		FROM vacaciones
		WHERE id = $1
	`
	var v models.Vacacion
	err := r.db.QueryRow(query, id).Scan(
		&v.ID, &v.EmpleadoID, &v.FechaInicio, &v.FechaFin, &v.Estado, &v.CreatedAt,
	)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		r.logger.Error().Err(err).Str("id", id.String()).Msg("Error buscando vacacion por ID")
		return nil, fmt.Errorf("error buscando vacacion: %w", err)
	}
	return &v, nil
}

func (r *vacacionesRepo) FindByEmpleadoID(empleadoID uuid.UUID) ([]models.Vacacion, error) {
	query := `
		SELECT id, empleado_id, fecha_inicio, fecha_fin, estado, created_at
		FROM vacaciones
		WHERE empleado_id = $1
		ORDER BY created_at DESC
	`
	rows, err := r.db.Query(query, empleadoID)
	if err != nil {
		r.logger.Error().Err(err).Str("empleadoId", empleadoID.String()).Msg("Error listando vacaciones por empleado")
		return nil, fmt.Errorf("error listando vacaciones: %w", err)
	}
	defer rows.Close()

	return scanVacaciones(rows)
}

func (r *vacacionesRepo) FindAll() ([]models.Vacacion, error) {
	query := `
		SELECT id, empleado_id, fecha_inicio, fecha_fin, estado, created_at
		FROM vacaciones
		ORDER BY created_at DESC
	`
	rows, err := r.db.Query(query)
	if err != nil {
		r.logger.Error().Err(err).Msg("Error listando todas las vacaciones")
		return nil, fmt.Errorf("error listando vacaciones: %w", err)
	}
	defer rows.Close()

	return scanVacaciones(rows)
}

func (r *vacacionesRepo) UpdateEstado(id uuid.UUID, estado string) error {
	query := `UPDATE vacaciones SET estado = $1 WHERE id = $2`
	result, err := r.db.Exec(query, estado, id)
	if err != nil {
		r.logger.Error().Err(err).Str("id", id.String()).Str("estado", estado).Msg("Error actualizando estado")
		return fmt.Errorf("error actualizando estado de vacacion: %w", err)
	}

	rowsAffected, _ := result.RowsAffected()
	if rowsAffected == 0 {
		return fmt.Errorf("vacacion con id %s no encontrada", id.String())
	}

	return nil
}

func (r *vacacionesRepo) ExistsOverlap(empleadoID uuid.UUID, fechaInicio, fechaFin time.Time, excludeID *uuid.UUID) (bool, error) {
	query := `
		SELECT COUNT(*) FROM vacaciones
		WHERE empleado_id = $1
		AND estado NOT IN ('CANCELADA', 'COMPLETADA')
		AND (fecha_inicio, fecha_fin) OVERLAPS ($2::date, $3::date)
	`
	args := []interface{}{empleadoID, fechaInicio, fechaFin}

	if excludeID != nil {
		query += ` AND id != $4`
		args = append(args, *excludeID)
	}

	var count int
	err := r.db.QueryRow(query, args...).Scan(&count)
	if err != nil {
		r.logger.Error().Err(err).Msg("Error verificando solapamiento")
		return false, fmt.Errorf("error verificando solapamiento: %w", err)
	}

	return count > 0, nil
}

func scanVacaciones(rows *sql.Rows) ([]models.Vacacion, error) {
	var vacaciones []models.Vacacion
	for rows.Next() {
		var v models.Vacacion
		if err := rows.Scan(&v.ID, &v.EmpleadoID, &v.FechaInicio, &v.FechaFin, &v.Estado, &v.CreatedAt); err != nil {
			return nil, fmt.Errorf("error escaneando fila: %w", err)
		}
		vacaciones = append(vacaciones, v)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("error iterando filas: %w", err)
	}
	return vacaciones, nil
}
