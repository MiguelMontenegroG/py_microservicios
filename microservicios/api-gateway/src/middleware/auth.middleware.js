const axios = require('axios');
const config = require('../config');
const { logger } = require('./logger.middleware');

async function authMiddleware(req, res, next) {
  const authHeader = req.headers.authorization;

  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({
      success: false,
      error: {
        code: 'TOKEN_NO_PROVEIDO',
        message: 'Token de autenticacion no proporcionado',
      },
      timestamp: new Date().toISOString(),
    });
  }

  const token = authHeader.split(' ')[1];

  try {
    const response = await axios.post(
      `${config.services.AUTH_URL}/auth/validate`,
      { token },
      { timeout: 3000 }
    );

    const data = response.data;

    // La respuesta del auth-service tiene estructura { success: true, data: { valid: true, ... } }
    if (!data || !data.data || !data.data.valid) {
      return res.status(401).json({
        success: false,
        error: {
          code: 'TOKEN_INVALIDO',
          message: data?.data?.reason || 'El token proporcionado no es valido',
        },
        timestamp: new Date().toISOString(),
      });
    }

    req.empleadoId = data.data.empleadoId;
    req.rol = data.data.rol || 'EMPLEADO';

    // Agregar headers para microservicios internos
    req.headers['X-Empleado-Id'] = req.empleadoId;
    req.headers['X-Rol'] = req.rol;
    delete req.headers.authorization;

    next();
  } catch (error) {
    if (error.code === 'ECONNABORTED' || error.code === 'ECONNREFUSED') {
      logger.error({
        message: 'Error de conexion al validar token',
        error: error.message,
        service: 'api-gateway',
      });
      return res.status(502).json({
        success: false,
        error: {
          code: 'SERVICIO_NO_DISPONIBLE',
          message: 'El servicio de autenticacion no esta disponible',
        },
        timestamp: new Date().toISOString(),
      });
    }

    if (error.response && error.response.status === 401) {
      return res.status(401).json({
        success: false,
        error: {
          code: 'TOKEN_INVALIDO',
          message: 'El token proporcionado no es valido',
        },
        timestamp: new Date().toISOString(),
      });
    }

    logger.error({
      message: 'Error inesperado al validar token',
      error: error.message,
      service: 'api-gateway',
    });
    return res.status(500).json({
      success: false,
      error: {
        code: 'ERROR_INTERNO',
        message: 'Error interno al validar autenticacion',
      },
      timestamp: new Date().toISOString(),
    });
  }
}

module.exports = { authMiddleware };
