module.exports = {
  PORT: parseInt(process.env.PORT, 10) || 8080,
  JWT_SECRET: process.env.JWT_SECRET || 'MiClaveSecretaParaJWTDeAutenticacionDebeSerLargaDe32Chars!',
  services: {
    AUTH_URL: process.env.AUTH_URL || 'http://localhost:8081',
    EMPLEADOS_URL: process.env.EMPLEADOS_URL || 'http://localhost:8082',
    PERFILES_URL: process.env.PERFILES_URL || 'http://localhost:8083',
    VACACIONES_URL: process.env.VACACIONES_URL || 'http://localhost:8084',
    NOTIFICACIONES_URL: process.env.NOTIFICACIONES_URL || 'http://localhost:8085',
  },
  requestTimeout: parseInt(process.env.REQUEST_TIMEOUT, 10) || 5000,
};
