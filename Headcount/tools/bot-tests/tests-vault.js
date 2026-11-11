const L = require('./lib');
const WORK = L.S;
const { sleep, record, serverConsole, logSize, logSince, db, waitFor, makeBot, waitChat, openWindow, rawClick } = L;

const vaultCount = (name) => db(`SELECT COUNT(*) FROM vault_items v JOIN players p ON p.uuid=v.uuid WHERE p.name='${name}'`)[0][0];
const status = async (player, st) => {
  const mark = logSize();
  serverConsole(`hca status ${player} ${st}`);
  await sleep(700);
  const m = /: (\S+)\s*$/m.exec(logSince(mark).split('\n').filter((l) => l.includes(st)).join('\n'));
  return m ? m[1] : null;
};

(async () => {
  const a = await L.makeBot('Alpha');
  // --- join flow -------------------------------------------------------
  const okJoin = await L.waitFor(() => a.chatLog.some((m) => m.includes('Cycle 1 is running')), 6000);
  const iw = a.chatLog.findIndex((m) => m.includes('Welcome to Headcount'));
  const ic = a.chatLog.findIndex((m) => m.includes('Cycle 1 is running'));
  record('join: first-join welcome then first-join-cycle message, in that order', okJoin && iw >= 0 && iw < ic, `welcome@${iw} cycle@${ic}`);
  record('join: vault set up with 18 own heads', vaultCount('Alpha') === 18, `count=${vaultCount('Alpha')}`);
  serverConsole('op Alpha');
  await sleep(500);

  // --- PvP gate ----------------------------------------------------------
  let pvp = await status('Alpha', 'in-pvp');
  if (pvp === 'false') { a.chat('/pvp'); await sleep(800); pvp = await status('Alpha', 'in-pvp'); }
  record('status: in-pvp follows PvPManager (on by default)', pvp === 'true', `in-pvp=${pvp}`);
  let w = await openWindow(a, '/hc');
  record('vault: /hc opens a 27-slot vault holding 18 heads', !!w && w.slots.slice(0, 27).filter((s) => s && s.name === 'player_head').length === 18,
    w ? `type=${w.type} heads=${w.slots.slice(0, 27).filter((s) => s && s.name === 'player_head').length}` : 'no window');
  let mark = a.chatLog.length;
  await a.clickWindow(0, 0, 0);
  await sleep(700);
  record('gate: with PvP on, taking a head out is refused with the in-pvp message', vaultCount('Alpha') === 18 && await waitChat(a, /PvP is on/, mark, 1500),
    `count=${vaultCount('Alpha')}`);
  if (a.currentWindow) a.closeWindow(a.currentWindow);
  await sleep(300);
  a.chat('/pvp');
  await sleep(1000);
  pvp = await status('Alpha', 'in-pvp');
  record('status: /pvp turns in-pvp off', pvp === 'false', `in-pvp=${pvp}`);

  // --- vault moves -------------------------------------------------------
  w = await openWindow(a, '/hc');
  const inv0 = w.inventoryStart;
  await a.clickWindow(0, 0, 0);          // pick up
  await sleep(300);
  await a.clickWindow(inv0, 0, 0);       // place in first inventory slot
  await sleep(800);
  record('vault: pick up and place into inventory moves a head out', vaultCount('Alpha') === 17, `count=${vaultCount('Alpha')}`);
  await a.clickWindow(inv0, 0, 1);       // shift-click back
  await sleep(800);
  record('vault: shift-click from inventory puts it back', vaultCount('Alpha') === 18, `count=${vaultCount('Alpha')}`);
  const freeKey = w.slots.slice(w.hotbarStart, w.hotbarStart + 9).findIndex((s) => !s);
  await a.clickWindow(2, freeKey, 2);    // number key on vault slot 2 -> an empty hotbar slot
  await sleep(800);
  record('vault: number key swaps a head to the hotbar', vaultCount('Alpha') === 17, `key=${freeKey + 1} count=${vaultCount('Alpha')}`);
  await a.clickWindow(2, freeKey, 2);    // and back
  await sleep(800);
  record('vault: number key swaps it back', vaultCount('Alpha') === 18, `count=${vaultCount('Alpha')}`);
  const entitiesBefore = Object.values(a.entities).filter((e) => e.name === 'item').length;
  rawClick(a, w, 3, 0, 4);               // Q on a vault slot
  rawClick(a, w, 3, 1, 4);               // Ctrl+Q
  await sleep(1000);
  const entitiesAfter = Object.values(a.entities).filter((e) => e.name === 'item').length;
  record('vault: drop keys (Q, Ctrl+Q) on a vault slot are refused', vaultCount('Alpha') === 18 && entitiesAfter === entitiesBefore,
    `count=${vaultCount('Alpha')} items ${entitiesBefore}->${entitiesAfter}`);
  rawClick(a, w, 4, 2, 3);               // middle click (clone) on a vault slot
  await sleep(600);
  record('vault: middle click (clone) changes nothing', vaultCount('Alpha') === 18, `count=${vaultCount('Alpha')}`);

  // --- what may not go in ------------------------------------------------
  await a.clickWindow(0, 0, 1);          // shift-click a head out to free slot 0
  await sleep(800);
  serverConsole('give Alpha dirt 4');
  serverConsole('give Alpha player_head 1');
  await sleep(1200);
  w = a.currentWindow;
  const findInv = (name) => w.slots.findIndex((s, i) => i >= w.inventoryStart && s && s.name === name && !(s.customName || '').includes("Alpha's Head"));
  const dirtSlot = findInv('dirt');
  mark = a.chatLog.length;
  await a.clickWindow(dirtSlot, 0, 0);   // pick up dirt
  await sleep(300);
  await a.clickWindow(0, 0, 0);          // try to put it in vault slot 0
  await sleep(800);
  record('vault: dirt cannot be placed in a vault slot', vaultCount('Alpha') === 17 && await waitChat(a, /Only economy heads/, mark, 1500),
    `count=${vaultCount('Alpha')}`);
  await a.clickWindow(dirtSlot, 0, 0);   // put dirt back
  await sleep(500);
  mark = a.chatLog.length;
  await a.clickWindow(dirtSlot, 0, 1);   // shift-click dirt
  await sleep(800);
  record('vault: dirt cannot be shift-clicked into the vault', vaultCount('Alpha') === 17, `count=${vaultCount('Alpha')}`);
  w = a.currentWindow;
  const vanilla = w.slots.findIndex((s, i) => i >= w.inventoryStart && s && s.name === 'player_head' && !s.customName && !(JSON.stringify(s.components || s.nbt || '')).includes('head_id'));
  mark = a.chatLog.length;
  if (vanilla >= 0) await a.clickWindow(vanilla, 0, 1);
  await sleep(800);
  record('vault: a vanilla /give player head is refused', vanilla >= 0 && vaultCount('Alpha') === 17 && await waitChat(a, /Only economy heads/, mark, 1500),
    `slot=${vanilla} count=${vaultCount('Alpha')}`);

  // --- put the head back, check the audit trail --------------------------
  w = a.currentWindow;
  const own = w.slots.findIndex((s, i) => i >= w.inventoryStart && s && s.name === 'player_head' && JSON.stringify(s.components || s.nbt || '').includes('head_id'));
  if (own >= 0) await a.clickWindow(own, 0, 1);
  await sleep(800);
  record('vault: the economy head goes back in (18 again)', vaultCount('Alpha') === 18, `slot=${own} count=${vaultCount('Alpha')}`);
  a.closeWindow(a.currentWindow);
  await sleep(500);
  const audit = require('fs').readdirSync(L.db.length ? `${WORK}/botserver/plugins/Headcount/audit` : '.');
  const auditText = audit.map((f) => require('fs').readFileSync(`${WORK}/botserver/plugins/Headcount/audit/` + f, 'utf8')).join('');
  record('audit: vault moves are logged (VAULT_OUT and VAULT_IN)', /VAULT_OUT/.test(auditText) && /VAULT_IN/.test(auditText));

  a.quit();
  await sleep(500);
  process.exit(L.summary() ? 1 : 0);
})().catch((e) => { console.log('HARNESS ERROR', e); process.exit(2); });
