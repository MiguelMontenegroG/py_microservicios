const request = require('supertest');
const nock = require('nock');
const config = require('../src/config');
const app = require('../src/app');

describe('Employees Routes', () => {
  const validToken = 'Bearer eyJhbGciOiJIUzI1NiJ9.valid-token';
  const empleadoId = 'a1b2c3d4-e5f6-7890-abcd-ef1234567890';
  const empleadoData = {
    success: true,
    data: {
      id: empleadoId,
      nombre: 'Juan',
      apellido: 'Perez',
      email: 'juan.perez@empresa.com',
      numeroEmpleado: 'EMP-001',
      cargo: 'Desarrollador Senior',
      area: 'Tecnologia',
      fechaIngreso: '2024-01-15',
      estado: 'ACTIVO',
    },
    timestamp: new Date().toISOString(),
  };

  beforeEach(() => {
    // Mock de validacion JWT para todas las rutas protegidas
    nock(config.services.AUTH_URL)
      .post('/auth/validate', { token: 'eyJhbGciOiJIUzI1NiJ9.valid-token' })
      .reply(200, {
        success: true,
        data: {
          valid: true,
          empleadoId: 'autenticado-id',
          rol: 'ADMIN',
        },
        timestamp: new Date().toISOString(),
      });
  });

  afterEach(() => {
    nock.cleanAll();
  });

  describe('GET /employees', () => {
    it('debe retornar lista paginada de empleados', async () => {
      nock(config.services.EMPLEADOS_URL)
        .get('/employees?page=0&size=10')
        .reply(200, {
          success: true,
          data: [empleadoData.data],
          timestamp: new Date().toISOString(),
        });

      const res = await request(app)
        .get('/employees')
        .set('Authorization', validToken)
        .expect('Content-Type', /json/);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(Array.isArray(res.body.data)).toBe(true);
      expect(res.body.data.length).toBe(1);
    });

    it('debe retornar 401 sin token', async () => {
      const res = await request(app)
        .get('/employees')
        .expect('Content-Type', /json/);

      expect(res.status).toBe(401);
    });
  });

  describe('POST /employees', () => {
    const createPayload = {
      nombre: 'Juan',
      apellido: 'Perez',
      email: 'juan.perez@empresa.com',
      numeroEmpleado: 'EMP-001',
      fechaIngreso: '2024-01-15',
      cargo: 'Desarrollador Senior',
      area: 'Tecnologia',
    };

    it('debe crear un empleado exitosamente', async () => {
      nock(config.services.EMPLEADOS_URL)
        .post('/employees', createPayload)
        .reply(201, empleadoData);

      const res = await request(app)
        .post('/employees')
        .set('Authorization', validToken)
        .send(createPayload)
        .expect('Content-Type', /json/);

      expect(res.status).toBe(201);
      expect(res.body.success).toBe(true);
      expect(res.body.data.id).toBe(empleadoId);
    });

    it('debe retornar 400 con datos invalidos', async () => {
      nock(config.services.EMPLEADOS_URL)
        .post('/employees', {})
        .reply(400, {
          success: false,
          error: {
            code: 'ERROR_VALIDACION',
            message: 'El nombre es requerido',
          },
          timestamp: new Date().toISOString(),
        });

      const res = await request(app)
        .post('/employees')
        .set('Authorization', validToken)
        .send({})
        .expect('Content-Type', /json/);

      expect(res.status).toBe(400);
    });
  });

  describe('GET /employees/:id — endpoint composite', () => {
    it('debe retornar empleado con perfil cuando ambos servicios responden', async () => {
      // Mock del servicio de empleados
      nock(config.services.EMPLEADOS_URL)
        .get(`/employees/${empleadoId}`)
        .reply(200, empleadoData);

      // Mock del servicio de perfiles
      nock(config.services.PERFILES_URL)
        .get(`/profiles/${empleadoId}`)
        .reply(200, {
          success: true,
          data: {
            empleadoId,
            email: 'juan.perez@empresa.com',
            foto: null,
            biografia: 'Desarrollador con experiencia',
            telefono: '+525555555555',
          },
          timestamp: new Date().toISOString(),
        });

      const res = await request(app)
        .get(`/employees/${empleadoId}`)
        .set('Authorization', validToken)
        .expect('Content-Type', /json/);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.data.id).toBe(empleadoId);
      expect(res.body.data.nombre).toBe('Juan');
      expect(res.body.data.perfil).toBeDefined();
      expect(res.body.data.perfil.biografia).toBe('Desarrollador con experiencia');
    });

    it('debe retornar empleado con perfil null cuando perfil no existe', async () => {
      // Mock del servicio de empleados
      nock(config.services.EMPLEADOS_URL)
        .get(`/employees/${empleadoId}`)
        .reply(200, empleadoData);

      // Mock: perfil no encontrado (404)
      nock(config.services.PERFILES_URL)
        .get(`/profiles/${empleadoId}`)
        .reply(404, {
          success: false,
          error: {
            code: 'PERFIL_NO_ENCONTRADO',
            message: `Perfil no encontrado para empleado ${empleadoId}`,
          },
          timestamp: new Date().toISOString(),
        });

      const res = await request(app)
        .get(`/employees/${empleadoId}`)
        .set('Authorization', validToken)
        .expect('Content-Type', /json/);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.data.id).toBe(empleadoId);
      expect(res.body.data.perfil).toBeNull();
    });

    it('debe retornar 404 cuando el empleado no existe', async () => {
      const idInexistente = '00000000-0000-0000-0000-000000000000';

      nock(config.services.EMPLEADOS_URL)
        .get(`/employees/${idInexistente}`)
        .reply(404, {
          success: false,
          error: {
            code: 'EMPLEADO_NO_ENCONTRADO',
            message: `El empleado con id ${idInexistente} no existe`,
          },
          timestamp: new Date().toISOString(),
        });

      const res = await request(app)
        .get(`/employees/${idInexistente}`)
        .set('Authorization', validToken)
        .expect('Content-Type', /json/);

      expect(res.status).toBe(404);
      expect(res.body.success).toBe(false);
      expect(res.body.error.code).toBe('EMPLEADO_NO_ENCONTRADO');
    });

    it('debe retornar 502 cuando empleados no esta disponible', async () => {
      nock(config.services.EMPLEADOS_URL)
        .get(`/employees/${empleadoId}`)
        .replyWithError({ code: 'ECONNREFUSED' });

      const res = await request(app)
        .get(`/employees/${empleadoId}`)
        .set('Authorization', validToken)
        .expect('Content-Type', /json/);

      expect(res.status).toBe(502);
      expect(res.body.error.code).toBe('SERVICIO_NO_DISPONIBLE');
    });
  });

  describe('PUT /employees/:id', () => {
    const updatePayload = {
      nombre: 'Juan Carlos',
      cargo: 'Tech Lead',
    };

    it('debe actualizar un empleado exitosamente', async () => {
      nock(config.services.EMPLEADOS_URL)
        .put(`/employees/${empleadoId}`, updatePayload)
        .reply(200, {
          success: true,
          data: { ...empleadoData.data, nombre: 'Juan Carlos', cargo: 'Tech Lead' },
          timestamp: new Date().toISOString(),
        });

      const res = await request(app)
        .put(`/employees/${empleadoId}`)
        .set('Authorization', validToken)
        .send(updatePayload)
        .expect('Content-Type', /json/);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.data.nombre).toBe('Juan Carlos');
    });
  });

  describe('DELETE /employees/:id', () => {
    it('debe eliminar (soft delete) un empleado exitosamente', async () => {
      nock(config.services.EMPLEADOS_URL)
        .delete(`/employees/${empleadoId}`)
        .reply(204);

      const res = await request(app)
        .delete(`/employees/${empleadoId}`)
        .set('Authorization', validToken);

      expect(res.status).toBe(204);
    });

    it('debe retornar 404 al eliminar un empleado inexistente', async () => {
      const idInexistente = '00000000-0000-0000-0000-000000000000';

      nock(config.services.EMPLEADOS_URL)
        .delete(`/employees/${idInexistente}`)
        .reply(404, {
          success: false,
          error: {
            code: 'EMPLEADO_NO_ENCONTRADO',
            message: `El empleado con id ${idInexistente} no existe`,
          },
          timestamp: new Date().toISOString(),
        });

      const res = await request(app)
        .delete(`/employees/${idInexistente}`)
        .set('Authorization', validToken)
        .expect('Content-Type', /json/);

      expect(res.status).toBe(404);
    });
  });
});
