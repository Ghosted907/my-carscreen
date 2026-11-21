import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
const source = (await readFile(new URL('./worker.mjs', import.meta.url), 'utf8'))
  .replace('import { DurableObject } from "cloudflare:workers";', 'class DurableObject { constructor(ctx, env) { this.ctx = ctx; this.env = env; } }');
const { default: worker, T1Relay } = await import('data:text/javascript;base64,' + Buffer.from(source).toString('base64'));
const NativeResponse = globalThis.Response;
globalThis.Response = class extends NativeResponse {
  constructor(body, options = {}) {
    if (options.status === 101) { super(null); Object.defineProperty(this, 'status', { value: 101 }); this.webSocket = options.webSocket; }
    else super(body, options);
  }
};
class Socket {
  messages = []; attachment = null; closed = false;
  send(text) { if (this.closed) throw Error('closed'); this.messages.push(JSON.parse(text)); }
  close(code, reason) { this.closed = true; this.closeCode = code; this.reason = reason; }
  serializeAttachment(a) { this.attachment = structuredClone(a); }
  deserializeAttachment() { return structuredClone(this.attachment); }
}
globalThis.WebSocketPair = class { constructor() { this[0] = new Socket(); this[1] = new Socket(); } };
function context() {
  const data = new Map(), sockets = [];
  return {
    data, sockets,
    storage: { get: async k => structuredClone(data.get(k)), put: async (k, v) => data.set(k, structuredClone(v)) },
    blockConcurrencyWhile: async f => f(),
    acceptWebSocket(ws, tags) { sockets.push({ ws, tags }); },
    getWebSockets: role => sockets.filter(s => !s.ws.closed && (!role || s.tags.includes(role))).map(s => s.ws)
  };
}
const room = 'a'.repeat(24), hostKey = 'b'.repeat(64), phoneKey = 'c'.repeat(32);
const digest = async s => Buffer.from(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(s))).toString('hex');
const req = (role, key, extra = {}) => new Request(`https://relay.test/v1/rooms/${room}/${role}`, {
  headers: { Upgrade: 'websocket', Authorization: `Bearer ${key}`, ...extra }
});
async function setup() {
  const ctx = context(), relay = new T1Relay(ctx, {});
  assert.equal((await relay.fetch(req('host', hostKey, { 'X-T1-Phone-Hash': await digest(phoneKey) }))).status, 101);
  return { ctx, relay, host: ctx.getWebSockets('host')[0] };
}
async function phone(setup) {
  assert.equal((await setup.relay.fetch(req('phone', phoneKey))).status, 101);
  return setup.ctx.getWebSockets('phone')[0];
}
test('health distinguishes an unbound worker; missing credentials are rejected before a room is accessed', async () => {
  assert.equal((await (await worker.fetch(new Request('https://relay.test/health'), {})).json()).ready, false);
  assert.equal((await worker.fetch(req('phone', ''), {})).status, 401);
});
test('phone cannot register; host secret and phone secret cannot impersonate each other; only hashes persist', async () => {
  const empty = new T1Relay(context(), {});
  assert.equal((await empty.fetch(req('phone', phoneKey))).status, 401);
  const s = await setup();
  assert.equal((await s.relay.fetch(req('host', 'd'.repeat(64), { 'X-T1-Phone-Hash': await digest(phoneKey) }))).status, 401);
  assert.equal((await s.relay.fetch(req('phone', 'd'.repeat(32)))).status, 401);
  assert.equal(s.ctx.data.size, 1);
  assert.deepEqual(s.ctx.data.get('credentials'), { host: await digest(hostKey), phone: await digest(phoneKey) });
});
test('handshake, command deadline and matching reply survive hibernation without stored commands', async () => {
  const s = await setup(), p = await phone(s), session = p.deserializeAttachment().session;
  s.relay = new T1Relay(s.ctx, {}); // Worker memory can disappear while sockets remain connected.
  const hello = { t: 'hello', v: 2, name: 'T1' };
  s.relay.webSocketMessage(s.host, JSON.stringify({ t: 'hello', session, hello }));
  assert.deepEqual(p.messages.at(-1), hello);
  const command = { t: 'cmd', seq: 1, body: { op: 'ping' } }, before = Date.now();
  s.relay.webSocketMessage(p, JSON.stringify(command));
  const forwarded = s.host.messages.at(-1);
  assert.deepEqual(forwarded.command, command); assert.equal(forwarded.session, session);
  assert.ok(forwarded.deadline >= before + 10000 && forwarded.deadline <= Date.now() + 10000);
  const response = { t: 'res', seq: 1, ok: true, data: { rev: 4 } };
  s.relay.webSocketMessage(s.host, JSON.stringify({ t: 'reply', session, response }));
  assert.deepEqual(p.messages.at(-1), response); assert.equal(s.ctx.data.size, 1);
});
test('replaced phone cannot send and old replies cannot reach the next phone', async () => {
  const s = await setup(), old = await phone(s), oldSession = old.deserializeAttachment().session;
  const next = await phone(s), count = next.messages.length, hostCount = s.host.messages.length;
  assert.ok(old.closed);
  s.relay.webSocketMessage(old, JSON.stringify({ t: 'cmd', seq: 1, body: { op: 'vehicle' } }));
  assert.equal(s.host.messages.length, hostCount);
  s.relay.webSocketMessage(s.host, JSON.stringify({ t: 'reply', session: oldSession, response: { t: 'res', seq: 1 } }));
  assert.equal(next.messages.length, count);
});
test('duplicate commands are rejected, and an offline launcher rejects phone connections', async () => {
  const s = await setup(), p = await phone(s);
  const command = JSON.stringify({ t: 'cmd', seq: 1, body: { op: 'vehicle' } });
  s.relay.webSocketMessage(p, command); const count = s.host.messages.length;
  s.relay.webSocketMessage(p, command); assert.equal(s.host.messages.length, count); assert.equal(p.closeCode, 1008);
  s.relay.webSocketClose(s.host, 1006);
  assert.equal((await s.relay.fetch(req('phone', phoneKey))).status, 409);
});
test('host reconnect retires every previous phone; oversized and binary payloads are rejected', async () => {
  const s = await setup(), oldPhone = await phone(s);
  await s.relay.fetch(req('host', hostKey)); assert.ok(oldPhone.closed); assert.ok(s.host.closed);
  const p = await phone(s); s.relay.webSocketMessage(p, new Uint8Array(3)); assert.equal(p.closeCode, 1009);
  const p2 = await phone(s); s.relay.webSocketMessage(p2, 'x'.repeat(1100001)); assert.equal(p2.closeCode, 1009);
});
