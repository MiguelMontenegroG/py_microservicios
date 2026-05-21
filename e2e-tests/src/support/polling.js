/**
 * Polling utility for async event verification.
 * Polls until conditionFn returns truthy, or throws after maxAttempts.
 *
 * Default: 12 attempts x 2s = 24s max wait.
 * This covers:
 *   - RabbitMQ event propagation (~1-3s)
 *   - Consumer processing time (~1-5s)
 *   - Database write confirmation (~0.5-2s)
 *   24s is generous for all async flows in this system.
 */
async function waitUntil(conditionFn, maxAttempts = 12, intervalMs = 2000) {
  for (let i = 0; i < maxAttempts; i++) {
    try {
      const result = await conditionFn();
      if (result) return result;
    } catch (_) {
      // not ready yet, keep polling
    }
    await new Promise(resolve => setTimeout(resolve, intervalMs));
  }
  throw new Error(
    `Condition not met after ${maxAttempts} attempts (${(maxAttempts * intervalMs) / 1000}s)`
  );
}

module.exports = { waitUntil };
