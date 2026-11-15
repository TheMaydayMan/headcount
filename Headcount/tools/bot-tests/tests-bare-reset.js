// /hca reset hard and /hca reset cycle (1.1): permissions, the typed codes (wrong, expired, a set number of them),
// keep-heads and delete-heads, run IDs, defunct heads, deletion in inventories, containers, shulker boxes, bundles
// and on join, the backup folder, and what a cycle reset keeps. Runs on the bare server (setup-bare.sh), where
// every job is Headcount's own.
const L = require('./lib');
const fs = require('fs');
const { sleep, record, serverConsole, logSize, logSince, db, makeBot, waitChat, waitFor, openWindow } = L;
const HC = `${L.S}/botserver/plugins/Headcount/`;
const consoleOut = async (cmd, wait = 800) => { const m = logSize(); serverConsole(cmd); await sleep(wait); return logSince(m); };
const vaultCount = (name) => db(`SELECT COUNT(*) FROM vault_items v JOIN players p ON p.uuid=v.uuid WHERE p.name='${name}'`)[0][0];
const runToken = () => (db(`SELECT token FROM cycles WHERE state='active'`)[0] || [''])[0];
const cycleId = () => (db(`SELECT id FROM cycles WHERE state='active'`)[0] || [null])[0];
const headsIn = (bot) => bot.inventory.items().filter((i) => i.name === 'player_head');
const text = (item) => JSON.stringify(item ? item.components || item.nbt || '' : '');
const forgedNbt = (id, owner, cycle, token) => `{id:"minecraft:player_head",count:1,components:{"minecraft:custom_data":{PublicBukkitValues:{"headcount:head_id":"${id}","headcount:head_owner":"${owner}","headcount:head_cycle":${cycle},"headcount:head_token":"${token}"}}}}`;
const codeIn = (out) => { const m = /hca reset hard (\d{4})/.exec(out); return m ? m[1] : null; };

async function takeHeads(bot, n) {
  const w = await openWindow(bot, '/hc');
  if (!w) return 0;
  let took = 0;
  for (let s = 0; s < w.inventoryStart && took < n; s++) {
    if (w.slots[s] && w.slots[s].name === 'player_head') { await bot.clickWindow(s, 0, 1); await sleep(400); took++; }
  }
  bot.closeWindow(w);
  await sleep(500);
  return took;
}

async function openChest(bot, pos) {
  for (let i = 0; i < 3; i++) {
    for (let t = 0; t < 10 && !(bot.blockAt(pos) && bot.blockAt(pos).name === 'chest'); t++) await sleep(200);
    const p = new Promise((r) => bot.once('windowOpen', r));
    try { bot.activateBlock(bot.blockAt(pos)); } catch (e) { /* the window event decides */ }
    const w = await Promise.race([p, sleep(3000).then(() => null)]);
    if (w) { await sleep(800); return w; }
  }
  return null;
}

/** Runs a whole hard reset from the console, typing every code. Returns the console output. */
async function hardReset(mode, codes = 3) {
  let out = await consoleOut(`hca reset hard ${mode}`, 1000);
  let all = out;
  for (let i = 0; i < codes; i++) {
    const c = codeIn(out);
    if (!c) break;
    out = await consoleOut(`hca reset hard ${c}`, i === codes - 1 ? 3500 : 900);
    all += out;
  }
  return all;
}

(async () => {
  const cfg0 = fs.readFileSync(HC + 'config.yml', 'utf8');
  const H = await makeBot('Hostbot');
  const P = await makeBot('Plainbot');
  await sleep(3000);
  serverConsole('op Hostbot');
  serverConsole('minecraft:gamemode survival Hostbot');
  serverConsole('minecraft:gamemode survival Plainbot');
  await sleep(800);

  // --- permissions -------------------------------------------------------------------------------------
  let mark = P.chatLog.length;
  P.chat('/hca reset hard');
  // Paper hides /hca from players without any admin permission, so it reads as an unknown command
  record('hard: a player who isn\'t op can\'t use it', await waitChat(P, /permission|Unknown or incomplete command/i, mark, 2000), P.chatLog.slice(mark).join(' | ').slice(0, 120));
  mark = H.chatLog.length;
  H.chat('/hca reset hard');
  record('hard: an op is asked for the first code', await waitChat(H, /Confirmation 1\/3/, mark, 2000), H.chatLog.slice(mark).join(' | ').slice(0, 160));
  record('hard: the warning lists what will be deleted', H.chatLog.slice(mark).some((l) => /HARD RESET.*\d+ players.*\d+ cycles.*heads in vaults/.test(l)));
  // the code goes only to whoever asked
  record('hard: the code is not shown to other players', !P.chatLog.slice(mark).some((l) => /reset hard \d{4}/.test(l)));

  // --- wrong, expired, repeated ------------------------------------------------------------------------
  let out = await consoleOut('hca reset hard keep-heads');
  const c1 = codeIn(out);
  out = await consoleOut(`hca reset hard ${c1 === '0000' ? '0001' : '0000'}`);
  record('hard: a wrong code cancels it', /Wrong code, so the hard reset was cancelled/.test(out), out.trim().slice(-120));
  out = await consoleOut(`hca reset hard ${c1}`);
  record('hard: after a wrong code, the earlier right code doesn\'t work either', /Wrong code/.test(out) && !/Confirmation 2/.test(out));

  fs.writeFileSync(HC + 'config.yml', cfg0.replace(/^(\s*)confirm-timeout: .*$/m, '$1confirm-timeout: 5s'));
  await consoleOut('hca reload', 2500);
  out = await consoleOut('hca reset hard');
  record('hard: the step shows the configured timeout', /within 5s/.test(out), out.trim().slice(-100));
  const c2 = codeIn(out);
  await sleep(6000);
  out = await consoleOut(`hca reset hard ${c2}`);
  record('hard: a code typed after the timeout has expired', /That code expired/.test(out), out.trim().slice(-120));

  fs.writeFileSync(HC + 'config.yml', cfg0.replace(/^(\s*)confirmations: .*$/m, '$1confirmations: 2'));
  await consoleOut('hca reload', 2500);
  out = await consoleOut('hca reset hard');
  const first = /Confirmation 1\/2/.test(out);
  out = await consoleOut(`hca reset hard ${codeIn(out)}`);
  record('hard: with confirmations: 2 it asks twice, and the second is the last', first && /Last confirmation.*\(2\/2\)/.test(out), out.trim().slice(-120));
  serverConsole('hca reset hard 9999');   // drop it
  fs.writeFileSync(HC + 'config.yml', cfg0);
  await consoleOut('hca reload', 2500);

  // --- a real hard reset, keeping heads -------------------------------------------------------------------
  const tokenBefore = runToken();
  const took = await takeHeads(H, 3);
  record('setup: three heads taken out of the vault', took === 3 && headsIn(H).length === 3, `took ${took}`);
  const giveBefore = db("SELECT COUNT(*) FROM players")[0][0];
  mark = H.chatLog.length;
  const pmark = P.chatLog.length;
  out = await hardReset('keep-heads');
  record('hard keep-heads: the warning said old heads are kept as defunct heads', /Old heads are kept as defunct heads/.test(out));
  record('hard keep-heads: it finishes with a backup folder named', /Hard reset done\. Backup: backups\/hard-reset-\d{8}-\d{6}/.test(out), out.trim().slice(-160));
  await sleep(2500);
  record('hard keep-heads: every player is told', await waitChat(P, /An admin reset Headcount/, pmark, 2000));
  record('hard keep-heads: the first-join welcome runs again', await waitChat(H, /Welcome to Headcount/, mark, 3000));
  record('hard keep-heads: the starter shield is given again', await waitChat(H, /shield/i, mark, 2000), H.chatLog.slice(mark).join(' | ').slice(0, 200));
  record('hard keep-heads: a new vault with 18 heads', vaultCount('Hostbot') === 18 && vaultCount('Plainbot') === 18, `${vaultCount('Hostbot')}/${vaultCount('Plainbot')}`);
  record('hard keep-heads: it\'s cycle 1 with a new run ID', cycleId() === 1 && runToken() && runToken() !== tokenBefore, `cycle ${cycleId()} token ${runToken()} was ${tokenBefore}`);
  out = await consoleOut('hca cycle info');
  record('hard keep-heads: /hca cycle info shows cycle 1 and the run', /[Cc]ycle 1/.test(out) && out.includes(runToken().slice(0, 6)), out.trim().slice(0, 200));
  await sleep(1000);
  const inv = headsIn(H);
  record('hard keep-heads: the heads taken out now say (defunct)', inv.length === 3 && inv.every((i) => /defunct/i.test(text(i))), inv.map((i) => text(i).slice(0, 60)).join(' / '));
  record('hard keep-heads: their lore shows the cycle in gray', inv.length > 0 && /Defunct.*cycle.*gray/i.test(text(inv[0])) && !/gold/.test(text(inv[0])), text(inv[0]).slice(0, 300));
  let w = await openWindow(H, '/hc');
  mark = H.chatLog.length;
  const defunctSlot = w ? w.slots.findIndex((s, i) => i >= w.inventoryStart && s && s.name === 'player_head') : -1;
  if (defunctSlot >= 0) await H.clickWindow(defunctSlot, 0, 1);
  await sleep(900);
  record('hard keep-heads: the vault refuses a defunct head', vaultCount('Hostbot') === 18 && await waitChat(H, /no longer counts.*was reset/, mark, 1500), H.chatLog.slice(mark).join(' | ').slice(0, 120));
  if (w) H.closeWindow(w);
  await sleep(400);
  const backups = fs.readdirSync(HC + 'backups').filter((f) => f.startsWith('hard-reset-')).sort();
  const bdir = HC + 'backups/' + backups[backups.length - 1];
  record('hard keep-heads: the backup holds headcount.db and the audit folder', backups.length > 0 && fs.existsSync(bdir + '/headcount.db') && fs.existsSync(bdir + '/audit'), backups.join(','));
  const liveAudit = fs.readdirSync(HC + 'audit').map((f) => f.endsWith('.gz') ? '' : fs.readFileSync(HC + 'audit/' + f, 'utf8')).join('');
  record('hard keep-heads: the live audit log starts after the reset', /hard-reset-done/.test(liveAudit) && !/hard-reset-requested/.test(liveAudit), liveAudit.slice(0, 120));
  out = await consoleOut('hca status Hostbot newbie-protected');
  record('hard keep-heads: everyone is a new player again (newbie-protected)', /newbie-protected: true/.test(out), out.trim().slice(-80));
  record('hard keep-heads: players and points start over', db('SELECT COALESCE(SUM(points),0) FROM players')[0][0] === 0 && db('SELECT COUNT(*) FROM players')[0][0] <= giveBefore);
  const inspect = async () => { H.setQuickBarSlot(H.inventory.items().find((i) => i.name === 'player_head').slot - 36); await sleep(300); return consoleOut('execute as Hostbot run hca inspect', 900); };
  // hca inspect works on the held item; the console has no hand, so the bot runs it
  mark = H.chatLog.length;
  const hb = headsIn(H)[0];
  if (hb && hb.slot >= 36) { H.setQuickBarSlot(hb.slot - 36); await sleep(300); }
  H.chat('/hca inspect');
  record('hard keep-heads: /hca inspect calls the old head defunct', await waitChat(H, /defunct/i, mark, 2000), H.chatLog.slice(mark).join(' | ').slice(0, 200));
  void inspect;

  // --- hard reset, deleting heads, with one in a chest ------------------------------------------------------
  const keepDefunct = headsIn(H).length;   // defunct heads from the keep-heads reset stay through the next one
  await takeHeads(H, 2);
  const pos = H.entity.position.floored().offset(0, 0, -3);
  serverConsole(`setblock ${pos.x} ${pos.y} ${pos.z} chest`);
  await sleep(800);
  let cw = await openChest(H, pos);
  const fresh = cw ? cw.slots.map((s, i) => [s, i]).filter(([s, i]) => i >= cw.inventoryStart && s && s.name === 'player_head' && !/defunct/i.test(text(s))) : [];
  if (fresh.length) await H.clickWindow(fresh[0][1], 0, 1);
  await sleep(700);
  const inChest = cw ? cw.slots.slice(0, cw.inventoryStart).filter((s) => s && s.name === 'player_head').length : 0;
  if (cw) H.closeWindow(cw);
  await sleep(400);
  record('setup: one current head in the chest, one in the inventory', inChest === 1 && headsIn(H).filter((i) => !/defunct/i.test(text(i))).length === 1, `chest ${inChest}`);
  out = await hardReset('delete-heads');
  record('hard delete-heads: the warning said old heads are deleted', /Old heads are deleted wherever they turn up/.test(out));
  await sleep(2500);
  const left = headsIn(H);
  record('hard delete-heads: the current head in the inventory is gone', left.filter((i) => !/defunct/i.test(text(i))).length === 0, left.map((i) => text(i).slice(0, 50)).join(' / '));
  record('hard delete-heads: defunct heads from the earlier keep-heads reset stay', left.filter((i) => /defunct/i.test(text(i))).length === keepDefunct, `${left.length} left, ${keepDefunct} expected`);
  cw = await openChest(H, pos);
  await sleep(600);
  const chestNow = cw ? cw.slots.slice(0, cw.inventoryStart).filter((s) => s && s.name === 'player_head').length : -1;
  record('hard delete-heads: the head in the chest is gone when it\'s opened', chestNow === 0, `chest ${chestNow}`);
  if (cw) H.closeWindow(cw);
  out = await consoleOut('hca audit type:head_delete', 2500);
  record('hard delete-heads: /hca audit type:head_delete shows reason=reset', /reason=reset/.test(out), out.trim().slice(-200));

  // a defunct head thrown on the ground stays
  const d = headsIn(H)[0];
  if (d) { await H.tossStack(d); await sleep(2500); }
  const dropped = Object.values(H.entities).filter((e) => e.name === 'item').length;
  record('hard delete-heads: a defunct head from a keep-heads reset can be thrown and stays on the ground', !!d && dropped > 0, `items ${dropped}`);
  serverConsole('minecraft:kill @e[type=item]');

  // --- reset cycle, keep-heads ---------------------------------------------------------------------------------
  // some state that a cycle reset keeps, and some it clears
  serverConsole('hca points Hostbot give 40');
  serverConsole('hca title Hostbot grant champion');
  serverConsole('hca run Hostbot timed-test');
  await sleep(1500);
  const pts = db("SELECT points FROM players WHERE name='Hostbot'")[0][0];
  const effects = db("SELECT COUNT(*) FROM active_actions a JOIN players p ON p.uuid=a.uuid WHERE p.name='Hostbot'")[0][0];
  const hostUuid = db("SELECT uuid FROM players WHERE name='Hostbot'")[0][0];
  const plainUuid = db("SELECT uuid FROM players WHERE name='Plainbot'")[0][0];
  const cyc = cycleId();
  // a kill claim and a shop purchase this cycle, written as the plugin would
  serverConsole('hca shop'); // no-op for the console, keeps the log moving
  await takeHeads(H, 2);
  await takeHeads(P, 2);
  const tokenC = runToken();
  out = await consoleOut('hca reset cycle keep-heads');
  record('cycle keep-heads: without confirm it asks, and says old heads are kept as defunct heads', /restarts cycle \d+ from now.*kept as defunct heads/.test(out) && /reset cycle keep-heads confirm/.test(out), out.trim().slice(-200));
  mark = H.chatLog.length;
  out = await consoleOut('hca reset cycle keep-heads confirm', 3000);
  record('cycle keep-heads: done, with the new end date', /Cycle \d+ started over\. It now ends/.test(out), out.trim().slice(-120));
  record('cycle keep-heads: everyone is told', await waitChat(H, /An admin restarted cycle/, mark, 2000));
  record('cycle keep-heads: the first-join-cycle line runs again', await waitChat(H, /Cycle \d+ is running/, mark, 2500), H.chatLog.slice(mark).join(' | ').slice(0, 200));
  record('cycle keep-heads: same cycle number, new run ID', cycleId() === cyc && runToken() !== tokenC);
  const end = db(`SELECT end_ms, start_ms FROM cycles WHERE id=${cyc}`)[0];
  // it runs from now to the scheduled end-time (00:00) 14 days on, so a little under 14 days
  const days = (end[0] - end[1]) / 86400000;
  record('cycle keep-heads: it starts now and ends at the scheduled end-time 13-14 days on', days > 13 && days <= 14 && Math.abs(end[1] - Date.now()) < 120000
    && new Date(end[0]).getUTCHours() === 0 && new Date(end[0]).getUTCMinutes() === 0, `${days.toFixed(2)} days`);
  record('cycle keep-heads: vaults are refilled', vaultCount('Hostbot') === 18 && vaultCount('Plainbot') === 18);
  await sleep(800);
  record('cycle keep-heads: heads taken out before are defunct', headsIn(P).length === 2 && headsIn(P).every((i) => /defunct/i.test(text(i))), headsIn(P).map((i) => text(i).slice(0, 50)).join(' / '));
  record('cycle keep-heads: points, titles and effects are kept',
    db("SELECT points FROM players WHERE name='Hostbot'")[0][0] === pts && db(`SELECT COUNT(*) FROM titles WHERE uuid='${hostUuid}' AND title='champion'`)[0][0] === 1
    && db(`SELECT COUNT(*) FROM active_actions WHERE uuid='${hostUuid}'`)[0][0] === effects, `points ${pts} effects ${effects}`);
  record('cycle keep-heads: kill claims and shop stock for the cycle are cleared',
    db(`SELECT COUNT(*) FROM kill_drops WHERE cycle=${cyc}`)[0][0] === 0 && db(`SELECT COUNT(*) FROM shop_stock WHERE cycle=${cyc}`)[0][0] === 0);
  void plainUuid;

  // --- reset cycle, delete-heads: inventory at once, containers (with shulker and bundle) when opened, offline on join
  await takeHeads(H, 1);
  await takeHeads(P, 1);
  const tok = runToken();
  const owner = hostUuid;
  const ids = ['aaaaaaaa-1111-4111-8111-111111111111', 'bbbbbbbb-2222-4222-8222-222222222222', 'cccccccc-3333-4333-8333-333333333333'];
  const pos2 = pos.offset(2, 0, 0);
  serverConsole(`setblock ${pos2.x} ${pos2.y} ${pos2.z} chest`);
  await sleep(400);
  serverConsole(`minecraft:item replace block ${pos2.x} ${pos2.y} ${pos2.z} container.0 with player_head[custom_data={PublicBukkitValues:{"headcount:head_id":"${ids[0]}","headcount:head_owner":"${owner}","headcount:head_cycle":${cyc},"headcount:head_token":"${tok}"}}]`);
  serverConsole(`minecraft:item replace block ${pos2.x} ${pos2.y} ${pos2.z} container.1 with shulker_box[container=[{slot:0,item:${forgedNbt(ids[1], owner, cyc, tok)}}]]`);
  serverConsole(`minecraft:item replace block ${pos2.x} ${pos2.y} ${pos2.z} container.2 with bundle[bundle_contents=[${forgedNbt(ids[2], owner, cyc, tok)}]]`);
  await sleep(600);
  P.quit();
  await sleep(1500);
  out = await consoleOut('hca reset cycle delete-heads confirm', 3000);
  await sleep(1500);
  record('cycle delete-heads: heads in an online player\'s inventory vanish at once', headsIn(H).filter((i) => !/defunct/i.test(text(i))).length === 0, headsIn(H).map((i) => text(i).slice(0, 40)).join(' / '));
  cw = await openChest(H, pos2);
  await sleep(800);
  const s0 = cw && cw.slots[0] ? cw.slots[0].name : 'none';
  const s1 = cw && cw.slots[1] ? text(cw.slots[1]) : '';
  const s2 = cw && cw.slots[2] ? text(cw.slots[2]) : '';
  record('cycle delete-heads: a head in a chest is removed when the chest is opened', s0 !== 'player_head', s0);
  record('cycle delete-heads: a head inside a shulker box in the chest is removed', !s1.includes(ids[1]), s1.slice(0, 120));
  record('cycle delete-heads: a head inside a bundle in the chest is removed', !s2.includes(ids[2]), s2.slice(0, 120));
  if (cw) H.closeWindow(cw);
  const P2 = await makeBot('Plainbot');
  await sleep(3000);
  record('cycle delete-heads: a player who was offline loses theirs when they join', headsIn(P2).filter((i) => !/defunct/i.test(text(i))).length === 0, headsIn(P2).map((i) => text(i).slice(0, 40)).join(' / '));
  out = await consoleOut('hca audit type:head_delete', 2500);
  const lines = out.split('\n').filter((l) => /reason=reset/.test(l));
  record('cycle delete-heads: each deletion is audited with reason=reset and where it was', lines.length >= 3 && lines.some((l) => /container|chest/i.test(l)) && lines.some((l) => /inventory|join/i.test(l)), lines.slice(0, 4).join(' || ').slice(0, 300));

  serverConsole(`setblock ${pos.x} ${pos.y} ${pos.z} air`);
  serverConsole(`setblock ${pos2.x} ${pos2.y} ${pos2.z} air`);
  serverConsole('hca effects Hostbot remove all');
  fs.writeFileSync(HC + 'config.yml', cfg0);
  await sleep(500);
  H.quit(); P2.quit();
  await sleep(1000);
  process.exit(L.summary() ? 1 : 0);
})().catch((e) => { console.log('HARNESS ERROR', e.stack); L.summary(); process.exit(2); });
