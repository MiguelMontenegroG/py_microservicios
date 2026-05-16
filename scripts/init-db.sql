-- ============================================================
-- Script de inicialización de bases de datos
-- Se ejecuta automáticamente en el contenedor db-empleados
-- ============================================================

-- Extensión para UUID
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ─── TABLA EMPLEADOS ───────────────────────────────────────
CREATE TABLE IF NOT EXISTS empleados (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  nombre VARCHAR(100) NOT NULL,
  apellido VARCHAR(100) NOT NULL,
  email VARCHAR(255) UNIQUE NOT NULL,
  numero_empleado VARCHAR(50) UNIQUE NOT NULL,
  fecha_ingreso DATE NOT NULL,
  cargo VARCHAR(100),
  area VARCHAR(100),
  estado VARCHAR(20) DEFAULT 'ACTIVO'
    CHECK (estado IN ('ACTIVO', 'EN_VACACIONES', 'RETIRADO')),
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW()
);

-- ─── TABLA USUARIOS (auth_db — este script se adapta allá) ─
-- Este bloque es solo referencia; auth_db tiene su propia instancia
/*
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS usuarios (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  empleado_id UUID UNIQUE NOT NULL,
  username VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  activo BOOLEAN DEFAULT TRUE,
  rol VARCHAR(20) DEFAULT 'EMPLEADO'
    CHECK (rol IN ('EMPLEADO', 'ADMIN')),
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
*/

-- ─── TABLA VACACIONES (vacaciones_db — referencia) ──────────
/*
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE IF NOT EXISTS vacaciones (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  empleado_id UUID NOT NULL,
  fecha_inicio DATE NOT NULL,
  fecha_fin DATE NOT NULL,
  estado VARCHAR(20) DEFAULT 'PROGRAMADA'
    CHECK (estado IN ('PROGRAMADA', 'ACTIVA', 'COMPLETADA', 'CANCELADA')),
  created_at TIMESTAMP DEFAULT NOW(),
  CONSTRAINT no_solapamiento EXCLUDE USING gist (
    empleado_id WITH =,
    daterange(fecha_inicio, fecha_fin, '[]') WITH &&
  )
);
*/
