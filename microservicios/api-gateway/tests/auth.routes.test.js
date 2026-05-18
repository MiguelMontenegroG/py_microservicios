const request = require('supertest');
const nock = require('nock');
const config = require('../src/config');
const app = require('../src/app');

describe('Auth Routes', () => {
  afterEach(() => {
    nock.cleanAll();
  });

  describe('POST /auth/login', () => {
    const loginPayload = {
      username: 'admin@empresa.com',
      password: 'Admin123!',
    };

    it('debe retornar 200 con token cuando las credenciales son correctas', async () => {
      const mockResponse = {
        success: true,
        data: {
          token: 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbkBlbXByZXNhLmNvbSJ9.test',
          expiresIn: 86400,
        },
        timestamp: new Date().toISOString(),
      };

      nock(config.services.AUTH_URL)
        .post('/auth/login', loginPayload)
        .reply(200, mockResponse);

      const res = await request(app)
        .post('/auth/login')
        .send(loginPayload)
        .expect('Content-Type', /json/);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.data.token).toBeDefined();
      expect(res.body.data.expiresIn).toBe(86400);
    });

    it('debe retornar 401 cuando las credenciales son invalidas', async () => {
      nock(config.services.AUTH_URL)
        .post('/auth/login', loginPayload)
        .reply(401, {
          success: false,
          error: {
            code: 'CREDENCIALES_INVALIDAS',
            message: 'Usuario o contrasena incorrectos',
          },
          timestamp: new Date().toISOString(),
        });

      const res = await request(app)
        .post('/auth/login')
        .send(loginPayload)
        .expect('Content-Type', /json/);

      expect(res.status).toBe(401);
      expect(res.body.success).toBe(false);
      expect(res.body.error.code).toBe('CREDENCIALES_INVALIDAS');
    });

    it('debe retornar 502 cuando el servicio de autenticacion no esta disponible', async () => {
      nock(config.services.AUTH_URL)
        .post('/auth/login', loginPayload)
        .replyWithError({ code: 'ECONNREFUSED' });

      const res = await request(app)
        .post('/auth/login')
        .send(loginPayload)
        .expect('Content-Type', /json/);

      expect(res.status).toBe(502);
      expect(res.body.success).toBe(false);
      expect(res.body.error.code).toBe('SERVICIO_NO_DISPONIBLE');
    });

    it('debe retornar 400 cuando no se envia cuerpo', async () => {
      const res = await request(app)
        .post('/auth/login')
        .send({})
        .expect('Content-Type', /json/);

      // El gateway reenvia al microservicio, que devolvera 400
      // pero mockeamos que el auth service devuelve 400
      nock(config.services.AUTH_URL)
        .post('/auth/login', {})
        .reply(400, {
          success: false,
          error: {
            code: 'ERROR_LECTURA_JSON',
            message: 'Formato de solicitud invalido',
          },
          timestamp: new Date().toISOString(),
        });

      const res2 = await request(app)
        .post('/auth/login')
        .send({})
        .expect('Content-Type', /json/);

      expect(res2.status).toBe(400);
    });
  });

  describe('POST /auth/change-password (protegida)', () => {
    const validToken = 'Bearer eyJhbGciOiJIUzI1NiJ9.valid-token';

    it('debe retornar 401 si no se envia token', async () => {
      const res = await request(app)
        .post('/auth/change-password')
        .send({
          currentPassword: 'OldPass123!',
          newPassword: 'NewPass456!',
        })
        .expect('Content-Type', /json/);

      expect(res.status).toBe(401);
      expect(res.body.error.code).toBe('TOKEN_NO_PROVEIDO');
    });

    it('debe retornar 401 si el token es invalido', async () => {
      nock(config.services.AUTH_URL)
        .post('/auth/validate', { token: 'eyJhbGciOiJIUzI1NiJ9.valid-token' })
        .reply(200, {
          success: true,
          data: { valid: false, reason: 'Token expirado' },
          timestamp: new Date().toISOString(),
        });

      const res = await request(app)
        .post('/auth/change-password')
        .set('Authorization', validToken)
        .send({
          currentPassword: 'OldPass123!',
          newPassword: 'NewPass456!',
        })
        .expect('Content-Type', /json/);

      expect(res.status).toBe(401);
      expect(res.body.error.code).toBe('TOKEN_INVALIDO');
    });

    it('debe retornar 200 si el token es valido y la contrasena se cambia', async () => {
      // Mock de validacion de token exitosa
      nock(config.services.AUTH_URL)
        .post('/auth/validate', { token: 'eyJhbGciOiJIUzI1NiJ9.valid-token' })
        .reply(200, {
          success: true,
          data: {
            valid: true,
            empleadoId: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
            rol: 'EMPLEADO',
          },
          timestamp: new Date().toISOString(),
        });

      // Mock de cambio de password exitoso
      nock(config.services.AUTH_URL)
        .post('/auth/change-password', {
          currentPassword: 'OldPass123!',
          newPassword: 'NewPass456!',
        })
        .reply(200, {
          success: true,
          data: { message: 'Contrasena actualizada exitosamente' },
          timestamp: new Date().toISOString(),
        });

      const res = await request(app)
        .post('/auth/change-password')
        .set('Authorization', validToken)
        .send({
          currentPassword: 'OldPass123!',
          newPassword: 'NewPass456!',
        })
        .expect('Content-Type', /json/);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
    });

    it('debe retornar 502 si el servicio de autenticacion no responde al validar token', async () => {
      nock(config.services.AUTH_URL)
        .post('/auth/validate')
        .replyWithError({ code: 'ECONNREFUSED' });

      const res = await request(app)
        .post('/auth/change-password')
        .set('Authorization', validToken)
        .send({
          currentPassword: 'OldPass123!',
          newPassword: 'NewPass456!',
        })
        .expect('Content-Type', /json/);

      expect(res.status).toBe(502);
      expect(res.body.error.code).toBe('SERVICIO_NO_DISPONIBLE');
    });
  });
});
