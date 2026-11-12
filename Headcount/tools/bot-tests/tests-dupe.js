// Forged copies of real head IDs (made with vanilla /give, the way a dupe glitch would make them) and the lazy
// "expired" refresh of heads in a chest, a shulker box inside it and a bundle inside it.
const L = require('./lib');
const WORK = L.S;
const fs = require('fs');
const { sleep, record, serverConsole, logSize, logSince, db, waitFor, makeBot, waitChat, openWindow } = L;
const S = `${WORK}`;
const AUDIT = S + '/botserver/plugins/Headcount/audit/';
const auditPlain = () => fs.readdirSync(AUDIT).filter((f) => f.endsWith('.log')).map((f) => fs.readFileSync(AUDIT + f, 'utf8')).join('');
const consoleOut = async (cmd, wait = 900) => { const m = logSize(); serverConsole(cmd); await sleep(wait); return logSince(m); };
const isEcon = (s) => s && s.name === 'player_head' && JSON.stringify(s.components || s.nbt || '').includes('head_id');
const N = process.argv[2] || 'Golf';
const vaultIds = () => db(`SELECT head_id FROM vault_items v JOIN players p ON p.uuid=v.uuid WHERE p.name='${N}'`).map((r) => r[0]);
// Heads carry their cycle run's token since 1.1; a copy of a current head copies it too.
let runToken = '';
const forged = (id, owner, cycle) => `player_head[custom_data={PublicBukkitValues:{"headcount:head_id":"${id}","headcount:head_owner":"${owner}","headcount:head_cycle":${cycle}${runToken ? `,"headcount:head_token":"${runToken}"` : ''}}}]`;
const forgedNbt = (id, owner, cycle) => `{id:"minecraft:player_head",count:1,components:{"minecraft:custom_data":{PublicBukkitValues:{"headcount:head_id":"${id}","headcount:head_owner":"${owner}","headcount:head_cycle":${cycle}}}}}`;
const slotsWith = (w, id) => w.slots.map((s, i) => [s, i]).filter(([s, i]) => i >= w.inventoryStart && s && JSON.stringify(s.components || '').includes(id)).map(([, i]) => i);

(async () => {
  const admin = await makeBot('Alpha');   // operator: gets the duplicate alerts
  const g = await makeBot(N);
  await sleep(2500);
  const st = await consoleOut(`hca status ${N} in-pvp`);
  if (/in-pvp: true/.test(st)) { g.chat('/pvp'); await sleep(900); }
  const uuid = db(`SELECT uuid FROM players WHERE name='${N}'`)[0][0];
  const cycle = db(`SELECT id FROM cycles WHERE state='active'`)[0][0];
  runToken = db(`SELECT token FROM cycles WHERE state='active'`)[0][0] || '';

  // --- a forged copy of a head that is in the vault ---------------------------------------------------
  let w = await openWindow(g, '/hc');
  const full = vaultIds();
  await g.clickWindow(w.slots.findIndex((s, i) => i < 27 && s), 0, 1);   // one real head out (X), so the vault has a free slot
  await sleep(800);
  const inVault = vaultIds();
  const x = full.find((id) => !inVault.includes(id));
  g.closeWindow(g.currentWindow);
  await sleep(600);
  const y = inVault[0];
  let ma = admin.chatLog.length;
  let mark = auditPlain().length;
  serverConsole(`minecraft:give ${N} ${forged(y, uuid, cycle)}`);
  await sleep(800);
  w = await openWindow(g, '/hc');          // closing any inventory runs the duplicate scan
  g.closeWindow(g.currentWindow);
  await sleep(1200);
  let newAudit = auditPlain().slice(mark);
  record('duplicates: a forged copy of a vaulted head ID is found by the scan on inventory close',
    new RegExp(`DUPLICATE kind=inventory head=${y} .*in-vault-of=${uuid}.*trigger=inventory-close`).test(newAudit), (newAudit.match(/DUPLICATE[^\n]*/) || ['none'])[0].slice(0, 160));
  record('duplicates: online admins get the alert', await waitChat(admin, new RegExp(`Duplicate head \\(inventory\\): ${y}`), ma, 2000));

  w = await openWindow(g, '/hc');
  const fy = slotsWith(w, y);
  let mg = g.chatLog.length;
  mark = auditPlain().length;
  if (fy.length) await g.clickWindow(fy[0], 0, 1);   // try to put the forged copy into the vault
  await sleep(900);
  newAudit = auditPlain().slice(mark);
  record('duplicates: the forged copy cannot go into the vault (an ID already in a vault is rejected)',
    fy.length === 1 && vaultIds().length === inVault.length && vaultIds().filter((id) => id === y).length === 1 &&
    await waitChat(g, /That head is a copy/, mg, 1500) && /DUPLICATE kind=already-in-vault-of/.test(newAudit), `forged slots=${fy.length} vault=${vaultIds().length}`);

  // --- a forged copy of a head in the inventory: two copies, the first may go in, the second not -----------
  serverConsole(`minecraft:give ${N} ${forged(x, uuid, cycle)}`);
  await sleep(800);
  g.closeWindow(g.currentWindow);
  await sleep(300);
  mark = auditPlain().length;
  w = await openWindow(g, '/hc');
  g.closeWindow(g.currentWindow);
  await sleep(1200);
  newAudit = auditPlain().slice(mark);
  record('duplicates: two copies of one ID in an inventory are reported (copies=2)', new RegExp(`DUPLICATE kind=inventory head=${x} .*copies=2`).test(newAudit),
    (newAudit.match(/DUPLICATE[^\n]*/) || ['none'])[0].slice(0, 160));
  w = await openWindow(g, '/hc');
  let fx = slotsWith(w, x);
  await g.clickWindow(fx[0], 0, 1);        // the first copy goes in
  await sleep(900);
  const afterFirst = vaultIds().length;
  await g.clickWindow(1, 0, 1);            // make room again (a different head comes out)
  await sleep(900);
  w = g.currentWindow;
  fx = slotsWith(w, x);
  mg = g.chatLog.length;
  if (fx.length) await g.clickWindow(fx[0], 0, 1);
  await sleep(900);
  record('duplicates: the first copy of X goes in, the second is rejected', afterFirst === inVault.length + 1 && fx.length === 1 &&
    vaultIds().filter((id) => id === x).length === 1 && await waitChat(g, /That head is a copy/, mg, 1500), `after first=${afterFirst} remaining copies=${fx.length}`);
  g.closeWindow(g.currentWindow);
  await sleep(400);

  // --- the expired refresh in containers ----------------------------------------------------------------
  const pos = g.entity.position.floored().offset(0, 0, -3);
  const old = cycle - 1;
  const ids = ['11111111-1111-4111-8111-111111111111', '22222222-2222-4222-8222-222222222222', '33333333-3333-4333-8333-333333333333'];
  serverConsole(`setblock ${pos.x} ${pos.y} ${pos.z} chest`);
  await sleep(400);
  serverConsole(`minecraft:item replace block ${pos.x} ${pos.y} ${pos.z} container.0 with ${forged(ids[0], uuid, old)}`);
  serverConsole(`minecraft:item replace block ${pos.x} ${pos.y} ${pos.z} container.1 with shulker_box[container=[{slot:0,item:${forgedNbt(ids[1], uuid, old)}}]]`);
  serverConsole(`minecraft:item replace block ${pos.x} ${pos.y} ${pos.z} container.2 with bundle[bundle_contents=[${forgedNbt(ids[2], uuid, old)}]]`);
  await sleep(800);
  const chest = g.blockAt(pos);
  const p = new Promise((r) => g.once('windowOpen', r));
  try { g.activateBlock(chest); } catch (e) { /* the window event decides */ }
  const cw = await Promise.race([p, sleep(4000).then(() => null)]);
  await sleep(1000);
  const show = (i) => (cw && cw.slots[i] ? JSON.stringify(cw.slots[i].components || '') : '');
  record('expiry: an old-cycle head in a chest shows as expired when the chest is opened', /expired/i.test(show(0)), show(0).slice(0, 100));
  record('expiry: an old-cycle head inside a shulker box in the chest is refreshed too', /expired/i.test(show(1)), show(1).slice(0, 100));
  record('expiry: an old-cycle head inside a bundle in the chest is refreshed too', /expired/i.test(show(2)), show(2).slice(0, 100));
  if (g.currentWindow) g.closeWindow(g.currentWindow);
  await sleep(300);
  serverConsole(`setblock ${pos.x} ${pos.y} ${pos.z} air`);

  admin.quit();
  g.quit();
  await sleep(800);
  process.exit(L.summary() ? 1 : 0);
})().catch((e) => { console.log('HARNESS ERROR', e); process.exit(2); });
