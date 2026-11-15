// Headcount Studio over HTTP (1.1.1), on the bare server's Minecraft port: the page, sign-in and the cookie,
// the write checks, saves with backups and the changed-on-disk refusal, YAML errors, console commands, the live
// views, export, path checks, sign-out, a new password, and the lockout after five wrong passwords (last, since
// it locks this address out for five minutes).
const L = require('./lib');
const fs = require('fs');
const { sleep, record, serverConsole, logSize, logSince } = L;
const HC = `${L.S}/botserver/plugins/Headcount/`;
const BASE = `http://127.0.0.1:${L.PORT}`;
const consoleOut = async (cmd, wait = 900) => { const m = logSize(); serverConsole(cmd); await sleep(wait); return logSince(m); };

let cookie = '';
async function call(method, path, body, extra = {}) {
  const headers = { 'X-Studio': '1', ...(cookie ? { Cookie: cookie } : {}), ...extra };
  if (body !== undefined && !(body instanceof Buffer)) headers['Content-Type'] = 'application/json';
  for (const k of Object.keys(headers)) if (headers[k] === null) delete headers[k];
  const r = await fetch(BASE + path, { method, headers, body: body === undefined ? undefined : body instanceof Buffer ? body : JSON.stringify(body) });
  const buf = Buffer.from(await r.arrayBuffer());
  let json = null;
  try { json = JSON.parse(buf.toString('utf8')); } catch (e) { /* not JSON */ }
  return { status: r.status, json, text: buf.toString('utf8'), buf, headers: r.headers };
}
const password = async (cmd = 'hca studio') => {
  const out = await consoleOut(cmd, 1200);
  const m = /Password: (\S+)/.exec(out) || /New Studio password: (\S+?)\./.exec(out);
  return m ? m[1] : null;
};

(async () => {
  const cfgBackup = fs.readFileSync(HC + 'config.yml', 'utf8');
  let r = await call('GET', '/api/ping');
  record('studio: /api/ping answers on the Minecraft port', r.status === 200 && r.json && r.json.ok === true && r.json.app === 'headcount-studio', `${r.status} ${r.text.slice(0, 80)}`);
  r = await call('GET', '/');
  record('studio: the page itself is served', r.status === 200 && /<html/i.test(r.text) && /text\/html/.test(r.headers.get('content-type') || ''), `${r.status}`);
  r = await call('GET', '/api/all');
  record('studio: the API needs a sign-in', r.status === 401, `${r.status}`);
  r = await call('GET', '/api/session');
  record('studio: /api/session says not signed in', r.status === 200 && r.json && r.json.signedIn === false);

  const pw = await password();
  record('studio: /hca studio shows a password', !!pw && pw.length >= 8, String(pw));
  r = await call('POST', '/api/login', { password: pw }, { 'X-Studio': null });
  record('studio: a sign-in without the X-Studio header is refused (cross-site forms)', r.status === 403, `${r.status}`);
  r = await call('POST', '/api/login', { password: pw }, { Origin: 'http://evil.example' });
  record('studio: a sign-in from another origin is refused', r.status === 403, `${r.status}`);
  r = await call('POST', '/api/login', { password: 'wrong-password' });
  record('studio: a wrong password says so', r.status === 401 && /isn't the password/.test(r.text), `${r.status} ${r.text.slice(0, 80)}`);
  r = await call('POST', '/api/login', { password: pw.toUpperCase().replace(/-/g, ' ') });
  record('studio: the password works in capitals and with spaces for dashes', r.status === 200, `${r.status}`);
  const set = r.headers.get('set-cookie') || '';
  cookie = set.split(';')[0];
  record('studio: the session cookie is HttpOnly and SameSite=Strict', /HttpOnly/.test(set) && /SameSite=Strict/.test(set), set.replace(/=[0-9a-f]{20,}/, '=…'));
  let out = await consoleOut('hca studio');
  record('studio: /hca studio counts the signed-in browser', /1 browser|signed in: 1|1 signed in/i.test(out), out.trim().slice(-160));

  // --- reading -----------------------------------------------------------------------------------------------
  r = await call('GET', '/api/all');
  const list = (r.json && r.json.files) || [];
  const files = Object.fromEntries(list.map((f) => [f.name, f]));
  record('studio: /api/all returns every config file with its text', r.status === 200 && files['config.yml'] && files['lang/en_US.yml'] && files['config.yml'].text === cfgBackup, `${r.status} ${list.map((f) => f.name).join(',')}`);
  for (const p of ['/api/live/summary', '/api/live/players', '/api/live/effects', '/api/live/history', '/api/backups', '/api/catalog']) {
    r = await call('GET', p);
    record(`studio: ${p} answers`, r.status === 200 && r.json !== null, `${r.status} ${r.text.slice(0, 60)}`);
  }
  r = await call('GET', '/api/audit?q=studio');
  record('studio: the audit search finds the sign-in', r.status === 200 && /studio-sign-in/.test(r.text), r.text.slice(0, 120));

  // --- saving --------------------------------------------------------------------------------------------------
  const mtimeOf = () => {
    const f = files && files['config.yml'];
    return f && typeof f === 'object' ? f.mtime : null;
  };
  const text1 = cfgBackup.replace(/^(\s*)confirm-timeout: .*$/m, '$1confirm-timeout: 45s');
  r = await call('POST', '/api/save?name=config.yml', { text: text1, mtime: mtimeOf() });
  record('studio: a save writes the file and makes a backup', r.status === 200 && fs.readFileSync(HC + 'config.yml', 'utf8') === text1 && !!(r.json && r.json.backup), `${r.status} ${r.text.slice(0, 160)}`);
  const b1 = r.json && r.json.backup;
  const m1 = r.json && r.json.mtime;
  const text2 = text1.replace('confirm-timeout: 45s', 'confirm-timeout: 50s');
  r = await call('POST', '/api/save?name=config.yml', { text: text2, mtime: m1 });
  const b2 = r.json && r.json.backup;
  record('studio: a second save in the same second keeps both backups', r.status === 200 && b1 && b2 && b1 !== b2 && fs.existsSync(HC + b1) && fs.existsSync(HC + b2)
    && fs.readFileSync(HC + b1, 'utf8') === cfgBackup && fs.readFileSync(HC + b2, 'utf8') === text1, `${b1} / ${b2}`);
  r = await call('GET', '/api/backups');
  record('studio: the backups list shows them, tied to config.yml', r.status === 200 && Array.isArray(r.json) && r.json.filter((x) => x.file === 'config.yml').length >= 2);
  r = await call('GET', `/api/backup?path=${encodeURIComponent(b1)}`);
  record('studio: a backup can be read back', r.status === 200 && r.json && r.json.text === cfgBackup);
  r = await call('POST', '/api/save?name=config.yml', { text: cfgBackup, mtime: (m1 || 0) - 100 });
  record('studio: a save over a file that changed on disk is refused', r.status === 409 && /changed on disk/.test(r.text), `${r.status} ${r.text.slice(0, 80)}`);
  r = await call('POST', '/api/save?name=config.yml', { text: 'cycle:\n  length-days: [oops\n' });
  record('studio: invalid YAML is refused and the file kept', r.status === 422 && fs.readFileSync(HC + 'config.yml', 'utf8') === text2, `${r.status} ${r.text.slice(0, 100)}`);
  r = await call('POST', '/api/save?name=../../server.properties', { text: 'x' });
  record('studio: only the known config files can be saved', r.status === 400, `${r.status}`);
  r = await call('POST', '/api/save?name=config.yml', { text: cfgBackup, force: true });
  record('studio: a forced save goes through', r.status === 200 && fs.readFileSync(HC + 'config.yml', 'utf8') === cfgBackup, `${r.status}`);
  out = await consoleOut('hca audit type:admin studio-save', 2000);
  record('studio: saves are in the audit log', /studio-save file=config\.yml/.test(out), out.trim().slice(-160));

  // --- commands, export, paths ------------------------------------------------------------------------------------
  r = await call('POST', '/api/command', { command: '/hca selftest' });
  record('studio: a console command runs and its reply comes back', r.status === 200 && r.json && /self.?test|pass|check/i.test(r.json.reply), `${r.status} ${(r.json && r.json.reply || r.text).slice(0, 120)}`);
  r = await call('POST', '/api/command', { command: 'list' });
  record('studio: vanilla commands reply too', r.status === 200 && r.json && /players online/i.test(r.json.reply), (r.json && r.json.reply || '').slice(0, 80));
  r = await call('GET', '/api/export');
  record('studio: export gives a zip of the configs', r.status === 200 && r.buf.slice(0, 2).toString() === 'PK' && r.buf.includes(Buffer.from('config.yml')), `${r.status} ${r.buf.length} bytes`);
  r = await call('GET', '/api/backup?path=../config.yml');
  record('studio: a backup path outside backups/ is refused', r.status === 404, `${r.status}`);
  r = await call('GET', '/..%2Fconfig.yml');
  record('studio: a page path with .. is refused', r.status === 404 && !/length-days/.test(r.text), `${r.status}`);
  r = await call('GET', '/api/audit/head?id=../../x');
  record('studio: a bad head ID is refused', r.status === 400, `${r.status}`);

  // --- sign-out and a new password --------------------------------------------------------------------------------
  r = await call('POST', '/api/logout', {});
  const old = cookie;
  r = await call('GET', '/api/all');
  record('studio: after signing out, the cookie no longer works', r.status === 401, `${r.status}`);
  cookie = '';
  r = await call('POST', '/api/login', { password: pw });
  cookie = (r.headers.get('set-cookie') || '').split(';')[0];
  const pw2 = await password('hca studio reset');
  r = await call('GET', '/api/all');
  record('studio: /hca studio reset signs every browser out', r.status === 401 && cookie !== old, `${r.status}`);
  cookie = '';
  r = await call('POST', '/api/login', { password: pw });
  record('studio: the old password no longer works', r.status === 401, `${r.status}`);
  r = await call('POST', '/api/login', { password: pw2 });
  record('studio: the new password works', r.status === 200 && !!pw2, `${r.status} ${pw2}`);

  // --- lockout: five wrong passwords, then even the right one waits --------------------------------------------------
  cookie = '';
  for (let i = 0; i < 4; i++) await call('POST', '/api/login', { password: 'nope' + i });   // the old password above was the first
  r = await call('POST', '/api/login', { password: 'nope-last' });
  r = await call('POST', '/api/login', { password: pw2 });
  record('studio: after five wrong passwords, this address is locked out', r.status === 429 && /Too many wrong passwords/.test(r.text), `${r.status} ${r.text.slice(0, 80)}`);

  fs.writeFileSync(HC + 'config.yml', cfgBackup);
  process.exit(L.summary() ? 1 : 0);
})().catch((e) => { console.log('HARNESS ERROR', e.stack); L.summary(); process.exit(2); });
