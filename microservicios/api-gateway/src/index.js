const app = require('./app');
const config = require('./config');
const { logger } = require('./middleware/logger.middleware');

app.listen(config.PORT, () => {
  logger.info({
    message: `API Gateway iniciado en puerto ${config.PORT}`,
    service: 'api-gateway',
  });
  console.log(`API Gateway corriendo en http://localhost:${config.PORT}`);
});
