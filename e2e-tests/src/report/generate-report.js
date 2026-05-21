#!/usr/bin/env node

const fs = require('fs');
const path = require('path');

const REPORTS_DIR = path.join(__dirname, '..', '..', 'reports');
const MESSAGES_FILE = path.join(REPORTS_DIR, 'cucumber-messages.ndjson');
const JSON_REPORT = path.join(REPORTS_DIR, 'cucumber-report.json');
const HTML_OFFICIAL = path.join(REPORTS_DIR, 'cucumber-report.html');
const HTML_ESPANOL = path.join(REPORTS_DIR, 'cucumber-report-es.html');

// ============================================================
// 1. GENERAR INFORME OFICIAL DE CUCUMBER
// ============================================================
async function generarInformeOficial() {
  if (!fs.existsSync(MESSAGES_FILE)) {
    console.error('  [OFICIAL] No se encontraron mensajes de Cucumber en:', MESSAGES_FILE);
    return false;
  }

  try {
    const { CucumberHtmlStream } = require('@cucumber/html-formatter');
    const { NdjsonToMessageStream } = require('@cucumber/message-streams');

    const formatterDir = path.dirname(require.resolve('@cucumber/html-formatter/package.json'));
    const cssPath = path.join(formatterDir, 'dist', 'main.css');
    const jsPath = path.join(formatterDir, 'dist', 'main.js');

    if (!fs.existsSync(cssPath) || !fs.existsSync(jsPath)) {
      console.error('  [OFICIAL] Assets del formatter no encontrados');
      return false;
    }

    const inputStream = fs.createReadStream(MESSAGES_FILE);
    const outputStream = fs.createWriteStream(HTML_OFFICIAL);
    const ndjsonToMessageStream = new NdjsonToMessageStream();
    const cucumberHtmlStream = new CucumberHtmlStream(cssPath, jsPath);

    inputStream
        .pipe(ndjsonToMessageStream)
        .pipe(cucumberHtmlStream)
        .pipe(outputStream);

    return new Promise((resolve) => {
      outputStream.on('finish', () => {
        if (fs.existsSync(HTML_OFFICIAL)) {
          const stats = fs.statSync(HTML_OFFICIAL);
          console.log('  [OFICIAL] cucumber-report.html (' + (stats.size / 1024).toFixed(1) + ' KB)');
          resolve(true);
        } else {
          resolve(false);
        }
      });
      outputStream.on('error', () => resolve(false));
      inputStream.on('error', () => resolve(false));
    });
  } catch (err) {
    console.error('  [OFICIAL] Error:', err.message);
    return false;
  }
}

// ============================================================
// 2. GENERAR INFORME EN ESPANOL
// ============================================================
function generarInformeEspanol() {
  if (!fs.existsSync(JSON_REPORT)) {
    console.error('  [ESPANOL] No se encontro el reporte JSON en:', JSON_REPORT);
    return false;
  }

  try {
    const data = JSON.parse(fs.readFileSync(JSON_REPORT, 'utf-8'));
    const features = Array.isArray(data) ? data : [data];

    function statusColor(status) {
      const colors = { passed: '#4caf50', failed: '#f44336', skipped: '#ff9800', undefined: '#9e9e9e', ambiguous: '#9e9e9e', pending: '#ff9800' };
      return colors[status] || '#9e9e9e';
    }

    function labelForStatus(status) {
      const labels = { passed: 'EXITOSO', failed: 'FALLIDO', skipped: 'SALTADO', undefined: 'NO DEFINIDO', ambiguous: 'AMBIGUO', pending: 'PENDIENTE' };
      return labels[status] || status.toUpperCase();
    }

    function iconForStatus(status) {
      const icons = { passed: '&#10003;', failed: '&#10007;', skipped: '&#9679;', undefined: '&#9679;' };
      return icons[status] || '&#9679;';
    }

    function renderFeature(feature) {
      const elements = feature.elements || [];
      let totalScenarios = 0, passedScenarios = 0, failedScenarios = 0;

      const scenarioRows = elements.map(element => {
        const steps = element.steps || [];
        totalScenarios++;
        const scenarioPassed = steps.every(s => s.result && s.result.status === 'passed');
        const scenarioFailed = steps.some(s => s.result && s.result.status === 'failed');

        if (scenarioPassed) passedScenarios++;
        if (scenarioFailed) failedScenarios++;

        const stepRows = steps.map(step => {
          const status = (step.result && step.result.status) || 'unknown';
          const duration = step.result && step.result.duration
              ? (step.result.duration / 1000000).toFixed(2) + 'ms' : '-';
          return `
            <tr class="step-row ${status}">
              <td class="step-icon">${iconForStatus(status)}</td>
              <td class="step-status" style="color:${statusColor(status)}">${labelForStatus(status)}</td>
              <td class="step-keyword">${step.keyword || ''}</td>
              <td class="step-name">${step.name || ''}</td>
              <td class="step-duration">${duration}</td>
            </tr>`;
        }).join('\n');

        const scenarioStatus = scenarioFailed ? 'failed' : (scenarioPassed ? 'passed' : 'skipped');

        return `
        <div class="escenario">
          <div class="encabezado" onclick="alternarPasos(this)">
            <span class="icono">${iconForStatus(scenarioStatus)}</span>
            <span class="nombre">${element.name || 'Sin nombre'}</span>
            <span class="etiqueta ${scenarioStatus}">${labelForStatus(scenarioStatus)}</span>
            <span class="flecha">&#9660;</span>
          </div>
          <div class="pasos" style="display:none">
            <table class="tabla-pasos">
              <thead>
                <tr>
                  <th></th>
                  <th>Estado</th>
                  <th>Palabra</th>
                  <th>Paso</th>
                  <th>Duracion</th>
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
        name: feature.name || 'Funcionalidad sin nombre',
        uri: feature.uri || '',
        status: featureStatus,
        total: totalScenarios,
        passed: passedScenarios,
        failed: failedScenarios,
        scenariosHtml: scenarioRows
      };
    }

    const renderedFeatures = features.map(renderFeature);
    const totalScenarios = renderedFeatures.reduce((s, f) => s + f.total, 0);
    const totalPassed = renderedFeatures.reduce((s, f) => s + f.passed, 0);
    const totalFailed = renderedFeatures.reduce((s, f) => s + f.failed, 0);
    const overallStatus = totalFailed > 0 ? 'failed' : 'passed';

    const htmlContent = `<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Informe de Pruebas E2E - Cucumber BDD</title>
  <style>
    * { margin:0; padding:0; box-sizing:border-box; }
    body { font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif; background:#f5f5f5; color:#333; padding:20px; }
    .contenedor { max-width:1000px; margin:0 auto; }
    .cabecera { background:white; border-radius:8px; padding:24px; margin-bottom:20px; box-shadow:0 1px 3px rgba(0,0,0,0.1); }
    .cabecera h1 { font-size:24px; margin-bottom:8px; }
    .cabecera .subtitulo { color:#666; font-size:14px; }
    .resumen { display:flex; gap:16px; margin-bottom:20px; }
    .tarjeta { background:white; border-radius:8px; padding:20px; flex:1; text-align:center; box-shadow:0 1px 3px rgba(0,0,0,0.1); }
    .tarjeta .numero { font-size:36px; font-weight:bold; }
    .tarjeta .etiqueta { font-size:14px; color:#666; margin-top:4px; }
    .tarjeta.total .numero { color:#2196f3; }
    .tarjeta.exitoso .numero { color:#4caf50; }
    .tarjeta.fallido .numero { color:#f44336; }
    .funcionalidad { background:white; border-radius:8px; margin-bottom:16px; box-shadow:0 1px 3px rgba(0,0,0,0.1); overflow:hidden; }
    .funcionalidad-encabezado { padding:16px 20px; border-left:4px solid; display:flex; justify-content:space-between; align-items:center; }
    .funcionalidad-encabezado.passed { border-left-color:#4caf50; }
    .funcionalidad-encabezado.failed { border-left-color:#f44336; }
    .funcionalidad-encabezado.skipped { border-left-color:#ff9800; }
    .funcionalidad-nombre { font-size:16px; font-weight:600; }
    .funcionalidad-ruta { font-size:12px; color:#999; }
    .funcionalidad-estadisticas { font-size:13px; color:#666; }
    .escenario { border-top:1px solid #eee; }
    .encabezado { padding:12px 20px 12px 40px; cursor:pointer; display:flex; align-items:center; gap:8px; transition:background 0.2s; }
    .encabezado:hover { background:#f9f9f9; }
    .icono { font-size:14px; }
    .nombre { flex:1; font-size:14px; }
    .etiqueta { font-size:11px; padding:2px 8px; border-radius:10px; color:white; text-transform:uppercase; font-weight:600; }
    .etiqueta.passed { background:#4caf50; }
    .etiqueta.failed { background:#f44336; }
    .etiqueta.skipped { background:#ff9800; }
    .flecha { color:#999; font-size:12px; transition:transform 0.2s; }
    .flecha.abierta { transform:rotate(180deg); }
    .pasos { padding:0 20px 12px 40px; }
    .tabla-pasos { width:100%; border-collapse:collapse; font-size:13px; }
    .tabla-pasos th { text-align:left; padding:6px 8px; border-bottom:1px solid #ddd; color:#666; font-weight:600; }
    .tabla-pasos td { padding:6px 8px; border-bottom:1px solid #f0f0f0; }
    .step-row.passed td { background:#f1f8e9; }
    .step-row.failed td { background:#fce4ec; }
    .step-row.skipped td { background:#fff3e0; }
    .step-icon { width:20px; text-align:center; }
    .step-status { width:80px; text-transform:uppercase; font-size:11px; font-weight:600; }
    .step-keyword { width:80px; color:#666; }
    .step-duration { width:80px; text-align:right; color:#999; font-size:12px; }
    .pie { text-align:center; color:#999; font-size:12px; padding:20px; }
    .estado { font-weight:600; }
    .estado.passed { color:#4caf50; }
    .estado.failed { color:#f44336; }
  </style>
</head>
<body>
  <div class="contenedor">
    <div class="cabecera">
      <h1>Informe de Pruebas E2E - Cucumber BDD</h1>
      <div class="subtitulo">
        Generado el ${new Date().toLocaleString('es-ES', { timeZone: 'UTC' })} UTC
        &mdash; Resultado general: <span class="estado ${overallStatus}">${labelForStatus(overallStatus)}</span>
      </div>
    </div>
    <div class="resumen">
      <div class="tarjeta total"><div class="numero">${totalScenarios}</div><div class="etiqueta">Escenarios</div></div>
      <div class="tarjeta exitoso"><div class="numero">${totalPassed}</div><div class="etiqueta">Exitosos</div></div>
      <div class="tarjeta fallido"><div class="numero">${totalFailed}</div><div class="etiqueta">Fallidos</div></div>
    </div>
    ${renderedFeatures.map(f => `
    <div class="funcionalidad">
      <div class="funcionalidad-encabezado ${f.status}">
        <div>
          <div class="funcionalidad-nombre">Funcionalidad: ${f.name}</div>
          <div class="funcionalidad-ruta">${f.uri}</div>
        </div>
        <div class="funcionalidad-estadisticas">${f.passed}/${f.total} escenarios</div>
      </div>
      ${f.scenariosHtml}
    </div>`).join('\n')}
    <div class="pie">Informe de Pruebas Cucumber BDD &mdash; Sistema de Gestion de Empleados</div>
  </div>
  <script>
    function alternarPasos(h) {
      const p = h.nextElementSibling;
      const f = h.querySelector('.flecha');
      if (p.style.display === 'none') { p.style.display = 'block'; f.classList.add('abierta'); }
      else { p.style.display = 'none'; f.classList.remove('abierta'); }
    }
  </script>
</body>
</html>`;

    fs.writeFileSync(HTML_ESPANOL, htmlContent);
    const stats = fs.statSync(HTML_ESPANOL);
    console.log('  [ESPANOL] cucumber-report-es.html (' + (stats.size / 1024).toFixed(1) + ' KB)');
    return true;
  } catch (err) {
    console.error('  [ESPANOL] Error:', err.message);
    return false;
  }
}

// ============================================================
// MAIN
// ============================================================
async function main() {
  console.log('');
  console.log('============================================');
  console.log('  GENERANDO INFORMES DE PRUEBAS');
  console.log('============================================');
  console.log('');

  console.log('[1/2] Generando informe oficial de Cucumber...');
  const oficialOk = await generarInformeOficial();

  console.log('[2/2] Generando informe en espanol...');
  const espanolOk = generarInformeEspanol();

  console.log('');
  console.log('============================================');
  console.log('  INFORMES GENERADOS');
  console.log('  Directorio: ' + REPORTS_DIR);
  console.log('');

  const filePrefix = 'file:///' + REPORTS_DIR.replace(/\\/g, '/').replace(/^\/+/, '');

  if (oficialOk) {
    console.log('  [OK] Informe oficial de Cucumber:');
    console.log('       cucumber-report.html');
  } else {
    console.log('  [--] Informe oficial de Cucumber: NO DISPONIBLE');
  }

  if (espanolOk) {
    console.log('  [OK] Informe en espanol:');
    console.log('       cucumber-report-es.html');
  } else {
    console.log('  [--] Informe en espanol: NO DISPONIBLE');
  }

  console.log('');
  console.log('  Abrelos en tu navegador:');
  if (oficialOk) console.log('  - file:///' + REPORTS_DIR.replace(/\\/g, '/') + '/cucumber-report.html');
  if (espanolOk) console.log('  - file:///' + REPORTS_DIR.replace(/\\/g, '/') + '/cucumber-report-es.html');
  console.log('============================================');
  console.log('');
}

main().catch(err => {
  console.error('Error general:', err.message);
  process.exit(1);
});
