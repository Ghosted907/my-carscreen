import { DurableObject } from "cloudflare:workers";
// T1 Remote relay. Only credential hashes are stored; commands and readings are never stored.
const MAX_MESSAGE = 1100000;
const DEADLINE_MS = 10000;
const ROOM = /^\/v1\/rooms\/([a-f0-9]{24})\/(host|phone)$/;
const HOST_KEY = /^[a-f0-9]{64}$/;
const PHONE_KEY = /^[a-f0-9]{32}$/;
const json = (data, status = 200) => new Response(JSON.stringify(data), {
  status, headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' }
});
const hash = async value => Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',
  new TextEncoder().encode(value))), b => b.toString(16).padStart(2, '0')).join('');
const token = request => (request.headers.get('Authorization') || '').replace(/^Bearer /, '');
const fail = () => json({ error: 'unauthorized' }, 401);

export default {
  async fetch(request, env) {
    const path = new URL(request.url).pathname;
    if (request.method === 'GET' && (path === '/' || path === '/health'))
      return json({ service: 'T1 Remote Relay', version: 1, ready: !!env.ROOMS, transport: 'wss', storesTelemetry: false });
    const route = path.match(ROOM);
    if (!route) return json({ error: 'not_found' }, 404);
    if (request.method !== 'GET' || request.headers.get('Upgrade')?.toLowerCase() !== 'websocket')
      return json({ error: 'websocket_required' }, 426);
    if (!(route[2] === 'host' ? HOST_KEY : PHONE_KEY).test(token(request))) return fail();
    if (!env.ROOMS) return json({ error: 'not_configured' }, 503);
    return env.ROOMS.get(env.ROOMS.idFromName(route[1])).fetch(request);
  }
};

export class T1Relay extends DurableObject {
  constructor(ctx, env) { super(ctx, env); }
  sockets(role) {
    return this.ctx.getWebSockets(role).filter(ws => ws.deserializeAttachment()?.active);
  }
  send(ws, body) {
    try { ws.send(JSON.stringify(body)); return true; } catch { return false; }
  }
  retire(ws, code, reason) {
    const a = ws.deserializeAttachment();
    if (a) ws.serializeAttachment({ ...a, active: false });
    try { ws.close(code, reason); } catch {}
  }
  async fetch(request) {
    const role = new URL(request.url).pathname.match(ROOM)?.[2];
    if (!role || !(role === 'host' ? HOST_KEY : PHONE_KEY).test(token(request))) return fail();
    const secretHash = await hash(token(request));
    const phoneHash = request.headers.get('X-T1-Phone-Hash');
    const auth = await this.ctx.blockConcurrencyWhile(async () => {
      let credentials = await this.ctx.storage.get('credentials');
      if (!credentials && role === 'host' && HOST_KEY.test(phoneHash || '')) {
        credentials = { host: secretHash, phone: phoneHash };
        await this.ctx.storage.put('credentials', credentials);
      }
      return credentials?.[role] === secretHash;
    });
    if (!auth) return fail();
    const host = this.sockets('host')[0];
    if (role === 'phone' && !host) return json({ error: 'launcher_offline' }, 409);
    // A new connection invalidates old sessions, including queued replies.
    if (role === 'host') {
      for (const ws of [...this.sockets('host'), ...this.sockets('phone')])
        this.retire(ws, 4001, 'launcher_reconnected');
    } else {
      for (const ws of this.sockets('phone')) this.retire(ws, 4001, 'phone_replaced');
    }
    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);
    const session = crypto.randomUUID();
    server.serializeAttachment({ role, session, active: true, seq: 0, window: 0, count: 0 });
    this.ctx.acceptWebSocket(server, [role]);
    this.send(server, { t: 'relay', event: 'ready', serverTime: Date.now() });
    if (role === 'phone') this.send(host, { t: 'relay', event: 'phone_connected', session });
    return new Response(null, { status: 101, webSocket: client });
  }
  webSocketMessage(ws, message) {
    const a = ws.deserializeAttachment();
    if (!a?.active) return;
    if (typeof message !== 'string' || new TextEncoder().encode(message).length > MAX_MESSAGE) {
      this.retire(ws, 1009, 'message_too_large'); return;
    }
    const now = Date.now();
    if (now - a.window >= 1000) { a.window = now; a.count = 0; }
    if (++a.count > 40) { this.retire(ws, 1008, 'rate_limit'); return; }
    ws.serializeAttachment(a);
    let data;
    try { data = JSON.parse(message); } catch { this.retire(ws, 1008, 'invalid_json'); return; }
    if (!data || typeof data !== 'object' || Array.isArray(data)) { this.retire(ws, 1008, 'invalid_message'); return; }
    if (a.role === 'phone') {
      if (data.t !== 'cmd' || !Number.isSafeInteger(data.seq) || data.seq <= a.seq ||
          !data.body || typeof data.body !== 'object' || Array.isArray(data.body)) {
        this.retire(ws, 1008, 'invalid_command'); return;
      }
      a.seq = data.seq; ws.serializeAttachment(a);
      const host = this.sockets('host')[0];
      if (!host || !this.send(host, { t: 'forward', session: a.session, deadline: now + DEADLINE_MS, command: data }))
        this.send(ws, { t: 'res', seq: data.seq, ok: false, msg: 'اللانشر غير متصل؛ لم يُرسل الأمر' });
    } else {
      const phone = this.sockets('phone').find(p => p.deserializeAttachment()?.session === data.session);
      if (!phone) return;
      if (data.t === 'hello' && data.hello?.t === 'hello') this.send(phone, data.hello);
      else if (data.t === 'reply' && data.response?.t === 'res' && Number.isSafeInteger(data.response.seq))
        this.send(phone, data.response);
    }
  }
  webSocketClose(ws, code) {
    const a = ws.deserializeAttachment();
    this.retire(ws, code >= 1000 && code < 5000 && ![1004, 1005, 1006, 1015].includes(code) ? code : 1000, 'closed');
    if (!a?.active) return;
    if (a.role === 'host') {
      for (const p of this.sockets('phone')) this.retire(p, 4002, 'launcher_offline');
    } else {
      for (const h of this.sockets('host')) this.send(h, { t: 'relay', event: 'phone_disconnected', session: a.session });
    }
  }
  webSocketError(ws) { this.webSocketClose(ws, 1011); }
}
