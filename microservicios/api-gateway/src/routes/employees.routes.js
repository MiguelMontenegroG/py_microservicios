const express = require('express');
const axios = require('axios');
const router = express.Router();
const config = require('../config');
const { forwardRequest } = require('../proxy/httpProxy');
const { authMiddleware } = require('../middleware/auth.middleware');
const { logger } = require('../middleware/logger.middleware');

// Todas las rutas de employees requieren autenticacion
router.use(authMiddleware);

function buildHeaders(req) {
  return {
    'Content-Type': 'application/json',
    'X-Empleado-Id': req.empleadoId,
    'X-Rol': req.rol,
  };
}

// GET /employees — listar empleados (paginado)
router.get('/', async (req, res, next) => {
  try {
    const page = req.query.page || '0';
    const size = req.query.size || '10';
    const url = `${config.services.EMPLEADOS_URL}/employees?page=${page}&size=${size}`;

    const response = await forwardRequest('GET', url, null, buildHeaders(req));
    return res.status(response.status).json(response.data);
  } catch (error) {
    next(error);
  }
});

// POST /employees — crear empleado
router.post('/', async (req, res, next) => {
  try {
    const response = await forwardRequest(
      'POST',
      `${config.services.EMPLEADOS_URL}/employees`,
      req.body,
      buildHeaders(req)
    );
    return res.status(response.status).json(response.data);
  } catch (error) {
    next(error);
  }
});

// GET /employees/:id — composite: empleado + perfil
router.get('/:id', async (req, res, next) => {
  try {
    const { id } = req.params;
    const headers = buildHeaders(req);

    const [empleadoResult, perfilResult] = await Promise.allSettled([
      axios.get(`${config.services.EMPLEADOS_URL}/employees/${id}`, {
        headers,
        timeout: config.requestTimeout,
      }),
      axios.get(`${config.services.PERFILES_URL}/profiles/${id}`, {
        headers,
        timeout: config.requestTimeout,
      }),
    ]);

    // Si empleado no existe → 404
    if (empleadoResult.status === 'rejected') {
      const error = empleadoResult.reason;
      if (error.response && error.response.status === 404) {
        return res.status(404).json({
          success: false,
          error: {
            code: 'EMPLEADO_NO_ENCONTRADO',
            message: `El empleado con id ${id} no existe`,
          },
          timestamp: new Date().toISOString(),
        });
      }

      logger.error({
        message: 'Error al obtener empleado en endpoint composite',
        empleadoId: id,
        error: error.message,
        service: 'api-gateway',
      });
      return res.status(502).json({
        success: false,
        error: {
          code: 'SERVICIO_NO_DISPONIBLE',
          message: 'El servicio de empleados no esta disponible',
        },
        timestamp: new Date().toISOString(),
      });
    }

    const empleadoData = empleadoResult.value.data;

    // Perfil puede fallar — no es crítico, devolvemos null
    let perfilData = null;
    if (perfilResult.status === 'fulfilled' && perfilResult.value.data.success) {
      perfilData = perfilResult.value.data.data || null;
    }

    return res.json({
      success: true,
      data: {
        ...empleadoData,
        perfil: perfilData,
      },
      timestamp: new Date().toISOString(),
    });
  } catch (error) {
    next(error);
  }
});

// PUT /employees/:id — actualizar empleado
router.put('/:id', async (req, res, next) => {
  try {
    const { id } = req.params;
    const response = await forwardRequest(
      'PUT',
      `${config.services.EMPLEADOS_URL}/employees/${id}`,
      req.body,
      buildHeaders(req)
    );
    return res.status(response.status).json(response.data);
  } catch (error) {
    next(error);
  }
});

// DELETE /employees/:id — soft delete
router.delete('/:id', async (req, res, next) => {
  try {
    const { id } = req.params;
    const response = await forwardRequest(
      'DELETE',
      `${config.services.EMPLEADOS_URL}/employees/${id}`,
      null,
      buildHeaders(req)
    );
    return res.status(response.status).json(response.data);
  } catch (error) {
    next(error);
  }
});

module.exports = router;
