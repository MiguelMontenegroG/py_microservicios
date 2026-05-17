package repository

import (
	"database/sql"
	"regexp"
	"testing"
	"time"

	"github.com/DATA-DOG/go-sqlmock"
	"github.com/google/uuid"
	"github.com/rs/zerolog"
	"github.com/stretchr/testify/assert"

	"github.com/empresa/gestion-vacaciones/internal/models"
)

func setupMockDB(t *testing.T) (*sql.DB, sqlmock.Sqlmock, VacacionesRepository) {
	db, mock, err := sqlmock.New()
	if err != nil {
		t.Fatalf("Error creando mock DB: %v", err)
	}
	logger := zerolog.Nop()
	repo := NewVacacionesRepository(db, logger)
	return db, mock, repo
}

func TestPing(t *testing.T) {
	db, mock, repo := setupMockDB(t)
	defer db.Close()

	mock.ExpectPing()

	err := repo.Ping()
	assert.NoError(t, err)
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestCreate(t *testing.T) {
	db, mock, repo := setupMockDB(t)
	defer db.Close()

	now := time.Now().UTC()
	vacacion := &models.Vacacion{
		ID:          uuid.New(),
		EmpleadoID:  uuid.New(),
		FechaInicio: now,
		FechaFin:    now.Add(7 * 24 * time.Hour),
		Estado:      models.EstadoProgramada,
		CreatedAt:   now,
	}

	mock.ExpectExec(regexp.QuoteMeta(`INSERT INTO vacaciones (id, empleado_id, fecha_inicio, fecha_fin, estado, created_at) VALUES ($1, $2, $3, $4, $5, $6)`)).
		WithArgs(vacacion.ID, vacacion.EmpleadoID, vacacion.FechaInicio, vacacion.FechaFin, vacacion.Estado, vacacion.CreatedAt).
		WillReturnResult(sqlmock.NewResult(1, 1))

	err := repo.Create(vacacion)
	assert.NoError(t, err)
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestCreate_Error(t *testing.T) {
	db, mock, repo := setupMockDB(t)
	defer db.Close()

	vacacion := &models.Vacacion{
		ID: uuid.New(),
	}

	mock.ExpectExec(regexp.QuoteMeta(`INSERT INTO vacaciones`)).
		WithArgs(vacacion.ID, vacacion.EmpleadoID, vacacion.FechaInicio, vacacion.FechaFin, vacacion.Estado, vacacion.CreatedAt).
		WillReturnError(sql.ErrConnDone)

	err := repo.Create(vacacion)
	assert.Error(t, err)
	assert.ErrorIs(t, err, sql.ErrConnDone)
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestFindByID_Exitoso(t *testing.T) {
	db, mock, repo := setupMockDB(t)
	defer db.Close()

	id := uuid.New()
	empleadoID := uuid.New()
	now := time.Now().UTC()

	rows := sqlmock.NewRows([]string{"id", "empleado_id", "fecha_inicio", "fecha_fin", "estado", "created_at"}).
		AddRow(id, empleadoID, now, now.Add(7*24*time.Hour), models.EstadoProgramada, now)

	mock.ExpectQuery(regexp.QuoteMeta(`SELECT id, empleado_id, fecha_inicio, fecha_fin, estado, created_at FROM vacaciones WHERE id = $1`)).
		WithArgs(id).
		WillReturnRows(rows)

	v, err := repo.FindByID(id)
	assert.NoError(t, err)
	assert.NotNil(t, v)
	assert.Equal(t, id, v.ID)
	assert.Equal(t, empleadoID, v.EmpleadoID)
	assert.Equal(t, models.EstadoProgramada, v.Estado)
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestFindByID_NoEncontrada(t *testing.T) {
	db, mock, repo := setupMockDB(t)
	defer db.Close()

	id := uuid.New()

	mock.ExpectQuery(regexp.QuoteMeta(`SELECT id, empleado_id, fecha_inicio, fecha_fin, estado, created_at FROM vacaciones WHERE id = $1`)).
		WithArgs(id).
		WillReturnError(sql.ErrNoRows)

	v, err := repo.FindByID(id)
	assert.NoError(t, err)
	assert.Nil(t, v)
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestFindByID_Error(t *testing.T) {
	db, mock, repo := setupMockDB(t)
	defer db.Close()

	id := uuid.New()

	mock.ExpectQuery(regexp.QuoteMeta(`SELECT id, empleado_id, fecha_inicio, fecha_fin, estado, created_at FROM vacaciones WHERE id = $1`)).
		WithArgs(id).
		WillReturnError(sql.ErrConnDone)

	v, err := repo.FindByID(id)
	assert.Error(t, err)
	assert.Nil(t, v)
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestFindByEmpleadoID(t *testing.T) {
	db, mock, repo := setupMockDB(t)
	defer db.Close()

	empleadoID := uuid.New()
	now := time.Now().UTC()

	rows := sqlmock.NewRows([]string{"id", "empleado_id", "fecha_inicio", "fecha_fin", "estado", "created_at"}).
		AddRow(uuid.New(), empleadoID, now, now.Add(7*24*time.Hour), models.EstadoProgramada, now).
		AddRow(uuid.New(), empleadoID, now.Add(14*24*time.Hour), now.Add(21*24*time.Hour), models.EstadoProgramada, now.Add(1*time.Hour))

	mock.ExpectQuery(regexp.QuoteMeta(`SELECT id, empleado_id, fecha_inicio, fecha_fin, estado, created_at FROM vacaciones WHERE empleado_id = $1 ORDER BY created_at DESC`)).
		WithArgs(empleadoID).
		WillReturnRows(rows)

	vacaciones, err := repo.FindByEmpleadoID(empleadoID)
	assert.NoError(t, err)
	assert.Len(t, vacaciones, 2)
	for _, v := range vacaciones {
		assert.Equal(t, empleadoID, v.EmpleadoID)
	}
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestFindByEmpleadoID_Vacio(t *testing.T) {
	db, mock, repo := setupMockDB(t)
	defer db.Close()

	empleadoID := uuid.New()

	rows := sqlmock.NewRows([]string{"id", "empleado_id", "fecha_inicio", "fecha_fin", "estado", "created_at"})

	mock.ExpectQuery(regexp.QuoteMeta(`SELECT id, empleado_id, fecha_inicio, fecha_fin, estado, created_at FROM vacaciones WHERE empleado_id = $1 ORDER BY created_at DESC`)).
		WithArgs(empleadoID).
		WillReturnRows(rows)

	vacaciones, err := repo.FindByEmpleadoID(empleadoID)
	assert.NoError(t, err)
	assert.Len(t, vacaciones, 0)
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestFindAll(t *testing.T) {
	db, mock, repo := setupMockDB(t)
	defer db.Close()

	now := time.Now().UTC()

	rows := sqlmock.NewRows([]string{"id", "empleado_id", "fecha_inicio", "fecha_fin", "estado", "created_at"}).
		AddRow(uuid.New(), uuid.New(), now, now.Add(7*24*time.Hour), models.EstadoProgramada, now).
		AddRow(uuid.New(), uuid.New(), now.Add(10*24*time.Hour), now.Add(17*24*time.Hour), models.EstadoProgramada, now.Add(1*time.Hour))

	mock.ExpectQuery(regexp.QuoteMeta(`SELECT id, empleado_id, fecha_inicio, fecha_fin, estado, created_at FROM vacaciones ORDER BY created_at DESC`)).
		WillReturnRows(rows)

	vacaciones, err := repo.FindAll()
	assert.NoError(t, err)
	assert.Len(t, vacaciones, 2)
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestFindAll_Vacio(t *testing.T) {
	db, mock, repo := setupMockDB(t)
	defer db.Close()

	rows := sqlmock.NewRows([]string{"id", "empleado_id", "fecha_inicio", "fecha_fin", "estado", "created_at"})

	mock.ExpectQuery(regexp.QuoteMeta(`SELECT id, empleado_id, fecha_inicio, fecha_fin, estado, created_at FROM vacaciones ORDER BY created_at DESC`)).
		WillReturnRows(rows)

	vacaciones, err := repo.FindAll()
	assert.NoError(t, err)
	assert.Len(t, vacaciones, 0)
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestUpdateEstado(t *testing.T) {
	db, mock, repo := setupMockDB(t)
	defer db.Close()

	id := uuid.New()

	mock.ExpectExec(regexp.QuoteMeta(`UPDATE vacaciones SET estado = $1 WHERE id = $2`)).
		WithArgs(models.EstadoCancelada, id).
		WillReturnResult(sqlmock.NewResult(0, 1))

	err := repo.UpdateEstado(id, models.EstadoCancelada)
	assert.NoError(t, err)
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestUpdateEstado_NoEncontrada(t *testing.T) {
	db, mock, repo := setupMockDB(t)
	defer db.Close()

	id := uuid.New()

	mock.ExpectExec(regexp.QuoteMeta(`UPDATE vacaciones SET estado = $1 WHERE id = $2`)).
		WithArgs(models.EstadoCancelada, id).
		WillReturnResult(sqlmock.NewResult(0, 0))

	err := repo.UpdateEstado(id, models.EstadoCancelada)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "no encontrada")
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestExistsOverlap_True(t *testing.T) {
	db, mock, repo := setupMockDB(t)
	defer db.Close()

	empleadoID := uuid.New()
	fechaInicio := time.Date(2024, 7, 1, 0, 0, 0, 0, time.UTC)
	fechaFin := time.Date(2024, 7, 15, 0, 0, 0, 0, time.UTC)

	rows := sqlmock.NewRows([]string{"count"}).AddRow(1)

	mock.ExpectQuery(regexp.QuoteMeta(`SELECT COUNT(*) FROM vacaciones WHERE empleado_id = $1 AND estado NOT IN ('CANCELADA', 'COMPLETADA') AND (fecha_inicio, fecha_fin) OVERLAPS ($2::date, $3::date)`)).
		WithArgs(empleadoID, fechaInicio, fechaFin).
		WillReturnRows(rows)

	exists, err := repo.ExistsOverlap(empleadoID, fechaInicio, fechaFin, nil)
	assert.NoError(t, err)
	assert.True(t, exists)
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestExistsOverlap_False(t *testing.T) {
	db, mock, repo := setupMockDB(t)
	defer db.Close()

	empleadoID := uuid.New()
	fechaInicio := time.Date(2024, 7, 1, 0, 0, 0, 0, time.UTC)
	fechaFin := time.Date(2024, 7, 15, 0, 0, 0, 0, time.UTC)

	rows := sqlmock.NewRows([]string{"count"}).AddRow(0)

	mock.ExpectQuery(regexp.QuoteMeta(`SELECT COUNT(*) FROM vacaciones WHERE empleado_id = $1 AND estado NOT IN ('CANCELADA', 'COMPLETADA') AND (fecha_inicio, fecha_fin) OVERLAPS ($2::date, $3::date)`)).
		WithArgs(empleadoID, fechaInicio, fechaFin).
		WillReturnRows(rows)

	exists, err := repo.ExistsOverlap(empleadoID, fechaInicio, fechaFin, nil)
	assert.NoError(t, err)
	assert.False(t, exists)
	assert.NoError(t, mock.ExpectationsWereMet())
}

func TestExistsOverlap_ConExclusion(t *testing.T) {
	db, mock, repo := setupMockDB(t)
	defer db.Close()

	empleadoID := uuid.New()
	excludeID := uuid.New()
	fechaInicio := time.Date(2024, 7, 1, 0, 0, 0, 0, time.UTC)
	fechaFin := time.Date(2024, 7, 15, 0, 0, 0, 0, time.UTC)

	rows := sqlmock.NewRows([]string{"count"}).AddRow(0)

	mock.ExpectQuery(regexp.QuoteMeta(`SELECT COUNT(*) FROM vacaciones WHERE empleado_id = $1 AND estado NOT IN ('CANCELADA', 'COMPLETADA') AND (fecha_inicio, fecha_fin) OVERLAPS ($2::date, $3::date) AND id != $4`)).
		WithArgs(empleadoID, fechaInicio, fechaFin, excludeID).
		WillReturnRows(rows)

	exists, err := repo.ExistsOverlap(empleadoID, fechaInicio, fechaFin, &excludeID)
	assert.NoError(t, err)
	assert.False(t, exists)
	assert.NoError(t, mock.ExpectationsWereMet())
}
