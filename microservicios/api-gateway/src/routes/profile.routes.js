const express = require('express');
const router = express.Router();
const config = require('../config');
const { forwardRequest } = require('../proxy/httpProxy');
const { authMiddleware } = require('../middleware/auth.middleware');

// Todas las rutas de profile requieren autenticacion
router.use(authMiddleware);

function buildHeaders(req) {
  return {
    'Content-Type': 'application/json',
    'X-Empleado-Id': req.empleadoId,
    'X-Rol': req.rol,
  };
}

// GET /profile — obtener perfil del empleado autenticado
router.get('/', async (req, res, next) => {
  try {
    const empleadoId = req.empleadoId;
    const response = await forwardRequest(
      'GET',
      `${config.services.PERFILES_URL}/profiles/${empleadoId}`,
      null,
      buildHeaders(req)
    );
    return res.status(response.status).json(response.data);
  } catch (error) {
    next(error);
  }
});

// PUT /profile — actualizar perfil del empleado autenticado
router.put('/', async (req, res, next) => {
  try {
    const empleadoId = req.empleadoId;
    const response = await forwardRequest(
      'PUT',
      `${config.services.PERFILES_URL}/profiles/${empleadoId}`,
      req.body,
      buildHeaders(req)
    );
    return res.status(response.status).json(response.data);
  } catch (error) {
    next(error);
  }
});

module.exports = router;
