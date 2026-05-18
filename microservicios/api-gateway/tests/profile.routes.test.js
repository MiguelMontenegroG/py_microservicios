const request = require('supertest');
const nock = require('nock');
const config = require('../src/config');
const app = require('../src/app');

describe('Profile Routes', () => {
  const validToken = 'Bearer eyJhbGciOiJIUzI1NiJ9.profile-token';
  const empleadoId = 'b2c3d4e5-f6a7-8901-bcde-f12345678901';

  beforeEach(() => {
    nock(config.services.AUTH_URL)
      .post('/auth/validate', { token: 'eyJhbGciOiJIUzI1NiJ9.profile-token' })
      .reply(200, {
        success: true,
        data: {
          valid: true,
          empleadoId,
          rol: 'EMPLEADO',
        },
        timestamp: new Date().toISOString(),
      });
  });

  afterEach(() => {
    nock.cleanAll();
  });

  describe('GET /profile', () => {
    it('debe retornar el perfil del empleado autenticado', async () => {
      const profileData = {
        success: true,
        data: {
          empleadoId,
          email: 'empleado@empresa.com',
          foto: null,
          biografia: 'Empleado de prueba',
          telefono: '+525512345678',
          direccion: {
            calle: 'Calle Principal 123',
            ciudad: 'Ciudad de Mexico',
            codigoPostal: '06600',
            pais: 'Mexico',
          },
          redesSociales: {
            linkedin: 'https://linkedin.com/in/empleado',
            github: null,
          },
        },
        timestamp: new Date().toISOString(),
      };

      nock(config.services.PERFILES_URL)
        .get(`/profiles/${empleadoId}`)
        .reply(200, profileData);

      const res = await request(app)
        .get('/profile')
        .set('Authorization', validToken)
        .expect('Content-Type', /json/);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
      expect(res.body.data.empleadoId).toBe(empleadoId);
      expect(res.body.data.direccion.ciudad).toBe('Ciudad de Mexico');
    });

    it('debe retornar 401 sin token', async () => {
      const res = await request(app)
        .get('/profile')
        .expect('Content-Type', /json/);

      expect(res.status).toBe(401);
    });
  });

  describe('PUT /profile', () => {
    const updateData = {
      biografia: 'Nueva biografia actualizada',
      telefono: '+525598765432',
      direccion: {
        calle: 'Nueva Calle 456',
        ciudad: 'Guadalajara',
        codigoPostal: '44100',
        pais: 'Mexico',
      },
    };

    it('debe actualizar el perfil exitosamente', async () => {
      nock(config.services.PERFILES_URL)
        .put(`/profiles/${empleadoId}`, updateData)
        .reply(200, {
          success: true,
          data: {
            empleadoId,
            ...updateData,
          },
          timestamp: new Date().toISOString(),
        });

      const res = await request(app)
        .put('/profile')
        .set('Authorization', validToken)
        .send(updateData)
        .expect('Content-Type', /json/);

      expect(res.status).toBe(200);
      expect(res.body.success).toBe(true);
    });

    it('debe retornar 502 si perfiles no esta disponible', async () => {
      nock(config.services.PERFILES_URL)
        .put(`/profiles/${empleadoId}`, updateData)
        .replyWithError({ code: 'ECONNREFUSED' });

      const res = await request(app)
        .put('/profile')
        .set('Authorization', validToken)
        .send(updateData)
        .expect('Content-Type', /json/);

      expect(res.status).toBe(502);
    });
  });
});
