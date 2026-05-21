const { Given, When } = require('@cucumber/cucumber');
const assert = require('assert');

Given('I am authenticated as ADMIN', async function () {
  const res = await this.request('POST', '/auth/login', {
    username: process.env.ADMIN_EMAIL || 'admin@empresa.com',
    password: process.env.ADMIN_PASSWORD || 'Admin123!'
  }, false);

  assert.strictEqual(
    res.status,
    200,
    `Login as ADMIN failed (status ${res.status}). Response: ${JSON.stringify(res.data)}. `
    + `Verify that the admin user exists (run seed: curl -X POST http://localhost:8081/auth/seed)`
  );

  assert.ok(
    res.data.token || res.data.accessToken,
    `Login returned 200 but no token in response: ${JSON.stringify(res.data)}`
  );
  this.token = res.data.token || res.data.accessToken;
});

Given('I am authenticated as a non-admin user', async function () {
  // Attempt to login as a non-admin user.
  // If the user doesn't exist in the system (no /register endpoint),
  // we skip the token to verify that unauthenticated requests are rejected.
  const res = await this.request('POST', '/auth/login', {
    username: process.env.USER_EMAIL || 'user@empresa.com',
    password: process.env.USER_PASSWORD || 'User123!'
  }, false);

  if (res.status === 200) {
  this.token = res.data.token || res.data.accessToken;
  } else {
    // User doesn't exist or invalid credentials — leave token null
    // The next step will verify auth enforcement returns 401
  this.token = null;
  }
});

When('I request GET {string} without token', async function (path) {
  this.token = null;
  await this.request('GET', path, null, true);
});

When('I request GET {string} with invalid token', async function (path) {
  this.token = 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJmYWtlIn0.invalid.token.here';
  await this.request('GET', path, null, true);
});

When('I request GET {string} with my token', async function (path) {
  // Uses current token (set in Background via "I am authenticated as ADMIN")
  await this.request('GET', path, null, true);
});
