const httpMocks = require('node-mocks-http');

describe('Error Middleware', () => {
  it('debe responder 400 para error de parseo JSON', () => {
    const { errorMiddleware } = require('../src/middleware/error.middleware');
    const req = httpMocks.createRequest({
      method: 'POST',
      url: '/auth/login',
      headers: { 'Content-Type': 'application/json' },
    });
    const res = httpMocks.createResponse();
    const err = {
      type: 'entity.parse.failed',
      message: 'Unexpected token',
    };

    errorMiddleware(err, req, res, () => {});

    expect(res.statusCode).toBe(400);
    const data = JSON.parse(res._getData());
    expect(data.success).toBe(false);
    expect(data.error.code).toBe('ERROR_LECTURA_JSON');
  });

  it('debe responder 500 para error interno generico', () => {
    const { errorMiddleware } = require('../src/middleware/error.middleware');
    const req = httpMocks.createRequest({
      method: 'GET',
      url: '/employees',
    });
    const res = httpMocks.createResponse();
    const err = new Error('Algo salio mal');

    errorMiddleware(err, req, res, () => {});

    expect(res.statusCode).toBe(500);
    const data = JSON.parse(res._getData());
    expect(data.success).toBe(false);
    expect(data.error.code).toBe('ERROR_INTERNO');
  });

  it('debe usar el status y code del error si estan presentes', () => {
    const { errorMiddleware } = require('../src/middleware/error.middleware');
    const req = httpMocks.createRequest({ method: 'GET', url: '/test' });
    const res = httpMocks.createResponse();
    const err = {
      status: 422,
      code: 'ENTIDAD_NO_PROCESABLE',
      message: 'Datos de entrada invalidos',
    };

    errorMiddleware(err, req, res, () => {});

    expect(res.statusCode).toBe(422);
    const data = JSON.parse(res._getData());
    expect(data.error.code).toBe('ENTIDAD_NO_PROCESABLE');
    expect(data.error.message).toBe('Datos de entrada invalidos');
  });
});
