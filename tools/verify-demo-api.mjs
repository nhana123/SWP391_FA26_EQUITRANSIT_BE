// Runtime smoke check only: no Maven, test files or test runner.
// Load the FE's actual Zod contracts, then parse real demo API responses.
import fs from 'node:fs/promises';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
const root = path.resolve(import.meta.dirname, '..');
const fe = path.resolve(root, '../FE/SWP391_FA26_EQUITRANSIT_FE');
const local = path.join(root, '.local/demo');
const { createServer } = await import(pathToFileURL(
  path.join(fe, 'node_modules/vite/dist/node/index.js')));
const modules = {
  services: 'booking-services', quotes: 'quotations',
  payments: 'payments', compliance: 'compliance', plans: 'plans',
  departure: 'departure', activity: 'activity-customer',
  resources: 'resources', tariffs: 'service-pricing',
  staff: 'staff-administration',
};
const server = await createServer({ root: fe, configFile: false,
  cacheDir: path.join(local, 'vite-contract-cache'),
  optimizeDeps: { noDiscovery: true },
  server: { middlewareMode: true, watch: null }, appType: 'custom' });
const schemas = {};
try {
  for (const [name, dir] of Object.entries(modules)) {
    schemas[name] = await server.ssrLoadModule('/src/features/' + dir + '/contracts.ts');
  }
} finally { await server.close(); }
const state = JSON.parse((await fs.readFile(path.join(local,
  'seed-state.json'), 'utf8')).replace(/^\uFEFF/, ''));
const tokens = {};
const results = [];
const responses = {};
const base = 'http://127.0.0.1:8080';
for (const role of ['CUSTOMER', 'MANAGER', 'SPECIALIST', 'COORDINATOR', 'DRIVER']) {
  const response = await fetch(base + '/api/auth/login', { method: 'POST',
    headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({
      email: role.toLowerCase() + '.demo@equitransit.com',
      password: 'EquiTransit@123',
    }) });
  const body = await response.json();
  if (!response.ok || body.role !== role) throw Error('Login failed: ' + role);
  tokens[role] = body.accessToken;
  results.push({ role, path: '/api/auth/login', status: response.status, ok: true });
}
async function check(role, endpoint, schema, options = {}, expected = 200) {
  const response = await fetch(base + endpoint, { ...options,
    headers: { Authorization: 'Bearer ' + tokens[role],
      'Content-Type': 'application/json' } });
  const body = await response.json();
  const parsed = schema?.safeParse(body);
  const ok = response.status === expected && (!schema || parsed.success);
  const result = { role, path: endpoint, status: response.status, ok };
  if (!ok) result.detail = parsed?.error?.issues ?? body;
  results.push(result);
  responses[role + ' ' + endpoint] = body;
  return body;
}
for (const entry of state.orders) {
  const prefix = `/api/transport-orders/${entry.orderId}`;
  const services = await check('CUSTOMER', prefix + '/services',
    schemas.services.disclosureSchema);
  if (entry.orderId === 1) {
    await check('CUSTOMER', prefix + '/services/preview',
      schemas.services.disclosureSchema, { method: 'POST', body: JSON.stringify({
        recordVersion: services.recordVersion,
        horseRecordVersion: services.horseRecordVersion,
        services: { ...services.selected, enhancedCare: true },
      }) });
  }
  if (entry.orderId !== 1) await check('CUSTOMER',
    prefix + `/quotes/${entry.quote.quote.quoteId}`, schemas.quotes.quoteResponseSchema);
  await check('COORDINATOR', prefix + `/plans/${entry.plan.planId}`,
    schemas.plans.planDetailSchema);
}
for (const id of [2, 5]) {
  await check('CUSTOMER', `/api/transport-orders/${id}/payments`,
    schemas.payments.overviewSchema);
  await check('SPECIALIST', `/api/transport-orders/${id}/compliance?phase=PRE_EXPORT`,
    schemas.compliance.overviewSchema);
}
await check('COORDINATOR', '/api/transport-orders/3/plans/shared-stall-candidates',
  { safeParse: data => {
    const result = schemas.plans.candidateSchema.array().safeParse(data);
    if (result.success && !data.some(x => x.candidateOrderId === 4 &&
        Number(x.combinedSlotQuantity) === 3)) return { success: false,
          error: { issues: ['Expected DEMO-ORD-04, combined slots 3.0'] } };
    return result;
  } });
const gates = await check('MANAGER', '/api/transport-orders/5/departure',
  schemas.departure.readinessSchema);
if (!gates.readyForAuthorization) results.push({ ok: false, detail: gates });
await check('DRIVER', '/api/transport-orders/5/departure',
  schemas.departure.readinessSchema);
await check('DRIVER', '/api/transport-orders/5/execution');
await check('MANAGER', '/api/manager/staff', schemas.staff.staffPageSchema);
await check('MANAGER', '/api/manager/service-tariffs', schemas.tariffs.tariffPageSchema);
for (const kind of ['vehicles', 'stalls', 'suppliers', 'fit-policies']) {
  await check('MANAGER', '/api/manager/resources/' + kind);
}
for (const role of Object.keys(tokens)) {
  await check(role, '/api/dashboard', role === 'CUSTOMER' ?
    schemas.activity.dashboardSchema : null);
  if (role !== 'CUSTOMER') await check(role, '/api/dashboard/tasks');
  await check(role, '/api/notifications', schemas.activity.notificationsSchema);
}
await check('MANAGER', '/api/manager/reports/financial');
await check('MANAGER', '/api/manager/audit-events');
await check('CUSTOMER', '/api/activity/orders/5/timeline');
await check('CUSTOMER', '/api/manager/reports/financial', null, {}, 403);
await check('CUSTOMER', '/api/manager/staff', null, {}, 403);
await fs.writeFile(path.join(local, 'api-smoke-results.json'),
  JSON.stringify(results, null, 2));
await fs.writeFile(path.join(local, 'api-responses.json'),
  JSON.stringify(responses, null, 2));
const failed = results.filter(result => !result.ok);
console.log(JSON.stringify({ checks: results.length,
  passed: results.length - failed.length, failed }, null, 2));
if (failed.length) process.exitCode = 1;
