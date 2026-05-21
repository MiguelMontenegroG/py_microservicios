const { When, Then } = require('@cucumber/cucumber');
const assert = require('assert');

When('I request GET {string}', async function (path) {
  await this.request('GET', path, null, true);
});

Then('the response status should be {int}', function (expectedStatus) {
  assert.strictEqual(
    this.response.status,
    expectedStatus,
    `Expected status ${expectedStatus} but got ${this.response.status}. Body: ${JSON.stringify(this.response.data)}`
  );
});

Then('the response status should be {int} or {int}', function (status1, status2) {
  assert.ok(
    this.response.status === status1 || this.response.status === status2,
    `Expected status ${status1} or ${status2} but got ${this.response.status}. Body: ${JSON.stringify(this.response.data)}`
  );
});
