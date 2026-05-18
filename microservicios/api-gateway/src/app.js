const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const swaggerUi = require('swagger-ui-express');
const YAML = require('yamljs');
const path = require('path');
const client = require('prom-client');
const axios = require('axios');

const config = require('./config');
const { requestLogger, logger } = require('./middleware/logger.middleware');
const { errorMiddleware } = require('./middleware/error.middleware');

const authRoutes = require('./routes/auth.routes');
const employeesRoutes = require('./routes/employees.routes');
const profileRoutes = require('./routes/profile.routes');
const vacationsRoutes = require('./routes/vacations.routes');

const app = express();

// ============================================================
// Middleware globales
// ============================================================
app.use(helmet());
app.use(cors());
app.use(express.json({ limit: '10mb' }));
app.use(requestLogger);

// ============================================================
// Metricas Prometheus
// ============================================================
client.collectDefaultMetrics({ prefix: 'gateway_' });

const httpRequestDuration = new client.Histogram({
  name: 'gateway_http_request_duration_seconds',
  help: 'Duracion de requests HTTP en segundos',
  labelNames: ['method', 'route', 'status'],
  buckets: [0.01, 0.05, 0.1, 0.5, 1, 2, 5],
});

const httpRequestTotal = new client.Counter({
  name: 'gateway_http_requests_total',
  help: 'Total de requests HTTP',
  labelNames: ['method', 'route', 'status'],
});

// Middleware de metricas
app.use((req, res, next) => {
  const end = httpRequestDuration.startTimer();
  res.on('finish', () => {
    const route = req.route ? req.route.path : req.path;
    const labels = { method: req.method, route, status: res.statusCode };
    end(labels);
    httpRequestTotal.inc(labels);
  });
  next();
});

// ============================================================
// Swagger UI
// ============================================================
let swaggerDoc;
try {
  swaggerDoc = YAML.load(path.join(__dirname, '..', 'openapi.yaml'));
  app.use('/api-docs', swaggerUi.serve, swaggerUi.setup(swaggerDoc));
  logger.info('Swagger UI montado en /api-docs');
} catch (err) {
  logger.warn({
    message: 'No se pudo cargar openapi.yaml para Swagger UI',
    error: err.message,
    service: 'api-gateway',
  });
}

// ============================================================
// Health Check
// ============================================================
app.get('/health', async (_req, res) => {
  const services = {
    autenticacion: config.services.AUTH_URL,
    'gestion-empleados': config.services.EMPLEADOS_URL,
    'gestion-perfiles': config.services.PERFILES_URL,
    'gestion-vacaciones': config.services.VACACIONES_URL,
    notificaciones: config.services.NOTIFICACIONES_URL,
  };

  const dependencies = {};

  await Promise.allSettled(
    Object.entries(services).map(async ([name, url]) => {
      try {
        await axios.get(`${url}/health`, { timeout: 2000 });
        dependencies[name] = 'UP';
      } catch {
        dependencies[name] = 'DOWN';
      }
    })
  );

  // En caso de que alguna promesa falle inesperadamente
  Object.keys(services).forEach((name) => {
    if (!dependencies[name]) {
      dependencies[name] = 'DOWN';
    }
  });

  return res.json({
    status: 'UP',
    service: 'api-gateway',
    version: '1.0.0',
    timestamp: new Date().toISOString(),
    dependencies,
  });
});

// ============================================================
// Metricas endpoint
// ============================================================
app.get('/metrics', async (_req, res) => {
  try {
    res.set('Content-Type', client.register.contentType);
    res.send(await client.register.metrics());
  } catch (err) {
    logger.error({
      message: 'Error al generar metricas Prometheus',
      error: err.message,
      service: 'api-gateway',
    });
    res.status(500).json({
      success: false,
      error: {
        code: 'ERROR_METRICAS',
        message: 'Error al generar metricas',
      },
      timestamp: new Date().toISOString(),
    });
  }
});

// ============================================================
// Rutas
// ============================================================
app.use('/auth', authRoutes);
app.use('/employees', employeesRoutes);
app.use('/profile', profileRoutes);
app.use('/vacations', vacationsRoutes);

// ============================================================
// 404 para rutas no encontradas
// ============================================================
app.use((_req, res) => {
  res.status(404).json({
    success: false,
    error: {
      code: 'RUTA_NO_ENCONTRADA',
      message: 'La ruta solicitada no existe',
    },
    timestamp: new Date().toISOString(),
  });
});

// ============================================================
// Middleware de errores (debe ir al final)
// ============================================================
app.use(errorMiddleware);

module.exports = app;
