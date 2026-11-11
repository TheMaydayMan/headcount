const L = require('./lib');
const WORK = L.S;
const fs = require('fs');
const { Vec3 } = require('vec3');
const { sleep, record, serverConsole, logSize, logSince, db, waitFor, makeBot, waitChat, openWindow } = L;
const AUDIT = `${WORK}/botserver/plugins/Headcount/audit/`;
const audit = () => fs.readdirSync(AUDIT).map((f) => fs.readFileSync(AUDIT + f, 'utf8')).join('');
const isEcon = (s) => s && s.name === 'player_head' && JSON.stringify(s.components || s.nbt || '').includes('head_id');
const consoleOut = async (cmd, wait = 900) => { const m = logSize(); serverConsole(cmd); await sleep(wait); return logSince(m); };
const vaultCount = (name) => db(`SELECT COUNT(*) FROM vault_items v JOIN players p ON p.uuid=v.uuid WHERE p.name='${name}'`)[0][0];
const pvpOn = async (bot, want) => {
  const out = await consoleOut(`hca status ${bot.username} in-pvp`);
  if (/in-pvp: true/.test(out) !== want) { bot.chat('/pvp'); await sleep(900); }
};
const startTrade = async (a, b) => {
  const mb = b.chatLog.length;
  a.chat('/trade ' + b.username);
  await waitChat(b, /wants to trade/, mb, 3000);
  const pa = new Promise((r) => a.once('windowOpen', r));
  const pb = new Promise((r) => b.once('windowOpen', r));
  b.chat('/trade accept');
  return Promise.race([Promise.all([pa, pb]), sleep(4000).then(() => [null, null])]);
};

(async () => {
  let a = await makeBot('Alpha');
  let b = await makeBot('Bravo');
  await sleep(2500);
  await pvpOn(a, false);
  await pvpOn(b, false);

  // --- PlaceholderAPI passthrough in Headcount's own text ---------------------------
  let ma = a.chatLog.length;
  serverConsole('hca run Alpha passthrough');
  await waitChat(a, /^PT /m, ma, 2000);
  const pt = a.chatLog.slice(ma).find((m) => m.startsWith('PT ')) || '';
  record("text: another plugin's placeholder (%pvpmanager_pvp_status%) is resolved inside Headcount text", /^PT pvp=\S+ hc=\d+$/.test(pt) && !pt.includes('%'), pt);

  // --- player commands ----------------------------------------------------------------
  const cmd = async (c, re) => { const m = a.chatLog.length; a.chat(c); return waitChat(a, re, m, 2500); };
  record('/hc points answers with the balance', await cmd('/hc points', /You have \d+ points/));
  record('/hc score answers with a preview', await cmd('/hc score', /If the cycle ended now: score/));
  record('/hc top lists the leaderboard', await cmd('/hc top', /Top players, cycle/));
  record('/hc effects answers', await cmd('/hc effects', /(active effects|Active effects)/));
  record('/hc help lists the commands', await cmd('/hc help', /Headcount commands/));
  let w = await openWindow(a, '/hc history');
  record('/hc history opens the history menu', !!w && w.slots.slice(0, 45).some((s) => s && /Cycle \d/.test(JSON.stringify(s.customName || '') + JSON.stringify(s.components || ''))),
    w ? w.type : 'no window');
  if (a.currentWindow) a.closeWindow(a.currentWindow);
  const tabs = await a.tabComplete('/hc ').catch(() => []);
  const tabNames = tabs.map((t) => (typeof t === 'string' ? t : t.match || t.text || ''));
  record('tab completion: /hc subcommands', ['shop', 'titles', 'history', 'sidebar'].every((s) => tabNames.includes(s)), tabNames.join(','));
  const ttabs = (await a.tabComplete('/trade ').catch(() => [])).map((t) => (typeof t === 'string' ? t : t.match || t.text || ''));
  record('tab completion: /trade offers online players', ttabs.includes('Bravo') && ttabs.includes('accept'), ttabs.join(','));

  // --- trade request rules -----------------------------------------------------------------
  record('trade: trading yourself is refused', await cmd('/trade Alpha', /can't trade with yourself/));
  record('trade: an offline name is refused', await cmd('/trade Nobody', /is not online/));
  let mb = b.chatLog.length;
  a.chat('/trade Bravo');
  await waitChat(b, /wants to trade/, mb, 2500);
  mb = b.chatLog.length;
  b.chat('/trade Alpha');
  record('trade: one request at a time (the target already has one)', await waitChat(b, /already have a trade or request open/, mb, 2500));
  record('trade: the sender can withdraw it with /trade cancel', await cmd('/trade cancel', /request cancelled/i));
  await sleep(500);
  let [wa, wb] = await startTrade(a, b);
  mb = b.chatLog.length;
  serverConsole('tp Alpha ~ ~ ~5');
  record('trade: a teleport cancels the open trade', !!wa && await waitChat(b, /teleported/, mb, 3000));
  await sleep(800);
  [wa, wb] = await startTrade(a, b);
  mb = b.chatLog.length;
  a.chat('/pvp');                           // Alpha turns PvP on during the trade
  record('trade: getting a cancel status (in-pvp) cancels the open trade', !!wa && await waitChat(b, /in-pvp/, mb, 3000));
  await sleep(800);
  await pvpOn(a, false);

  // --- confirmation menu -------------------------------------------------------------------
  await consoleOut('hca points Alpha give 50');
  w = await openWindow(a, '/hc shop common');   // Alpha's last score may open a higher tier
  let p = new Promise((r) => a.once('windowOpen', r));
  await a.clickWindow(10, 0, 0);           // 16 Steak, price 1 (confirm-at-price set to 1 on this server)
  let cw = await Promise.race([p, sleep(3000).then(() => null)]);
  record('shop: a purchase at or above confirm-at-price opens a confirmation menu', !!cw && cw.type === 'minecraft:generic_9x3', cw ? cw.type : 'none');
  const pointsBefore = db(`SELECT points FROM players WHERE name='Alpha'`)[0][0];
  await sleep(500);                        // gui.click-cooldown (150ms) drops clicks faster than a person makes them
  p = new Promise((r) => a.once('windowOpen', r));
  await a.clickWindow(14, 0, 0);           // cancel
  await Promise.race([p, sleep(3000)]);
  await sleep(1000);
  const ptsC = db(`SELECT points FROM players WHERE name='Alpha'`)[0][0];
  record('shop: cancelling the confirmation buys nothing and reopens the shop', ptsC === pointsBefore && !!a.currentWindow && a.currentWindow.type === 'minecraft:generic_9x6',
    `points ${pointsBefore}->${ptsC} window=${a.currentWindow ? a.currentWindow.type : 'none'} chat=${a.chatLog.slice(-3).join(' | ')}`);
  p = new Promise((r) => a.once('windowOpen', r));
  await a.clickWindow(10, 0, 0);
  await Promise.race([p, sleep(3000)]);
  await sleep(800);
  ma = a.chatLog.length;
  await a.clickWindow(10, 0, 0);           // confirm
  await sleep(1200);
  const bought = await waitChat(a, /Bought 16 Steak/, ma, 2000);
  const ptsB = db(`SELECT points FROM players WHERE name='Alpha'`)[0][0];
  record('shop: confirming buys it once', bought && ptsB === pointsBefore - 1, `bought=${bought} points ${pointsBefore}->${ptsB} chat=${a.chatLog.slice(ma).join(' | ')}`);
  if (a.currentWindow) a.closeWindow(a.currentWindow);
  await sleep(500);

  // --- admin live vault view -------------------------------------------------------------
  w = await openWindow(a, '/hc');
  const pv = new Promise((r) => b.once('windowOpen', r));
  b.chat('/hca vault Alpha');
  const bw = await Promise.race([pv, sleep(3000).then(() => null)]);
  await sleep(500);
  const before = vaultCount('Alpha');
  if (bw) await b.clickWindow(0, 0, 1);    // admin shift-clicks Alpha's head out
  await sleep(1000);
  record('admin: /hca vault edits are live in the owner\'s open vault', !!bw && vaultCount('Alpha') === before - 1 && !w.slots[0],
    `count ${before}->${vaultCount('Alpha')} owner slot0=${w.slots[0] ? w.slots[0].name : 'empty'}`);
  record('audit: admin vault edits are logged as VAULT_ADMIN', /VAULT_ADMIN/.test(audit()));
  if (b.currentWindow) b.closeWindow(b.currentWindow);
  await sleep(300);

  // --- placed heads --------------------------------------------------------------------------
  const firstHead = w.slots.findIndex((s, i) => i < 27 && s);
  await a.clickWindow(firstHead, 0, 1);    // take a head out
  await sleep(800);
  a.closeWindow(a.currentWindow);
  await sleep(400);
  await a.equip(a.inventory.items().find(isEcon), 'hand');
  await sleep(400);
  const headInHand = a.heldItem;
  record('placed heads: holding an economy head', isEcon(headInHand), headInHand ? headInHand.name : 'nothing');
  const below = a.blockAt(a.entity.position.offset(2, -1, 0).floored());
  try {
    await a.placeBlock(below, new Vec3(0, 1, 0));
  } catch (e) { /* mineflayer may time out waiting for its own physics; the server decides */ }
  await sleep(1000);
  const placedAt = below.position.offset(0, 1, 0);
  const placed = db(`SELECT head_id FROM placed_heads`);
  record('placed heads: placing an economy head stores it in the index', placed.length === 1, JSON.stringify(placed));
  const headBlock = a.blockAt(placedAt);
  try { await a.dig(headBlock, true); } catch (e) { console.log('dig:', e.message); }
  await sleep(1500);
  const brk = placed.length ? audit().match(new RegExp(`HEAD_BREAK head=(${placed[0][0]}) .*?cause=break:Alpha`)) : null;
  record('placed heads: breaking it drops the same economy head (HEAD_BREAK with the stored ID)', !!brk && placed.length === 1 && brk[1] === placed[0][0],
    brk ? brk[1] : 'no HEAD_BREAK');
  const dropped = Object.values(a.entities).filter((e) => e.name === 'item').map((e) => e.getDroppedItem && e.getDroppedItem()).filter(Boolean);
  const hasId = (d) => isEcon(d) && placed.length === 1 && JSON.stringify(d.components).includes(placed[0][0]);
  record('placed heads: the drop is the economy head with that ID (on the ground, or already picked up)', dropped.some(hasId) || a.inventory.items().some(hasId));
  // explosion
  const again = a.inventory.items().find(isEcon);
  if (again) { await a.equip(again, 'hand'); await sleep(300); }
  try { await a.placeBlock(below, new Vec3(0, 1, 0)); } catch (e) { /* see above */ }
  await sleep(1000);
  const placed2 = db(`SELECT head_id FROM placed_heads`);
  serverConsole(`tp Alpha ~ ~ ~12`);
  await sleep(800);
  serverConsole(`summon tnt ${placedAt.x} ${placedAt.y + 1} ${placedAt.z}`);
  await sleep(6000);
  const exp = placed2.length ? audit().match(new RegExp(`HEAD_BREAK head=(${placed2[0][0]}) .*?cause=explosion`)) : null;
  record('placed heads: an explosion drops the economy head (HEAD_BREAK cause=explosion)', placed2.length === 1 && !!exp && exp[1] === placed2[0][0],
    exp ? exp[1] : 'none');
  record('placed heads: the index is empty again', db(`SELECT COUNT(*) FROM placed_heads`)[0][0] === 0);

  // --- offline delivery on rejoin ----------------------------------------------------------------
  b.quit();
  await sleep(1500);
  serverConsole('hca run Bravo timed-test');   // needs Bravo online: waits for his join
  await sleep(500);
  await consoleOut('hca cycle end confirm', 3000);
  b = await makeBot('Bravo');
  await waitFor(() => b.chatLog.some((m) => /is running/.test(m)), 8000);
  await sleep(1500);
  const iReport = b.chatLog.findIndex((m) => /Your cycle \d+ report|not active in cycle/.test(m));
  const iCycle = b.chatLog.findIndex((m) => /Cycle \d+ is running/.test(m));
  record('hooks: an offline player gets the end-of-cycle report on rejoin, before the first-join-cycle message', iReport >= 0 && iCycle >= 0 && iReport < iCycle,
    `report@${iReport} cycle@${iCycle}`);
  record('actions: a timed action for an offline player applies on join', b.chatLog.some((m) => /TIMED START/.test(m)));
  const eff = await consoleOut('hca effects Bravo');
  record('actions: and is tracked while active', /slowness/.test(eff));

  a.quit();
  b.quit();
  await sleep(800);
  process.exit(L.summary() ? 1 : 0);
})().catch((e) => { console.log('HARNESS ERROR', e); process.exit(2); });
