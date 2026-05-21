require('dotenv').config();

module.exports = {
  default: {
    require: ['src/support/*.js', 'src/steps/*.js'],
    format: ['progress', 'json:reports/cucumber-report.json'],
    timeout: 30000
  }
};
