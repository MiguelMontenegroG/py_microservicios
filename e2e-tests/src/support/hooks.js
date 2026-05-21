const { Before, After } = require('@cucumber/cucumber');

Before(function () {
  // Reset state before each scenario to ensure independence
  this.token = null;
  this.response = null;
  this.lastCreatedEmployeeId = null;
  this.lastCreatedEmployeeEmail = null;
});

After(function () {
  // No cleanup needed — each scenario uses unique emails/timestamps
  // to avoid collisions. System state is preserved for debugging.
});
