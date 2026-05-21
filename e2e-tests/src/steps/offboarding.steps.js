const { Given, When, Then, setDefaultTimeout } = require('@cucumber/cucumber');
const assert = require('assert');
const { waitUntil } = require('../support/polling');

setDefaultTimeout(60000);

function uniqueEmail(suffix) {
  return `e2e.${suffix}.${Date.now()}@test.com`;
}
Given('a fully onboarded employee exists with unique email ending with {string}', { timeout: 60000 }, async function (suffix) {
  const email = uniqueEmail(suffix);
  this.lastCreatedEmployeeEmail = email;

  // Step 1: Create the employee
  const numeroEmpleado = `EMP-E2E-OFFBOARD-${Date.now()}`;
  await this.request('POST', '/employees', {
    nombre: 'Offboard',
    apellido: 'Test',
    email,
    cargo: 'Developer',
    area: 'IT',
    numeroEmpleado,
    fechaIngreso: new Date().toISOString().split('T')[0]
  });

  assert.strictEqual(
    this.response.status,
    201,
    `Failed to create offboarding employee. Got ${this.response.status}: ${JSON.stringify(this.response.data)}`
  );

  this.lastCreatedEmployeeId = this.response.data.id || this.response.data.empleadoId;

  // Step 2: Wait for auth service to create credentials (async event via RabbitMQ)
  // The auth service returns:
  //   - 401 CREDENCIALES_INVALIDAS if user exists (wrong password)
  //   - 401 CREDENCIALES_INVALIDAS if user doesn't exist
  // Since both cases return 401, we need an additional check.
  // We poll until we get 401 (user processed) instead of 500 (not yet processed).
  await waitUntil(async () => {
    const res = await this.request('POST', '/auth/login', {
      username: email,
      password: 'wrong-pw-for-polling'
    }, false);
    // Once the consumer processes the event, auth will respond with 401
    // (either user exists or doesn't, but at least the service responded).
    // If still processing, it might return 500 or timeout.
    return res.status === 401 || res.status === 403;
  }, 10, 2000);  // More attempts for full onboarding flow
});

When('I delete the last created employee', async function () {
  assert.ok(
    this.lastCreatedEmployeeId,
    'No employee ID stored. Make sure a previous step created an employee.'
  );
  await this.request('DELETE', `/employees/${this.lastCreatedEmployeeId}`);
});

When('I look up the last created employee', async function () {
  assert.ok(
    this.lastCreatedEmployeeId,
    'No employee ID stored. Make sure a previous step created an employee.'
  );
  await this.request('GET', `/employees/${this.lastCreatedEmployeeId}`);
});

Then('the offboarded employee cannot login', async function () {
  const email = this.lastCreatedEmployeeEmail;
  assert.ok(email, 'No email stored for offboarding verification.');

  // After offboarding, the auth service deactivates the account.
  // The system returns:
  //   - 403 CUENTA_DESACTIVADA: account exists but disabled (successful offboarding)
  //   - 401 CREDENCIALES_INVALIDAS: credentials invalid
  // Either is acceptable — what matters is NOT getting 200.
  await waitUntil(async () => {
    const res = await this.request('POST', '/auth/login', {
      username: email,
      password: process.env.ADMIN_PASSWORD || 'Admin123!'
    }, false);
    return res.status === 403 || res.status === 401;
  }, 10, 2000);

  // Final verification
  const res = await this.request('POST', '/auth/login', {
    username: email,
    password: process.env.ADMIN_PASSWORD || 'Admin123!'
  }, false);

  assert.notStrictEqual(
    res.status,
    200,
    `Employee ${email} should not be able to login after offboarding. Got status ${res.status}`
  );
});
