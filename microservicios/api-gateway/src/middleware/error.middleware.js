const { logger } = require('./logger.middleware');

function errorMiddleware(err, req, res, _next) {
  logger.error({
    message: 'Error no manejado en el gateway',
    method: req.method,
    url: req.originalUrl,
    error: err.message,
    stack: err.stack,
    service: 'api-gateway',
  });

  // Error de sintaxis JSON
  if (err.type === 'entity.parse.failed') {
    return res.status(400).json({
      success: false,
      error: {
        code: 'ERROR_LECTURA_JSON',
        message: 'Formato de solicitud invalido: JSON mal formado',
      },
      timestamp: new Date().toISOString(),
    });
  }

  return res.status(err.status || 500).json({
    success: false,
    error: {
      code: err.code || 'ERROR_INTERNO',
      message: err.message || 'Error interno del servidor',
    },
    timestamp: new Date().toISOString(),
  });
}

module.exports = { errorMiddleware };
