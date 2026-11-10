// Bedrock smoke test through Geyser (main test server, port 19132), with two bedrock-protocol clients.
// Run with Geyser's validate-bedrock-login off (the bots can't sign in to Xbox) and Headcount's bedrock provider
// set to geyser (Floodgate needs a real Xbox login).
const { createClient } = require('bedrock-protocol');
const fs = require('fs');
// The main test server (TestServer/ next to the repository, or $HC_TEST_SERVER).
const LOG = require('path').join(process.env.HC_TEST_SERVER || require('path').join(__dirname, '../../../../TestServer'), 'logs/latest.log');
const CONSOLE = process.argv[2];   // the server's console input file
const MODE = process.argv[3] || 'single';   // single: one bot (status, vault, sidebar); trade: two bots (status, trade text)
const results = [];
const record = (name, ok, detail = '') => { results.push(ok); console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  -- ' + detail : ''}`); };
const J = (x) => JSON.stringify(x, (k, v) => (typeof v === 'bigint' ? v.toString() : v));
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const consoleCmd = (c) => fs.appendFileSync(CONSOLE, c + '\n');
const logSize = () => fs.statSync(LOG).size;
const logSince = (o) => fs.readFileSync(LOG).slice(o).toString();

function bot(name) {
  return new Promise((resolve, reject) => {
    const c = createClient({ host: '127.0.0.1', port: 19132, username: name, offline: true, version: '1.26.60', skipPing: true });
    // offline profiles all have XUID 0 (Geyser then sees one player twice): give each its own. The offline
    // session is made inside createClient, before any listener could see it, so set it on the profile here.
    if (c.profile) c.profile.xuid = String(2535400000000000 + (name.endsWith('One') ? 1 : 2));
    c.chat = [];
    c.packets = {};
    c.on('text', (p) => c.chat.push(String(p.message || '') + ' ' + JSON.stringify(p.parameters || '')));
    for (const n of ['container_open', 'inventory_content', 'set_display_objective', 'set_score', 'modal_form_request']) c.on(n, (p) => { (c.packets[n] = c.packets[n] || []).push(p); });
    c.on('spawn', () => resolve(c));
    c.on('error', (e) => console.log(`[${name}] ERROR ${e.message}`));
    c.on('kick', (r) => console.log(`[${name}] KICKED ${JSON.stringify(r).slice(0, 200)}`));
    setTimeout(() => reject(new Error(name + ' did not spawn')), 45000);
  });
}
const command = (c, cmd) => c.queue('command_request', {
  command: cmd, origin: { type: 'player', uuid: c.profile && c.profile.uuid ? c.profile.uuid : '00000000-0000-0000-0000-000000000000', request_id: '', player_entity_id: 0n },
  internal: false, version: '52',
});
const waitFor = async (pred, t = 5000) => { const end = Date.now() + t; while (Date.now() < end) { if (pred()) return true; await sleep(150); } return false; };

(async () => {
  const m0 = logSize();
  const b1 = await bot(process.env.B1 || 'BedrockOne');
  const b2 = MODE === 'trade' ? await bot(process.env.B2 || 'BedrockTwo') : null;
  await sleep(4000);
  const B1 = process.env.B1 || 'BedrockOne', B2 = process.env.B2 || 'BedrockTwo';
  const joined = logSince(m0).match(new RegExp(`(\\S*${B1})\\[\\/[^\\]]*\\] logged in`));
  const j1 = joined ? joined[1] : B1;
  const j2 = j1.replace(B1, B2);
  record(`bedrock (${MODE}): the client joined the Java server through Geyser`, !!joined, j1);
  let m = logSize();
  consoleCmd(`hca status ${j1} bedrock-player`);
  await sleep(1200);
  record('bedrock: Headcount sees the player as Bedrock (status bedrock-player)', /bedrock-player: true/.test(logSince(m)), logSince(m).split('\n').filter((l) => /bedrock/.test(l)).join(' ').slice(0, 120));
  consoleCmd(`pvp ${j1} off`);
  consoleCmd(`pvp ${j2} off`);
  await sleep(1000);
  if (MODE === 'trade') {
  // trade request: the Bedrock text, no click link
  const before = b2.chat.length;
  command(b1, `/trade ${j2}`);
  const got = await waitFor(() => b2.chat.slice(before).some((x) => /wants to trade/.test(x)), 6000);
  const text = b2.chat.slice(before).join(' | ');
  record('bedrock: a trade request to a Bedrock player uses the typed-command text', got && /Type \/trade accept|\/trade accept/.test(text) && !/\[Accept\]/.test(text), text.slice(0, 160));
  command(b1, '/trade cancel');
  await sleep(1000);
  } else {
  // vault menu
  b1.packets = {};
  command(b1, '/hc');
  const opened = await waitFor(() => (b1.packets.container_open || []).length > 0, 6000);
  await sleep(1500);
  const contents = (b1.packets.inventory_content || []).map((p) => (p.input || p.items || []).filter((i) => i && i.network_id && i.network_id !== 0).length);
  record('bedrock: /hc opens the vault as a chest menu (Geyser container)', opened, `container_open=${(b1.packets.container_open || []).length} non-empty slots per content packet=${contents.join(',')}`);
  b1.queue('container_close', { window_id: (b1.packets.container_open || [{}])[0].window_id || 0, window_type: 'none', server: false });
  await sleep(800);
  // sidebar
  // the per-player choice is remembered: toggle until it says on, and watch the packets from that toggle
  for (let i = 0; i < 2; i++) {
    b1.packets = {};
    const mc = b1.chat.length;
    command(b1, '/hc sidebar');
    await waitFor(() => b1.chat.slice(mc).some((x) => /Sidebar (on|off)/.test(x)), 4000);
    if (b1.chat.slice(mc).some((x) => /Sidebar on/.test(x))) break;
  }
  const side = await waitFor(() => (b1.packets.set_display_objective || []).some((p) => p.display_slot === 'sidebar'), 6000);
  await sleep(2000);
  const lines = J(b1.packets.set_score || []);
  record('bedrock: the sidebar reaches the Bedrock client as a sidebar objective with its lines', side && /Unique heads|Cycle/.test(lines + J(b1.packets.set_display_objective)),
    `objectives=${(b1.packets.set_display_objective || []).length} score packets=${(b1.packets.set_score || []).length}`);
  command(b1, '/hc sidebar');           // and off again
  await sleep(800);
  }
  b1.close();
  if (b2) b2.close();
  await sleep(1000);
  const failed = results.filter((r) => !r).length;
  console.log(`\n${results.length - failed} passed, ${failed} failed`);
  process.exit(failed ? 1 : 0);
})().catch((e) => { console.log('HARNESS ERROR', e); process.exit(2); });
