const express = require('express');
const router = express.Router();
const config = require('../config');
const { forwardRequest } = require('../proxy/httpProxy');
const { authMiddleware } = require('../middleware/auth.middleware');

// Todas las rutas de vacations requieren autenticacion
router.use(authMiddleware);

function buildHeaders(req) {
  return {
    'Content-Type': 'application/json',
    'X-Empleado-Id': req.empleadoId,
    'X-Rol': req.rol,
  };
}

// POST /vacations — programar vacaciones
router.post('/', async (req, res, next) => {
  try {
    const response = await forwardRequest(
      'POST',
      `${config.services.VACACIONES_URL}/vacations`,
      req.body,
      buildHeaders(req)
    );
    return res.status(response.status).json(response.data);
  } catch (error) {
    next(error);
  }
});

// GET /vacations — listar vacaciones (con filtro opcional por empleadoId)
router.get('/', async (req, res, next) => {
  try {
    let url = `${config.services.VACACIONES_URL}/vacations`;
    if (req.query.empleadoId) {
      url += `?empleadoId=${req.query.empleadoId}`;
    }

    const response = await forwardRequest('GET', url, null, buildHeaders(req));
    return res.status(response.status).json(response.data);
  } catch (error) {
    next(error);
  }
});

// GET /vacations/:id — obtener una vacacion por id
router.get('/:id', async (req, res, next) => {
  try {
    const { id } = req.params;
    const response = await forwardRequest(
      'GET',
      `${config.services.VACACIONES_URL}/vacations/${id}`,
      null,
      buildHeaders(req)
    );
    return res.status(response.status).json(response.data);
  } catch (error) {
    next(error);
  }
});

// DELETE /vacations/:id — cancelar vacacion
router.delete('/:id', async (req, res, next) => {
  try {
    const { id } = req.params;
    const response = await forwardRequest(
      'DELETE',
      `${config.services.VACACIONES_URL}/vacations/${id}`,
      null,
      buildHeaders(req)
    );
    return res.status(response.status).json(response.data);
  } catch (error) {
    next(error);
  }
});

module.exports = router;
