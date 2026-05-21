#!/usr/bin/env node

/**
 * Script de inicializacion de datos de prueba.
 * Se ejecuta automaticamente antes de los tests E2E para asegurar
 * que el usuario admin existe en el sistema.
 */

const axios = require('axios');

const BASE_URL = process.env.BASE_URL || 'http://localhost:8080';
const AUTH_URL = process.env.AUTH_INTERNAL_URL || 'http://autenticacion:8081';

async function seedData() {
  console.log('');
  console.log('============================================');
  console.log('  INICIALIZANDO DATOS DE PRUEBA');
  console.log('============================================');
  console.log('');

  let adminCreado = false;

  // 1. Seed del admin en el microservicio de autenticacion
  try {
    console.log('[1/1] Verificando/creando usuario admin...');
    const res = await axios.post(
      `${AUTH_URL}/auth/seed`,
      {},
      {
        headers: { 'Content-Type': 'application/json' },
        timeout: 5000,
        validateStatus: () => true
      }
    );

    if (res.status === 200) {
      const msg = res.data.message || '';
      if (msg.includes('ya existe')) {
        console.log('  -> El usuario admin ya existe (OK)');
      } else {
        console.log('  -> Usuario admin creado exitosamente');
        adminCreado = true;
      }

      // Mostrar credenciales si se creo ahora
      if (adminCreado) {
        console.log('  -> Usuario: ' + (res.data.username || 'admin@empresa.com'));
        console.log('  -> Password: ' + (res.data.password || 'Admin123!'));
      }
    } else {
      console.error('  -> Error al crear admin. Status:', res.status, JSON.stringify(res.data));
    }
  } catch (err) {
    console.error('  -> Error de conexion al crear admin:', err.message);
    console.error('  -> Asegurate de que el microservicio autenticacion esta corriendo');
  }

  // 2. Verificar que el login funciona
  try {
    console.log('');
    console.log('[VERIFICACION] Probando login como admin...');
    const loginRes = await axios.post(
      `${BASE_URL}/auth/login`,
      {
        username: process.env.ADMIN_EMAIL || 'admin@empresa.com',
        password: process.env.ADMIN_PASSWORD || 'Admin123!'
      },
      {
        headers: { 'Content-Type': 'application/json' },
        timeout: 5000,
        validateStatus: () => true
      }
    );

    if (loginRes.status === 200 && loginRes.data.token) {
      console.log('  -> Login exitoso (token obtenido)');
    } else {
      console.error('  -> ERROR: Login fallo. Status:', loginRes.status);
      console.error('  -> Respuesta:', JSON.stringify(loginRes.data));
      console.error('  -> Los tests PROBABLEMENTE fallaran por problemas de autenticacion');
    }
  } catch (err) {
    console.error('  -> ERROR de conexion en verficacion de login:', err.message);
    console.error('  -> Asegurate de que el API Gateway esta corriendo en', BASE_URL);
  }

  console.log('');
  console.log('============================================');
  console.log('  INICIALIZACION COMPLETADA');
  console.log('============================================');
  console.log('');
}

seedData().catch(err => {
  console.error('Error fatal en inicializacion:', err.message);
  process.exit(1);
});
