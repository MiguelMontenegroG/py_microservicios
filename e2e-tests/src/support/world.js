const { setWorldConstructor } = require('@cucumber/cucumber');
const axios = require('axios');
class World {
  constructor() {
    this.baseUrl = process.env.BASE_URL || 'http://localhost:8080';
    this.token = null;
    this.response = null;
    this.lastCreatedEmployeeId = null;
    this.lastCreatedEmployeeEmail = null;
  }

  async request(method, path, body = null, useToken = true) {
    const headers = {};
    // Only set Content-Type when there's a body to send
    if (body !== null && body !== undefined) {
      headers['Content-Type'] = 'application/json';
    }
    if (useToken && this.token) {
      headers['Authorization'] = `Bearer ${this.token}`;
    }
    try {
      this.response = await axios({
        method,
        url: `${this.baseUrl}${path}`,
        data: body || undefined,
        headers,
        validateStatus: () => true
      });
    } catch (err) {
      this.response = { status: 500, data: { error: err.message } };
    }
    return this.response;
  }
}

setWorldConstructor(World);
