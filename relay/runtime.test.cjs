const { test } = require('node:test');
const assert = require('node:assert/strict');
const { randomBytes, createHash } = require('node:crypto');
const { resolve } = require('node:path');
const { Miniflare, Log, LogLevel, convertV4MiniflareOptions } = require('miniflare');

function incoming(ws) {
  const queued = [], waiting = [];
  ws.addEventListener('message', e => {
    const value = JSON.parse(e.data);
    if (waiting.length) waiting.shift()(value); else queued.push(value);
  });
  ws.accept();
  return () => queued.length ? Promise.resolve(queued.shift()) : new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(Error('Timed out waiting for a relay message')), 5000);
    waiting.push(value => { clearTimeout(timer); resolve(value); });
  });
}

test('workerd accepts real host/phone sockets, forwards hello and replies, and rejects a wrong key', { timeout: 30000 }, async () => {
  const mf = new Miniflare(convertV4MiniflareOptions({
    name: 't1-relay-check', modules: true, scriptPath: resolve(__dirname, 'worker.mjs'),
    compatibilityDate: '2026-11-21',
    durableObjects: { ROOMS: { className: 'T1Relay', useSQLite: true } },
    log: new Log(LogLevel.ERROR)
  }));
  let host, phone;
  try {
    const health = await mf.dispatchFetch('http://relay.test/health');
    assert.equal((await health.json()).ready, true);
    const room = randomBytes(12).toString('hex'), hostKey = randomBytes(32).toString('hex'), phoneKey = randomBytes(16).toString('hex');
    const hostResponse = await mf.dispatchFetch(`http://relay.test/v1/rooms/${room}/host`, {
      headers: { Upgrade: 'websocket', Authorization: `Bearer ${hostKey}`, 'X-T1-Phone-Hash': createHash('sha256').update(phoneKey).digest('hex') }
    });
    assert.equal(hostResponse.status, 101, 'host handshake must succeed');
    host = hostResponse.webSocket;
    const fromHost = incoming(host);
    assert.equal((await fromHost()).event, 'ready');
    const phoneResponse = await mf.dispatchFetch(`http://relay.test/v1/rooms/${room}/phone`, {
      headers: { Upgrade: 'websocket', Authorization: `Bearer ${phoneKey}` }
    });
    assert.equal(phoneResponse.status, 101);
    phone = phoneResponse.webSocket;
    const fromPhone = incoming(phone);
    assert.equal((await fromPhone()).event, 'ready');
    const session = (await fromHost()).session;
    host.send(JSON.stringify({ t: 'hello', session, hello: { t: 'hello', v: 2, name: 'T1' } }));
    assert.equal((await fromPhone()).t, 'hello');
    phone.send(JSON.stringify({ t: 'cmd', seq: 1, body: { op: 'ping' } }));
    const forwarded = await fromHost();
    assert.equal(forwarded.t, 'forward'); assert.equal(forwarded.command.seq, 1);
    host.send(JSON.stringify({ t: 'reply', session, response: { t: 'res', seq: 1, ok: true, data: { rev: 4 } } }));
    assert.equal((await fromPhone()).ok, true);
    const denied = await mf.dispatchFetch(`http://relay.test/v1/rooms/${room}/phone`, {
      headers: { Upgrade: 'websocket', Authorization: `Bearer ${randomBytes(16).toString('hex')}` }
    });
    assert.equal(denied.status, 401);
  } finally {
    try { phone?.close(); } catch {}
    try { host?.close(); } catch {}
    await mf.dispose();
  }
});
