-- ============================================================
-- Datos de prueba — Solo para entorno de desarrollo
-- ============================================================

-- Empleado administrador inicial
INSERT INTO empleados (id, nombre, apellido, email, numero_empleado, fecha_ingreso, cargo, area, estado)
VALUES (
  '00000000-0000-0000-0000-000000000001',
  'Admin',
  'Sistema',
  'admin@empresa.com',
  'EMP-000',
  '2024-01-01',
  'Administrador',
  'RRHH',
  'ACTIVO'
) ON CONFLICT DO NOTHING;

-- Empleado de prueba
INSERT INTO empleados (id, nombre, apellido, email, numero_empleado, fecha_ingreso, cargo, area, estado)
VALUES (
  '00000000-0000-0000-0000-000000000002',
  'Juan',
  'Pérez',
  'juan.perez@empresa.com',
  'EMP-001',
  '2024-01-15',
  'Desarrollador',
  'TI',
  'ACTIVO'
) ON CONFLICT DO NOTHING;

-- Nota: Las credenciales del admin en auth_db deben crearse manualmente
-- o via el script de seed específico de auth_db.
-- Usuario: admin@empresa.com
-- Password: Admin123! (BCrypt hash debe generarse en el servicio de auth)
