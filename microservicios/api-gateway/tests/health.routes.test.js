const request = require('supertest');
const nock = require('nock');
const config = require('../src/config');
const app = require('../src/app');

describe('Health Routes', () => {
  afterEach(() => {
    nock.cleanAll();
  });

  describe('GET /health', () => {
    it('debe retornar 200 con estado UP y dependencias', async () => {
      // Mockear todos los servicios como UP
      nock(config.services.AUTH_URL)
        .get('/health')
        .reply(200, { status: 'UP' });
      nock(config.services.EMPLEADOS_URL)
        .get('/health')
        .reply(200, { status: 'UP' });
      nock(config.services.PERFILES_URL)
        .get('/health')
        .reply(200, { status: 'UP' });
      nock(config.services.VACACIONES_URL)
        .get('/health')
        .reply(200, { status: 'UP' });
      nock(config.services.NOTIFICACIONES_URL)
        .get('/health')
        .reply(200, { status: 'UP' });

      const res = await request(app)
        .get('/health')
        .expect('Content-Type', /json/);

      expect(res.status).toBe(200);
      expect(res.body.status).toBe('UP');
      expect(res.body.service).toBe('api-gateway');
      expect(res.body.version).toBe('1.0.0');
      expect(res.body.dependencies).toBeDefined();
      expect(res.body.dependencies.autenticacion).toBe('UP');
      expect(res.body.dependencies['gestion-empleados']).toBe('UP');
      expect(res.body.dependencies['gestion-perfiles']).toBe('UP');
      expect(res.body.dependencies['gestion-vacaciones']).toBe('UP');
      expect(res.body.dependencies.notificaciones).toBe('UP');
    });

    it('debe reportar DOWN cuando un servicio falla', async () => {
      nock(config.services.AUTH_URL)
        .get('/health')
        .reply(200, { status: 'UP' });
      nock(config.services.EMPLEADOS_URL)
        .get('/health')
        .reply(200, { status: 'UP' });
      nock(config.services.PERFILES_URL)
        .get('/health')
        .replyWithError({ code: 'ECONNREFUSED' });
      nock(config.services.VACACIONES_URL)
        .get('/health')
        .reply(200, { status: 'UP' });
      nock(config.services.NOTIFICACIONES_URL)
        .get('/health')
        .reply(200, { status: 'UP' });

      const res = await request(app)
        .get('/health')
        .expect('Content-Type', /json/);

      expect(res.status).toBe(200);
      expect(res.body.status).toBe('UP');
      expect(res.body.dependencies['gestion-perfiles']).toBe('DOWN');
      expect(res.body.dependencies.autenticacion).toBe('UP');
    });
  });

  describe('GET /metrics', () => {
    it('debe retornar metricas en formato Prometheus', async () => {
      const res = await request(app)
        .get('/metrics');

      expect(res.status).toBe(200);
      expect(res.headers['content-type']).toContain('text/plain');
    });
  });

  describe('404 para rutas no encontradas', () => {
    it('debe retornar 404 para rutas inexistentes', async () => {
      const res = await request(app)
        .get('/ruta-inexistente')
        .expect('Content-Type', /json/);

      expect(res.status).toBe(404);
      expect(res.body.success).toBe(false);
      expect(res.body.error.code).toBe('RUTA_NO_ENCONTRADA');
    });
  });
});
