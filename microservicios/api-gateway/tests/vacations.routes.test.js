const request = require('supertest');
const nock = require('nock');
const config = require('../src/config');
const app = require('../src/app');

describe('Vacations Routes', () => {
  const validToken = 'Bearer eyJhbGciOiJIUzI1NiJ9.vacation-token';
  const empleadoId = 'c3d4e5f6-a7b8-9012-cdef-123456789012';
  const vacationId = 'd4e5f6a7-b8c9-0123-defa-234567890123';

  beforeEach(() => {
    nock(config.services.AUTH_URL)
      .post('/auth/validate', { token: 'eyJhbGciOiJIUzI1NiJ9.vacation-token' })
      .reply(200, {
        valid: true,
        empleadoId,
        rol: 'EMPLEADO',
        username: 'empleado@empresa.com',
      });
  });

  afterEach(() => {
    nock.cleanAll();
  });

  describe('POST /vacations', () => {
    const createPayload = {
      empleadoId,
      fechaInicio: '2024-07-01',
      fechaFin: '2024-07-15',
    };

    it('debe programar vacaciones exitosamente', async () => {
      nock(config.services.VACACIONES_URL)
        .post('/vacations', createPayload)
        .reply(201, {
          success: true,
          data: {
            id: vacationId,
            empleadoId,
            fechaInicio: '2024-07-01',
            fechaFin: '2024-07-15',
            estado: 'PROGRAMADA',
          },
          timestamp: new Date().toISOString(),
        });

      const res = await request(app)
        .post('/vacations')
        .set('Authorization', validToken)
        .send(createPayload)
        .expect('Content-Type', /json/);

      expect(res.status).toBe(201);
      expect(res.body.success).toBe(true);
      expect(res.body.data.estado).toBe('PROGRAMADA');
    });

    it('debe retornar 400 si fechaFin es anterior a fechaInicio', async () => {
      const invalidPayload = {
        empleadoId,
        fechaInicio: '2024-07-15',
        fechaFin: '2024-07-01',
      };

      nock(config.services.VACACIONES_URL)
        .post('/vacations', invalidPayload)
        .reply(400, {
          success: false,
          error: {
            code: 'FECHA_INVALIDA',
            message: 'La fecha fin debe ser posterior a la fecha de inicio',
          },
          timestamp: new Date().toISOString(),
        });

      const res = await request(app)
        .post('/vacations')
        .set('Authorization', validToken)
        .send(invalidPayload)
        .expect('Content-Type', /json/);

      expect(res.status).toBe(400);
      expect(res.body.error.code).toBe('FECHA_INVALIDA');
    });
  });

  describe('GET /vacations', () => {
    it('debe listar todas las vacaciones', async () => {
      nock(config.services.VACACIONES_URL)
        .get('/vacations')
        .reply(200, {
          success: true,
          data: [
            {
              id: vacationId,
              empleadoId,
              fechaInicio: '2024-07-01',
              fechaFin: '2024-07-15',
              estado: 'PROGRAMADA',
            },
          ],
          timestamp: new Date().toISOString(),
        });

      const res = await request(app)
        .get('/vacations')
        .set('Authorization', validToken)
        .expect('Content-Type', /json/);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(Array.isArray(res.body.data)).toBe(true);
    });

    it('debe filtrar por empleadoId', async () => {
      nock(config.services.VACACIONES_URL)
        .get(`/vacations?empleadoId=${empleadoId}`)
        .reply(200, {
          success: true,
          data: [
            {
              id: vacationId,
              empleadoId,
              fechaInicio: '2024-07-01',
              fechaFin: '2024-07-15',
              estado: 'PROGRAMADA',
            },
          ],
          timestamp: new Date().toISOString(),
        });

      const res = await request(app)
        .get(`/vacations?empleadoId=${empleadoId}`)
        .set('Authorization', validToken)
        .expect('Content-Type', /json/);

      expect(res.status).toBe(200);
      expect(res.body.data.length).toBe(1);
    });
  });

  describe('GET /vacations/:id', () => {
    it('debe retornar una vacacion por su id', async () => {
      nock(config.services.VACACIONES_URL)
        .get(`/vacations/${vacationId}`)
        .reply(200, {
          success: true,
          data: {
            id: vacationId,
            empleadoId,
            fechaInicio: '2024-07-01',
            fechaFin: '2024-07-15',
            estado: 'PROGRAMADA',
          },
          timestamp: new Date().toISOString(),
        });

      const res = await request(app)
        .get(`/vacations/${vacationId}`)
        .set('Authorization', validToken)
        .expect('Content-Type', /json/);

      expect(res.status).toBe(200);
      expect(res.body.data.id).toBe(vacationId);
    });

    it('debe retornar 404 si la vacacion no existe', async () => {
      const idInexistente = '00000000-0000-0000-0000-000000000000';

      nock(config.services.VACACIONES_URL)
        .get(`/vacations/${idInexistente}`)
        .reply(404, {
          success: false,
          error: {
            code: 'VACACION_NO_ENCONTRADA',
            message: `La vacacion con id ${idInexistente} no existe`,
          },
          timestamp: new Date().toISOString(),
        });

      const res = await request(app)
        .get(`/vacations/${idInexistente}`)
        .set('Authorization', validToken)
        .expect('Content-Type', /json/);

      expect(res.status).toBe(404);
    });
  });

  describe('DELETE /vacations/:id', () => {
    it('debe cancelar una vacacion exitosamente', async () => {
      nock(config.services.VACACIONES_URL)
        .delete(`/vacations/${vacationId}`)
        .reply(204);

      const res = await request(app)
        .delete(`/vacations/${vacationId}`)
        .set('Authorization', validToken);

      expect(res.status).toBe(204);
    });
  });
});
