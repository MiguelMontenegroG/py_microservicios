const axios = require('axios');
const config = require('../config');
const { logger } = require('../middleware/logger.middleware');

function createTimeout() {
  return new Promise((_, reject) =>
    setTimeout(() => reject(new Error('Gateway Timeout')), config.requestTimeout)
  );
}

async function forwardRequest(method, url, data = null, headers = {}, req = null) {
  const axiosConfig = {
    method: method.toLowerCase(),
    url,
    timeout: config.requestTimeout,
    headers: {
      'Content-Type': 'application/json',
      ...headers,
    },
    validateStatus: () => true,
  };

  if (data && (method.toLowerCase() === 'post' || method.toLowerCase() === 'put')) {
    axiosConfig.data = data;
  }

  try {
    const response = await Promise.race([
      axios(axiosConfig),
      createTimeout(),
    ]);

    return response;
  } catch (error) {
    if (error.message === 'Gateway Timeout') {
      logger.warn({
        message: 'Timeout en llamada a microservicio',
        method,
        url,
        service: 'api-gateway',
      });
      return {
        status: 504,
        data: {
          success: false,
          error: {
            code: 'GATEWAY_TIMEOUT',
            message: `El servicio no respondio en ${config.requestTimeout / 1000}s`,
          },
          timestamp: new Date().toISOString(),
        },
      };
    }

    if (error.code === 'ECONNREFUSED' || error.code === 'ECONNRESET') {
      logger.error({
        message: 'Servicio no disponible',
        method,
        url,
        error: error.message,
        service: 'api-gateway',
      });
      return {
        status: 502,
        data: {
          success: false,
          error: {
            code: 'SERVICIO_NO_DISPONIBLE',
            message: 'El servicio destino no esta disponible',
          },
          timestamp: new Date().toISOString(),
        },
      };
    }

    logger.error({
      message: 'Error en forwarding de request',
      method,
      url,
      error: error.message,
      service: 'api-gateway',
    });
    return {
      status: 500,
      data: {
        success: false,
        error: {
          code: 'ERROR_INTERNO',
          message: 'Error interno del gateway',
        },
        timestamp: new Date().toISOString(),
      },
    };
  }
}

module.exports = { forwardRequest };
