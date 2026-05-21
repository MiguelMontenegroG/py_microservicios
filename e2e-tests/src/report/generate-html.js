const fs = require('fs');
const path = require('path');

const REPORTS_DIR = path.join(__dirname, '..', '..', 'reports');
const JSON_REPORT = path.join(REPORTS_DIR, 'cucumber-report.json');
const HTML_REPORT = path.join(REPORTS_DIR, 'cucumber-report.html');

if (!fs.existsSync(JSON_REPORT)) {
  console.error('No se encontro el reporte JSON en:', JSON_REPORT);
  process.exit(1);
}

const data = JSON.parse(fs.readFileSync(JSON_REPORT, 'utf-8'));
const features = Array.isArray(data) ? data : [data];

function statusColor(status) {
  const colors = {
    passed: '#4caf50',
    failed: '#f44336',
    skipped: '#ff9800',
    undefined: '#9e9e9e',
    ambiguous: '#9e9e9e',
    pending: '#ff9800'
  };
  return colors[status] || '#9e9e9e';
}

function iconForStatus(status) {
  const icons = {
    passed: '&#10003;',
    failed: '&#10007;',
    skipped: '&#9679;',
    undefined: '&#9679;'
  };
  return icons[status] || '&#9679;';
}

function renderFeature(feature) {
  const elements = feature.elements || [];
  let totalScenarios = 0;
  let passedScenarios = 0;
  let failedScenarios = 0;

  const scenarioRows = elements.map(element => {
    const steps = element.steps || [];
    totalScenarios++;
    const scenarioPassed = steps.every(s => s.result && s.result.status === 'passed');
    const scenarioFailed = steps.some(s => s.result && s.result.status === 'failed');
    const scenarioSkipped = steps.some(s => s.result && s.result.status === 'skipped') && !scenarioFailed;

    if (scenarioPassed) passedScenarios++;
    if (scenarioFailed) failedScenarios++;

    const stepRows = steps.map(step => {
      const status = (step.result && step.result.status) || 'unknown';
      const duration = step.result && step.result.duration
        ? (step.result.duration / 1000000).toFixed(2) + 'ms'
        : '-';
      return `
        <tr class="step-row ${status}">
          <td class="step-icon">${iconForStatus(status)}</td>
          <td class="step-status" style="color:${statusColor(status)}">${status}</td>
          <td class="step-keyword">${step.keyword || ''}</td>
          <td class="step-name">${step.name || ''}</td>
          <td class="step-duration">${duration}</td>
        </tr>`;
    }).join('\n');

    const scenarioStatus = scenarioFailed ? 'failed' : (scenarioPassed ? 'passed' : 'skipped');

    return `
    <div class="scenario">
      <div class="scenario-header" onclick="toggleSteps(this)">
        <span class="scenario-icon">${iconForStatus(scenarioStatus)}</span>
        <span class="scenario-name">${element.name || 'Sin nombre'}</span>
        <span class="scenario-badge ${scenarioStatus}">${scenarioStatus}</span>
        <span class="scenario-toggle">&#9660;</span>
      </div>
      <div class="steps-container" style="display:none">
        <table class="steps-table">
          <thead>
            <tr>
              <th></th>
              <th>Status</th>
              <th>Keyword</th>
              <th>Step</th>
              <th>Duration</th>
            </tr>
          </thead>
          <tbody>
            ${stepRows}
          </tbody>
        </table>
      </div>
    </div>`;
  }).join('\n');

  const featureStatus = failedScenarios > 0 ? 'failed' : (passedScenarios === totalScenarios ? 'passed' : 'skipped');

  return {
    name: feature.name || 'Feature sin nombre',
    description: feature.description || '',
    uri: feature.uri || '',
    status: featureStatus,
    total: totalScenarios,
    passed: passedScenarios,
    failed: failedScenarios,
    scenariosHtml: scenarioRows
  };
}

const renderedFeatures = features.map(renderFeature);

const totalScenarios = renderedFeatures.reduce((sum, f) => sum + f.total, 0);
const totalPassed = renderedFeatures.reduce((sum, f) => sum + f.passed, 0);
const totalFailed = renderedFeatures.reduce((sum, f) => sum + f.failed, 0);
const overallStatus = totalFailed > 0 ? 'failed' : 'passed';

const htmlContent = `<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Reporte E2E Tests - Cucumber BDD</title>
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    body {
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
      background: #f5f5f5;
      color: #333;
      padding: 20px;
    }
    .container {
      max-width: 1000px;
      margin: 0 auto;
    }
    .header {
      background: white;
      border-radius: 8px;
      padding: 24px;
      margin-bottom: 20px;
      box-shadow: 0 1px 3px rgba(0,0,0,0.1);
    }
    .header h1 {
      font-size: 24px;
      margin-bottom: 8px;
    }
    .header .subtitle {
      color: #666;
      font-size: 14px;
    }
    .summary {
      display: flex;
      gap: 16px;
      margin-bottom: 20px;
    }
    .summary-card {
      background: white;
      border-radius: 8px;
      padding: 20px;
      flex: 1;
      text-align: center;
      box-shadow: 0 1px 3px rgba(0,0,0,0.1);
    }
    .summary-card .number {
      font-size: 36px;
      font-weight: bold;
    }
    .summary-card .label {
      font-size: 14px;
      color: #666;
      margin-top: 4px;
    }
    .summary-card.total .number { color: #2196f3; }
    .summary-card.passed .number { color: #4caf50; }
    .summary-card.failed .number { color: #f44336; }
    .feature {
      background: white;
      border-radius: 8px;
      margin-bottom: 16px;
      box-shadow: 0 1px 3px rgba(0,0,0,0.1);
      overflow: hidden;
    }
    .feature-header {
      padding: 16px 20px;
      border-left: 4px solid;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .feature-header.passed { border-left-color: #4caf50; }
    .feature-header.failed { border-left-color: #f44336; }
    .feature-header.skipped { border-left-color: #ff9800; }
    .feature-name { font-size: 16px; font-weight: 600; }
    .feature-uri { font-size: 12px; color: #999; }
    .feature-stats { font-size: 13px; color: #666; }
    .scenario { border-top: 1px solid #eee; }
    .scenario-header {
      padding: 12px 20px 12px 40px;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 8px;
      transition: background 0.2s;
    }
    .scenario-header:hover { background: #f9f9f9; }
    .scenario-icon { font-size: 14px; }
    .scenario-name { flex: 1; font-size: 14px; }
    .scenario-badge {
      font-size: 11px;
      padding: 2px 8px;
      border-radius: 10px;
      color: white;
      text-transform: uppercase;
      font-weight: 600;
    }
    .scenario-badge.passed { background: #4caf50; }
    .scenario-badge.failed { background: #f44336; }
    .scenario-badge.skipped { background: #ff9800; }
    .scenario-toggle { color: #999; font-size: 12px; transition: transform 0.2s; }
    .scenario-toggle.open { transform: rotate(180deg); }
    .steps-container { padding: 0 20px 12px 40px; }
    .steps-table { width: 100%; border-collapse: collapse; font-size: 13px; }
    .steps-table th {
      text-align: left;
      padding: 6px 8px;
      border-bottom: 1px solid #ddd;
      color: #666;
      font-weight: 600;
    }
    .steps-table td { padding: 6px 8px; border-bottom: 1px solid #f0f0f0; }
    .step-row.passed td { background: #f1f8e9; }
    .step-row.failed td { background: #fce4ec; }
    .step-row.skipped td { background: #fff3e0; }
    .step-icon { width: 20px; text-align: center; }
    .step-status { width: 80px; text-transform: uppercase; font-size: 11px; font-weight: 600; }
    .step-keyword { width: 80px; color: #666; }
    .step-duration { width: 80px; text-align: right; color: #999; font-size: 12px; }
    .footer {
      text-align: center;
      color: #999;
      font-size: 12px;
      padding: 20px;
    }
    .status { font-weight: 600; }
    .status.passed { color: #4caf50; }
    .status.failed { color: #f44336; }
  </style>
</head>
<body>
  <div class="container">
    <div class="header">
      <h1>Reporte de Tests E2E - Cucumber BDD</h1>
      <div class="subtitle">
        Generado el ${new Date().toLocaleString('es-ES', { timeZone: 'UTC' })} UTC
        &mdash; Overall: <span class="status ${overallStatus}">${overallStatus.toUpperCase()}</span>
      </div>
    </div>

    <div class="summary">
      <div class="summary-card total">
        <div class="number">${totalScenarios}</div>
        <div class="label">Escenarios</div>
      </div>
      <div class="summary-card passed">
        <div class="number">${totalPassed}</div>
        <div class="label">Exitosos</div>
      </div>
      <div class="summary-card failed">
        <div class="number">${totalFailed}</div>
        <div class="label">Fallidos</div>
      </div>
    </div>

    ${renderedFeatures.map(f => `
    <div class="feature">
      <div class="feature-header ${f.status}">
        <div>
          <div class="feature-name">Feature: ${f.name}</div>
          <div class="feature-uri">${f.uri}</div>
        </div>
        <div class="feature-stats">${f.passed}/${f.total} escenarios</div>
      </div>
      ${f.scenariosHtml}
    </div>
    `).join('\n')}

    <div class="footer">
      Cucumber BDD Report &mdash; Employee Management System
    </div>
  </div>

  <script>
    function toggleSteps(header) {
      const container = header.nextElementSibling;
      const toggle = header.querySelector('.scenario-toggle');
      if (container.style.display === 'none') {
        container.style.display = 'block';
        toggle.classList.add('open');
      } else {
        container.style.display = 'none';
        toggle.classList.remove('open');
      }
    }
  </script>
</body>
</html>`;

fs.writeFileSync(HTML_REPORT, htmlContent);
console.log('Reporte HTML generado:', HTML_REPORT);
