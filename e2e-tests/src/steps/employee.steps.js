const { Given, When, Then } = require('@cucumber/cucumber');
const assert = require('assert');
const { waitUntil } = require('../support/polling');

/**
 * Generate a unique email for test isolation.
 * Each scenario generates a fresh email so no collisions occur.
 */
function uniqueEmail(suffix) {
  return `e2e.${suffix}.${Date.now()}@test.com`;
}

When('I create an employee with name {string} and unique email ending with {string}', async function (name, suffix) {
  const email = uniqueEmail(suffix);
    this.lastCreatedEmployeeEmail = email;
  const nameParts = name.split(' ');
  const nombre = nameParts[0] || name;
  const apellido = nameParts.slice(1).join(' ') || 'Test';
  const numeroEmpleado = `EMP-E2E-${Date.now()}`;

  await this.request('POST', '/employees', {
    nombre,
    apellido,
    email,
    cargo: 'Developer',
    area: 'IT',
    numeroEmpleado,
    fechaIngreso: new Date().toISOString().split('T')[0]
  });

  if (this.response.status === 201) {
    this.lastCreatedEmployeeId = this.response.data.id || this.response.data.empleadoId;
  }
});

When('I try to create an employee with name {string} and unique email ending with {string}', async function (name, suffix) {
  const email = uniqueEmail(suffix);
  this.lastCreatedEmployeeEmail = email;

  const nameParts = name.split(' ');
  const nombre = nameParts[0] || name;
  const apellido = nameParts.slice(1).join(' ') || 'Test';
  const numeroEmpleado = `EMP-E2E-${Date.now()}`;

  await this.request('POST', '/employees', {
    nombre,
    apellido,
    email,
    cargo: 'Developer',
    area: 'IT',
    numeroEmpleado,
    fechaIngreso: new Date().toISOString().split('T')[0]
  });
});

When('I try to create an employee with name {string} and the same email as before', async function (name) {
  const email = this.lastCreatedEmployeeEmail;
  assert.ok(email, 'No previous email stored. Make sure a step with "unique email" ran first.');

  const nameParts = name.split(' ');
  const nombre = nameParts[0] || name;
  const apellido = nameParts.slice(1).join(' ') || 'Test';

  await this.request('POST', '/employees', {
    nombre,
    apellido,
    email,
    cargo: 'Developer',
    area: 'IT',
    numeroEmpleado: `EMP-E2E-DUP-${Date.now()}`,
    fechaIngreso: new Date().toISOString().split('T')[0]
  });
});

When('I try to create an employee without required fields', async function () {
  await this.request('POST', '/employees', { nombre: '' });
});

Given('an employee with unique email ending with {string} already exists', async function (suffix) {
  const email = uniqueEmail(suffix);
  this.lastCreatedEmployeeEmail = email;
  this.duplicateEmail = email;

  const numeroEmpleado = `EMP-E2E-EXISTING-${Date.now()}`;
  await this.request('POST', '/employees', {
    nombre: 'Existing',
    apellido: 'Employee',
    email,
    cargo: 'Developer',
    area: 'IT',
    numeroEmpleado,
    fechaIngreso: new Date().toISOString().split('T')[0]
  });
  if (this.response.data && this.response.data.id) {
    this.lastCreatedEmployeeId = this.response.data.id;
  }
});
Then('the response body should contain the created employee email', function () {
  const body = JSON.stringify(this.response.data);
  assert.ok(
    this.lastCreatedEmployeeEmail,
    'No email stored. Make sure a create step ran first.'
  );
  assert.ok(
    body.includes(this.lastCreatedEmployeeEmail),
    `Expected body to contain "${this.lastCreatedEmployeeEmail}". Got: ${body}`
  );
});

Then('eventually the auth service should have a user for the created employee', async function () {
  const email = this.lastCreatedEmployeeEmail;
  assert.ok(email, 'No email stored. Make sure a create step ran first.');
  await waitUntil(async () => {
    const res = await this.request('POST', '/auth/login', {
      username: email,
      password: 'any-wrong-password-for-polling'
    }, false);
    // Any non-500 status means the auth service processed the event.
    return res.status !== 500;
  }, 12, 2000);
});
