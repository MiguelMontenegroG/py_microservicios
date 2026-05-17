package database

import (
	"database/sql"
	"fmt"
	"time"

	_ "github.com/lib/pq"
	"github.com/rs/zerolog"

	"github.com/empresa/gestion-vacaciones/internal/config"
)

func Connect(cfg *config.Config, logger zerolog.Logger) (*sql.DB, error) {
	db, err := sql.Open("postgres", cfg.DSN())
	if err != nil {
		return nil, fmt.Errorf("error abriendo conexion a BD: %w", err)
	}

	db.SetMaxOpenConns(25)
	db.SetMaxIdleConns(5)
	db.SetConnMaxLifetime(5 * time.Minute)

	if err := db.Ping(); err != nil {
		return nil, fmt.Errorf("error haciendo ping a BD: %w", err)
	}

	logger.Info().Msg("Conexion a PostgreSQL establecida exitosamente")
	return db, nil
}

func RunMigrations(db *sql.DB, logger zerolog.Logger) error {
	query := `
	CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

	CREATE TABLE IF NOT EXISTS vacaciones (
		id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
		empleado_id UUID NOT NULL,
		fecha_inicio DATE NOT NULL,
		fecha_fin DATE NOT NULL,
		estado VARCHAR(20) NOT NULL DEFAULT 'PROGRAMADA',
		created_at TIMESTAMP NOT NULL DEFAULT NOW(),
		CONSTRAINT chk_estado CHECK (estado IN ('PROGRAMADA', 'ACTIVA', 'COMPLETADA', 'CANCELADA')),
		CONSTRAINT chk_fechas CHECK (fecha_fin >= fecha_inicio)
	);

	CREATE INDEX IF NOT EXISTS idx_vacaciones_empleado_id ON vacaciones(empleado_id);
	CREATE INDEX IF NOT EXISTS idx_vacaciones_estado ON vacaciones(estado);
	`

	_, err := db.Exec(query)
	if err != nil {
		return fmt.Errorf("error ejecutando migraciones: %w", err)
	}

	logger.Info().Msg("Migraciones de BD ejecutadas exitosamente")
	return nil
}
