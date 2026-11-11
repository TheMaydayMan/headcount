// Titles (equip menu, force lock, LuckPerms prefix), the sidebar toggles, the admin history view, vault paging
// above 54 slots, bypass permissions and the opening gate.
const L = require('./lib');
const WORK = L.S;
const fs = require('fs');
const { sleep, record, serverConsole, logSize, logSince, db, makeBot, waitChat, openWindow } = L;
const S = `${WORK}`;
const HC = S + '/botserver/plugins/Headcount/';
const consoleOut = async (cmd, wait = 900) => { const m = logSize(); serverConsole(cmd); await sleep(wait); return logSince(m); };
const N = process.argv[2] || 'Uniform';
const pl = (col) => db(`SELECT ${col} FROM players WHERE name='${N}'`)[0][0];
const vaultCount = () => db(`SELECT COUNT(*) FROM vault_items v JOIN players p ON p.uuid=v.uuid WHERE p.name='${N}'`)[0][0];
const text = (s) => (s ? JSON.stringify(s.components || '') + (s.customName || '') : '');
const edit = (file, pairs) => {
  let s = fs.readFileSync(HC + file, 'utf8');
  for (const [a, b] of pairs) {
    if (!s.includes(a)) throw new Error(`${file}: "${a.slice(0, 50)}" not found`);
    s = s.replace(a, b);
  }
  fs.writeFileSync(HC + file, s);
};
const reload = () => consoleOut('hca reload', 2500);
const shown = async () => ((await consoleOut(`papi parse ${N} %headcount_title_raw%`, 800)).trim().split('\n').filter((l) => !/HCADDON|issued server command/.test(l)).pop() || '');
const isPvP = async () => /in-pvp: true/.test(await consoleOut(`hca status ${N} in-pvp`, 700));

(async () => {
  const backups = Object.fromEntries(['config.yml', 'titles.yml'].map((f) => [f, fs.readFileSync(HC + f, 'utf8')]));
  const u = await makeBot(N);
  const a = await makeBot('Alpha');
  await sleep(3000);

  // --- titles ------------------------------------------------------------------------------------------
  serverConsole(`hca title ${N} grant collector`);
  serverConsole(`hca title ${N} grant champion`);
  await sleep(1200);
  record('titles: the first title granted is equipped automatically', pl('equipped_title') === 'collector');
  let w = await openWindow(u, '/hc titles');
  const slotOf = (win, re) => win ? win.slots.findIndex((s, i) => i < win.inventoryStart && s && re.test(text(s))) : -1;
  let mu = u.chatLog.length;
  await u.clickWindow(slotOf(w, /Champion/), 0, 0);
  await sleep(1000);
  record('titles: clicking an owned title in the menu equips it', pl('equipped_title') === 'champion' && /Champion/.test(await shown()));
  u.closeWindow(u.currentWindow);
  await sleep(400);
  serverConsole(`hca run ${N} ta-force`);  // dunce for 6 s
  await sleep(1000);
  w = await openWindow(u, '/hc titles');
  mu = u.chatLog.length;
  await u.clickWindow(slotOf(w, /Collector/), 0, 0);
  record('titles: while a title is forced, equipping another is refused', await waitChat(u, /title is locked right now/, mu, 2000) && pl('equipped_title') === 'champion' && /Dunce/.test(await shown()));
  u.closeWindow(u.currentWindow);
  await sleep(6000);
  record('titles: when the force ends, the equipped title is shown again', /Champion/.test(await shown()));
  fs.writeFileSync(HC + 'titles.yml', fs.readFileSync(HC + 'titles.yml', 'utf8').replace(/(permission-prefix:\n(?:\s*#[^\n]*\n)*\s*)enabled: false/, '$1enabled: true'));
  if (!/permission-prefix:\n(?:\s*#[^\n]*\n)*\s*enabled: true/.test(fs.readFileSync(HC + 'titles.yml', 'utf8'))) throw new Error('permission-prefix edit did not apply');
  await reload();
  let meta = '';
  for (let i = 0; i < 6 && !/Champion/.test(meta); i++) meta = await consoleOut(`lp user ${N} meta info`, 800);
  record('titles: with permission-prefix on, the shown title becomes a LuckPerms prefix', /Champion/.test(meta), meta.split('\n').filter((l) => /prefix|Champion/i.test(l)).join(' | ').slice(0, 140));
  w = await openWindow(u, '/hc titles');
  await u.clickWindow(slotOf(w, /Champion/), 0, 0);   // unequip
  await sleep(1000);
  u.closeWindow(u.currentWindow);
  for (let i = 0; i < 6 && /Champion/.test(meta); i++) meta = await consoleOut(`lp user ${N} meta info`, 800);
  record('titles: unequipping removes the prefix', !/Champion/.test(meta) && pl('equipped_title') === null);
  fs.writeFileSync(HC + 'titles.yml', backups['titles.yml']);
  await reload();

  // --- sidebar ------------------------------------------------------------------------------------------
  const sidebarText = () => JSON.stringify(Object.values(u.teams || {}).map((t) => t.prefix && (t.prefix.toString ? t.prefix.toString() : t.prefix)));
  record('sidebar: a new player starts with it on, with the configured lines', /Unique heads/.test(sidebarText()) && pl('sidebar') === 1);
  record('sidebar: Bedrock-safe layout (lines are team prefixes on unique entries, scores hidden)', !!u.scoreboard && !!u.scoreboard.sidebar);
  mu = u.chatLog.length;
  u.chat('/hc sidebar');
  await sleep(1500);
  record('sidebar: /hc sidebar turns it off', await waitChat(u, /Sidebar off/, mu, 1500) && pl('sidebar') === 0);
  u.quit();
  await sleep(1500);
  let u2 = await makeBot(N);
  await sleep(3000);
  record('sidebar: the choice is remembered after rejoining', pl('sidebar') === 0 && !(u2.scoreboard && u2.scoreboard.sidebar));
  mu = u2.chatLog.length;
  u2.chat('/hc sidebar');
  await sleep(2000);
  record('sidebar: /hc sidebar again turns it on', await waitChat(u2, /Sidebar on/, mu, 1500) && pl('sidebar') === 1 &&
    /Unique heads/.test(JSON.stringify(Object.values(u2.teams || {}).map((t) => t.prefix && t.prefix.toString()))));
  edit('config.yml', [['sidebar:\n', 'sidebar:\n  enabled: false\n'], ['  enabled: true\n  update-interval: 1s', '  update-interval: 1s']]);
  await reload();
  mu = u2.chatLog.length;
  u2.chat('/hc sidebar');
  record('sidebar: with sidebar.enabled false, players are told it is disabled', await waitChat(u2, /sidebar is disabled on this server/, mu, 2000));
  fs.writeFileSync(HC + 'config.yml', backups['config.yml']);
  await reload();

  // --- vault paging (60 slots) -----------------------------------------------------------------------
  edit('config.yml', [['  size: 27\n', '  size: 60\n'], ['  starting-heads: 18\n', '  starting-heads: 60\n']]);
  await reload();
  await consoleOut('hca cycle end confirm', 5000);
  await consoleOut(`pvp ${N} off`, 900);   // the console isn't held to PvPManager's toggle cooldown
  record('vault: a 60-slot cycle fills 60 heads', vaultCount() === 60, `vault=${vaultCount()}`);
  w = await openWindow(u2, '/hc');
  const heads = (win) => win.slots.slice(0, 45).filter((s) => s && s.name === 'player_head').length;
  record('vault: page 1 shows 45 heads and a next-page button', !!w && heads(w) === 45 && w.slots[53] && w.slots[53].name === 'arrow', w && `heads=${heads(w)}`);
  let o = new Promise((r) => u2.once('windowOpen', r));
  await u2.clickWindow(53, 0, 0);
  w = await Promise.race([o, sleep(3000).then(() => null)]);
  await sleep(600);
  record('vault: page 2 shows the other 15, with locked slots after them', !!w && heads(w) === 15 && w.slots[20] && w.slots[20].name === 'black_stained_glass_pane', w && `heads=${heads(w)}`);
  await u2.clickWindow(0, 0, 1);           // shift-click a head out from page 2
  await sleep(900);
  record('vault: moving a head out on page 2 is saved', vaultCount() === 59);
  o = new Promise((r) => u2.once('windowOpen', r));
  await u2.clickWindow(45, 0, 0);
  w = await Promise.race([o, sleep(3000).then(() => null)]);
  await sleep(500);
  record('vault: previous-page goes back to page 1', !!w && heads(w) === 45);
  u2.closeWindow(u2.currentWindow);
  await sleep(400);

  // --- bypass permissions and the opening gate ---------------------------------------------------------
  await consoleOut(`pvp ${N} on`, 900);    // PvP on: moves are blocked
  w = await openWindow(u2, '/hc');
  mu = u2.chatLog.length;
  await u2.clickWindow(0, 0, 1);
  await sleep(900);
  record('gate: with PvP on, a move is refused', vaultCount() === 59 && await waitChat(u2, /PvP is on/, mu, 1500));
  u2.closeWindow(u2.currentWindow);
  await consoleOut(`lp user ${N} permission set headcount.bypass.vault-move.in-pvp true`, 1500);
  w = await openWindow(u2, '/hc');
  await u2.clickWindow(0, 0, 1);
  await sleep(900);
  record('bypass: headcount.bypass.vault-move.in-pvp lets the move through', vaultCount() === 58, `vault=${vaultCount()}`);
  u2.closeWindow(u2.currentWindow);
  await consoleOut(`lp user ${N} permission unset headcount.bypass.vault-move.in-pvp`, 1500);
  edit('config.yml', [['    block-opening: false', '    block-opening: true']]);
  await reload();
  mu = u2.chatLog.length;
  w = await openWindow(u2, '/hc', 2500);
  record('gate: with block-opening on, the vault does not open while PvP is on', !w && await waitChat(u2, /can't open your vault while your PvP is on/, mu, 1500));
  await consoleOut(`lp user ${N} permission set headcount.bypass.vault-open.in-pvp true`, 1500);
  w = await openWindow(u2, '/hc', 2500);
  record('bypass: headcount.bypass.vault-open.in-pvp opens it anyway', !!w);
  if (u2.currentWindow) u2.closeWindow(u2.currentWindow);
  await consoleOut(`lp user ${N} permission unset headcount.bypass.vault-open.in-pvp`, 1000);
  await consoleOut(`pvp ${N} off`, 900);

  // --- admin history view ---------------------------------------------------------------------------------
  w = await openWindow(a, `/hca history ${N}`);
  record('history: /hca history <player> opens that player\'s history for an admin', !!w && w.slots.slice(0, 45).some((s) => s && /Cycle \d/.test(text(s))) && new RegExp(N).test(JSON.stringify(w.title)),
    w && JSON.stringify(w.title).slice(0, 100));
  if (a.currentWindow) a.closeWindow(a.currentWindow);

  fs.writeFileSync(HC + 'config.yml', backups['config.yml']);
  await reload();
  await consoleOut('hca cycle end confirm', 5000);   // back to 27-slot vaults
  u2.quit(); a.quit();
  await sleep(800);
  process.exit(L.summary() ? 1 : 0);
})().catch((e) => { console.log('HARNESS ERROR', e); process.exit(2); });
