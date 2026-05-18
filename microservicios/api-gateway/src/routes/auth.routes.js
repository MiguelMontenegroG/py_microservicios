const express = require('express');
const router = express.Router();
const config = require('../config');
const { forwardRequest } = require('../proxy/httpProxy');
const { authMiddleware } = require('../middleware/auth.middleware');

// POST /auth/login — publico
router.post('/login', async (req, res, next) => {
  try {
    const response = await forwardRequest(
      'POST',
      `${config.services.AUTH_URL}/auth/login`,
      req.body,
      { 'Content-Type': 'application/json' }
    );

    return res.status(response.status).json(response.data);
  } catch (error) {
    next(error);
  }
});

// POST /auth/change-password — requiere JWT
router.post('/change-password', authMiddleware, async (req, res, next) => {
  try {
    const headers = {
      'Content-Type': 'application/json',
      'X-Empleado-Id': req.empleadoId,
      'X-Rol': req.rol,
    };

    const response = await forwardRequest(
      'POST',
      `${config.services.AUTH_URL}/auth/change-password`,
      req.body,
      headers
    );

    return res.status(response.status).json(response.data);
  } catch (error) {
    next(error);
  }
});

module.exports = router;
