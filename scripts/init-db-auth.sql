-- ============================================================
-- Script de inicialización para auth_db
-- Incluye la tabla de tokens de recuperacion de contrasena
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS usuarios (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  empleado_id UUID UNIQUE NOT NULL,
  username VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  activo BOOLEAN DEFAULT TRUE,
  rol VARCHAR(20) DEFAULT 'EMPLEADO'
    CHECK (rol IN ('EMPLEADO', 'ADMIN')),
  es_primer_acceso BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS audit_log (
  id BIGSERIAL PRIMARY KEY,
  empleado_id UUID,
  accion VARCHAR(100),
  detalle TEXT,
  ip_address VARCHAR(45),
  timestamp TIMESTAMP DEFAULT NOW()
);

-- Tabla para tokens de recuperacion de contrasena
CREATE TABLE IF NOT EXISTS password_reset_tokens (
  id BIGSERIAL PRIMARY KEY,
  empleado_id UUID NOT NULL,
  email VARCHAR(255) NOT NULL,
  codigo VARCHAR(6) NOT NULL,
  utilizado BOOLEAN DEFAULT FALSE,
  expira_en TIMESTAMP NOT NULL,
  created_at TIMESTAMP DEFAULT NOW(),
  CONSTRAINT fk_empleado FOREIGN KEY (empleado_id) REFERENCES usuarios(empleado_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_reset_tokens_email ON password_reset_tokens(email);
CREATE INDEX IF NOT EXISTS idx_reset_tokens_codigo ON password_reset_tokens(codigo);
