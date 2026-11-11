// Gaps left by the other suites: full join hook order with a non-op player, permissions, drag / offhand / double
// click in the vault, old-cycle heads refused, piston and water next to placed heads, audit rotation on a live server.
const L = require('./lib');
const WORK = L.S;
const fs = require('fs');
const { Vec3 } = require('vec3');
const { sleep, record, serverConsole, logSize, logSince, db, waitFor, makeBot, waitChat, openWindow, rawClick } = L;
const S = `${WORK}`;
const HC = S + '/botserver/plugins/Headcount/';
const AUDIT = HC + 'audit/';
const auditPlain = () => fs.readdirSync(AUDIT).filter((f) => f.endsWith('.log')).map((f) => fs.readFileSync(AUDIT + f, 'utf8')).join('');
const isEcon = (s) => s && s.name === 'player_head' && JSON.stringify(s.components || s.nbt || '').includes('head_id');
const consoleOut = async (cmd, wait = 900) => { const m = logSize(); serverConsole(cmd); await sleep(wait); return logSince(m); };
const vaultIds = (name) => db(`SELECT head_id FROM vault_items v JOIN players p ON p.uuid=v.uuid WHERE p.name='${name}'`).map((r) => r[0]);
const countOn = async (player, item) => {
  const out = await consoleOut(`minecraft:clear ${player} ${item} 0`, 700);   // Essentials' /clear doesn't count
  const m = /Found (\d+) matching item/.exec(out);
  return m ? Number(m[1]) : 0;
};
const droppedItems = (bot) => Object.values(bot.entities).filter((e) => e.name === 'item').map((e) => e.getDroppedItem && e.getDroppedItem()).filter(Boolean);
const pvpOff = async (bot) => {
  const out = await consoleOut(`hca status ${bot.username} in-pvp`);
  if (/in-pvp: true/.test(out)) { bot.chat('/pvp'); await sleep(900); }
};

const N = process.argv[2] || 'Charlie';
(async () => {
  // --- every join hook, in order, for a brand-new player who is not an operator ------------------
  let cfg = fs.readFileSync(HC + 'config.yml', 'utf8');
  if (!cfg.includes('HOOK became-active')) {
    cfg = cfg.replace('    became-active: []\n    join: []', '    became-active:\n      - message: "HOOK became-active"\n    join:\n      - message: "HOOK join"');
    fs.writeFileSync(HC + 'config.yml', cfg);
  }
  await consoleOut('hca reload', 2500);
  const c = await makeBot(N);
  await waitFor(() => c.chatLog.some((m) => m.includes('HOOK join')), 8000);
  const idx = (re) => c.chatLog.findIndex((m) => re.test(m));
  const order = [idx(/Welcome to Headcount/), idx(/Cycle \d+ is running/), idx(/HOOK became-active/), idx(/HOOK join/)];
  record('hooks: first-join, first-join-cycle, became-active, join run in that order', order.every((i) => i >= 0) && order.every((v, i) => i === 0 || v > order[i - 1]),
    order.join(' < '));
  const ops = JSON.parse(fs.readFileSync(S + '/botserver/ops.json', 'utf8')).map((o) => o.name);
  let mc = c.chatLog.length;
  c.chat('/hc points');
  record('permissions: a non-op player can use /hc (headcount.player is default true)', !ops.includes(N) && await waitChat(c, /You have \d+ points/, mc, 2500));
  mc = c.chatLog.length;
  c.chat('/hca cycle info');
  record('permissions: a non-op player cannot use /hca (Paper hides it: "Unknown or incomplete command")', await waitChat(c, /don't have permission|Unknown or incomplete command/, mc, 2500));
  const tabs = (await c.tabComplete('/hc ').catch(() => [])).map((t) => (typeof t === 'string' ? t : t.match || t.text || ''));
  record('permissions: tab completion hides admin for a non-op player', tabs.includes('shop') && !tabs.includes('admin'), tabs.join(','));
  mc = c.chatLog.length;
  await pvpOff(c);
  const pvpNow = await consoleOut(`hca status ${N} in-pvp`);
  record('permissions: a non-op player can turn PvP off with /pvp (pvp.let-players-toggle)', /in-pvp: false/.test(pvpNow) && !c.chatLog.slice(mc).some((m) => /Unknown or incomplete/.test(m)),
    c.chatLog.slice(mc).join(' | '));

  // --- drag, offhand swap, double click in the vault ------------------------------------------------
  let w = await openWindow(c, '/hc');
  await c.clickWindow(0, 0, 1);            // two heads out to the inventory, so two vault slots are empty
  await sleep(700);
  await c.clickWindow(1, 0, 1);
  await sleep(700);
  const start = vaultIds(N).length;
  record('vault: two heads shift-clicked out', start === 25, `vault=${start}`);
  serverConsole(`give ${N} dirt 4`);
  await sleep(1000);
  w = c.currentWindow;
  const groundBefore = droppedItems(c).filter((d) => d.name === 'dirt').length;
  const dirt = w.slots.findIndex((s, i) => i >= w.inventoryStart && s && s.name === 'dirt');
  await c.clickWindow(dirt, 0, 0);         // dirt on the cursor
  await sleep(400);
  rawClick(c, w, -999, 0, 5);              // start a left drag
  rawClick(c, w, 0, 1, 5);                 // over empty vault slot 0
  rawClick(c, w, 1, 1, 5);                 // and empty vault slot 1
  rawClick(c, w, -999, 2, 5);              // end the drag
  await sleep(900);
  c.closeWindow(c.currentWindow);          // the cursor goes back to the inventory
  await sleep(900);
  const dirtAfter = await countOn(N, 'dirt');
  const groundDirt = droppedItems(c).filter((d) => d.name === 'dirt').length - groundBefore;
  record('vault: dragging dirt across empty vault slots is refused, nothing is lost or dropped', dirtAfter === 4 && groundDirt === 0 && vaultIds(N).length === 25,
    `dirt on player=${dirtAfter} on ground=${groundDirt} vault=${vaultIds(N).length}`);

  w = await openWindow(c, '/hc');
  const before = vaultIds(N).length;
  const target = w.slots.findIndex((s, i) => i < 27 && s);
  rawClick(c, w, target, 40, 2);           // F: swap that vault slot with the (empty) offhand
  await sleep(900);
  const afterF = vaultIds(N).length;
  record('vault: the offhand key (F) swaps a head to an empty offhand', afterF === before - 1, `vault ${before}->${afterF}`);
  rawClick(c, w, target, 40, 2);           // and back
  await sleep(900);
  record('vault: F swaps it back', vaultIds(N).length === before, `vault=${vaultIds(N).length}`);
  c.closeWindow(c.currentWindow);
  await sleep(400);
  serverConsole(`minecraft:item replace entity ${N} weapon.offhand with dirt 1`)   // Essentials' /item is player-only;
  await sleep(700);
  w = await openWindow(c, '/hc');
  mc = c.chatLog.length;
  const t2 = w.slots.findIndex((s, i) => i < 27 && s);
  rawClick(c, w, t2, 40, 2);               // F with dirt in the offhand
  await sleep(900);
  record('vault: F with dirt in the offhand is refused', vaultIds(N).length === before && await waitChat(c, /Only economy heads/, mc, 1500),
    `vault=${vaultIds(N).length}`);
  c.closeWindow(c.currentWindow);
  await sleep(400);
  serverConsole(`minecraft:item replace entity ${N} weapon.offhand with air`);
  await sleep(500);

  w = await openWindow(c, '/hc');
  const vaultBefore = vaultIds(N).length;
  const headsOnPlayer = await countOn(N, 'player_head');
  const invHead = w.slots.findIndex((s, i) => i >= w.inventoryStart && isEcon(s));
  await c.clickWindow(invHead, 0, 0);      // a head on the cursor
  await sleep(400);
  rawClick(c, w, invHead, 0, 6);           // double click: collect to cursor
  await sleep(500);
  rawClick(c, w, 5, 0, 6);                 // double click on a vault slot too
  await sleep(700);
  c.closeWindow(c.currentWindow);
  await sleep(900);
  const totalAfter = vaultIds(N).length + await countOn(N, 'player_head');
  record('vault: double click (collect to cursor) pulls nothing out of the vault; heads total unchanged', totalAfter === vaultBefore + headsOnPlayer,
    `vault+player ${vaultBefore + headsOnPlayer}->${totalAfter}`);
  const ids = vaultIds(N);
  record('vault: no head ID appears twice in the vault', new Set(ids).size === ids.length);

  // --- piston and water next to placed economy heads -------------------------------------------------
  const placeHead = async (offset) => {
    const head = c.inventory.items().find(isEcon);
    await c.equip(head, 'hand');
    await sleep(400);
    const below = c.blockAt(c.entity.position.offset(offset.x, -1, offset.z).floored());
    try { await c.placeBlock(below, new Vec3(0, 1, 0)); } catch (e) { /* the server decides */ }
    await sleep(1000);
    return below.position.offset(0, 1, 0);
  };
  const p1 = await placeHead(new Vec3(0, 0, 3));
  const placed1 = db(`SELECT head_id FROM placed_heads`);
  record('placed heads: head placed for the piston test', placed1.length === 1, JSON.stringify(placed1));
  serverConsole(`setblock ${p1.x - 1} ${p1.y} ${p1.z} piston[facing=east]`);
  await sleep(400);
  serverConsole(`setblock ${p1.x - 2} ${p1.y} ${p1.z} redstone_block`);
  await sleep(2000);
  const pist = auditPlain().match(new RegExp(`HEAD_BREAK head=${placed1.length ? placed1[0][0] : 'none'} .*?cause=(\\S+)`));
  const dropped1 = droppedItems(c).filter((d) => isEcon(d) && placed1.length && JSON.stringify(d.components).includes(placed1[0][0]));
  record('placed heads: a piston push drops the same economy head (HEAD_BREAK cause=piston)', !!pist && pist[1] === 'piston' && dropped1.length === 1,
    `${pist ? pist[1] : 'no HEAD_BREAK'} drops=${dropped1.length}`);
  record('placed heads: the index is empty after the piston', db(`SELECT COUNT(*) FROM placed_heads`)[0][0] === 0);
  serverConsole(`fill ${p1.x - 2} ${p1.y} ${p1.z} ${p1.x + 1} ${p1.y} ${p1.z} air`);
  await sleep(500);

  const p2 = await placeHead(new Vec3(3, 0, 0));
  const placed2 = db(`SELECT head_id FROM placed_heads`);
  serverConsole(`setblock ${p2.x} ${p2.y} ${p2.z + 1} water`);
  await sleep(3000);
  const stillThere = c.blockAt(p2) && /head|skull/.test(c.blockAt(p2).name);
  const indexed = db(`SELECT COUNT(*) FROM placed_heads`)[0][0];
  const has2 = (d) => isEcon(d) && placed2.length && JSON.stringify(d.components).includes(placed2[0][0]);
  const dropped2 = droppedItems(c).filter(has2).length;
  const pickedUp2 = c.inventory.items().filter(has2).length;   // the flow can wash the drop to the player
  const fluid = placed2.length && new RegExp(`HEAD_BREAK head=${placed2[0][0]} .*?cause=fluid`).test(auditPlain());
  record('placed heads: water next to a head either leaves it placed and indexed, or breaks it into the same economy head',
    placed2.length === 1 && (stillThere ? indexed === 1 : indexed === 0 && fluid && dropped2 + pickedUp2 === 1),
    `still placed=${stillThere} indexed=${indexed} cause=fluid:${fluid} on ground=${dropped2} picked up=${pickedUp2}`);
  serverConsole(`fill ${p2.x} ${p2.y} ${p2.z - 1} ${p2.x} ${p2.y} ${p2.z + 4} air`);
  await sleep(500);
  if (stillThere) {
    // break it by hand so the index is clean for the next steps
    try { await c.dig(c.blockAt(p2), true); } catch (e) { /* fine */ }
    await sleep(1200);
  }

  // --- an old-cycle head cannot go into the new cycle's vault ----------------------------------------
  w = await openWindow(c, '/hc');
  const outId = (() => { const vb = new Set(vaultIds(N)); return vb; })();
  await c.clickWindow(w.slots.findIndex((s, i) => i < 27 && s), 0, 1);
  await sleep(700);
  const oldId = [...outId].find((id) => !vaultIds(N).includes(id));
  c.closeWindow(c.currentWindow);
  await sleep(400);
  await consoleOut('hca cycle end confirm', 4000);
  w = await openWindow(c, '/hc');
  const refilled = vaultIds(N);
  record('cycle: an online player gets a fresh vault at the new cycle', refilled.length === 27 && !refilled.includes(oldId), `vault=${refilled.length}`);
  await c.clickWindow(0, 0, 1);            // make room
  await sleep(700);
  w = c.currentWindow;
  const oldSlot = w.slots.findIndex((s, i) => i >= w.inventoryStart && s && JSON.stringify(s.components || '').includes(oldId));
  mc = c.chatLog.length;
  if (oldSlot >= 0) await c.clickWindow(oldSlot, 0, 1);
  await sleep(800);
  record('vault: a head from the previous cycle is refused', oldSlot >= 0 && vaultIds(N).length === 26 && !vaultIds(N).includes(oldId) &&
    await waitChat(c, /from cycle \d+ and has expired/, mc, 1500), `slot=${oldSlot} vault=${vaultIds(N).length}`);
  c.closeWindow(c.currentWindow);
  await sleep(400);

  // --- audit rotation on the live server ----------------------------------------------------------------
  const firstHead = (/HEAD_CREATE head=([0-9a-f-]{36})/.exec(auditPlain()) || [])[1];
  const firstLine = (auditPlain().split('\n').find((l) => firstHead && l.includes(firstHead)) || '');
  cfg = fs.readFileSync(HC + 'config.yml', 'utf8');
  fs.writeFileSync(HC + 'config.yml', cfg.replace('max-file-size-mb: 10', 'max-file-size-mb: 0'));
  await consoleOut('hca reload', 2500);
  const gzBefore = fs.readdirSync(AUDIT).filter((f) => f.endsWith('.gz')).length;
  for (let i = 0; i < 12 && fs.readdirSync(AUDIT).filter((f) => f.endsWith('.gz')).length === gzBefore; i++) await consoleOut('hca cycle end confirm', 3500);
  const files = fs.readdirSync(AUDIT).sort();
  record('audit: the file rotates by size and the full file is gzipped', files.filter((f) => f.endsWith('.gz')).length > gzBefore && files.some((f) => /\.\d+\.log$/.test(f)), files.join(' '));
  const out = await consoleOut(`hca audit ${firstHead}`, 1500);
  const inPlain = !!firstHead && auditPlain().includes(firstHead);
  record('audit: /hca audit still finds a line that is now only inside a .gz file', !!firstHead && !inPlain && out.includes(firstHead) && !/Nothing in the audit log matches/.test(out),
    `${firstHead} ${firstLine.slice(0, 40)}`);
  fs.writeFileSync(HC + 'config.yml', fs.readFileSync(HC + 'config.yml', 'utf8').replace('max-file-size-mb: 0', 'max-file-size-mb: 10'));
  await consoleOut('hca reload', 2500);

  c.quit();
  await sleep(800);
  process.exit(L.summary() ? 1 : 0);
})().catch((e) => { console.log('HARNESS ERROR', e); process.exit(2); });
