// Kills (head protection, extra heads, bounties, keepInventory) and trade rules: request expiry, cooldown, limits,
// blocked items, own heads only, trade types, range and world, and the remaining cancel cases with returns.
const L = require('./lib');
const WORK = L.S;
const fs = require('fs');
const { sleep, record, serverConsole, logSize, logSince, db, waitFor, makeBot, waitChat, openWindow } = L;
const S = `${WORK}`;
const HC = S + '/botserver/plugins/Headcount/';
const AUDIT = HC + 'audit/';
const auditPlain = () => fs.readdirSync(AUDIT).filter((f) => f.endsWith('.log')).map((f) => fs.readFileSync(AUDIT + f, 'utf8')).join('');
const consoleOut = async (cmd, wait = 900) => { const m = logSize(); serverConsole(cmd); await sleep(wait); return logSince(m); };
const isEcon = (s) => s && s.name === 'player_head' && JSON.stringify(s.components || s.nbt || '').includes('head_id');
const pts = (n) => db(`SELECT points FROM players WHERE name='${n}'`)[0][0];
const count = (bot, name) => bot.inventory.items().filter((i) => i.name === name).reduce((n, i) => n + i.count, 0);
const edit = (file, pairs) => {
  let s = fs.readFileSync(HC + file, 'utf8');
  for (const [a, b] of pairs) {
    if (!s.includes(a)) throw new Error(`${file}: "${a}" not found`);
    s = s.replace(a, b);
  }
  fs.writeFileSync(HC + file, s);
};
const reload = () => consoleOut('hca reload', 2500);
// From the console: a player's own /pvp is held to PvPManager's toggle cooldown, which the quick on/off here hits.
const setPvP = async (bot, want) => {
  await consoleOut(`pvp ${bot.username} ${want ? 'on' : 'off'}`, 900);
  if (!want) await consoleOut(`untag ${bot.username}`, 300);   // a combat tag also blocks trades
};
const kill = async (a, b) => {
  serverConsole(`tp ${b.username} ${a.username}`);
  await sleep(1200);
  for (let i = 0; i < 16 && b.health > 0; i++) {
    const t = Object.values(a.entities).find((e) => e.type === 'player' && e.username === b.username);
    if (t) a.attack(t);
    await sleep(650);
  }
  await sleep(1500);
};
const respawn = async (b) => { try { b.respawn(); } catch (e) { /* already alive */ } await sleep(4500); };   // past PvPManager's 3 s respawn protection
const lastKill = () => (auditPlain().match(/KILL_DROP [^\n]*/g) || []).pop() || '';

// Opens a trade between a and b; returns [windowA, windowB] or nulls.
const openTrade = async (a, b) => {
  const mb = b.chatLog.length;
  a.chat('/trade ' + b.username);
  if (!await waitChat(b, /wants to trade/, mb, 3000)) return [null, null];
  const pa = new Promise((r) => a.once('windowOpen', r));
  const pb = new Promise((r) => b.once('windowOpen', r));
  b.chat('/trade accept');
  const r = await Promise.race([Promise.all([pa, pb]), sleep(4000).then(() => [null, null])]);
  await sleep(700);
  return r;
};
const offer = async (bot, pred) => {            // shift-clicks the first matching inventory stack into the offer
  const w = bot.currentWindow;
  const i = w ? w.slots.findIndex((s, n) => n >= w.inventoryStart && s && pred(s)) : -1;
  if (i >= 0) await bot.clickWindow(i, 0, 1);
  await sleep(700);
  return i >= 0;
};
const closeAll = async (...bots) => { for (const b of bots) if (b.currentWindow) b.closeWindow(b.currentWindow); await sleep(1200); };

(async () => {
  // --- setup ---------------------------------------------------------------------------------------------
  let acts = fs.readFileSync(HC + 'actions.yml', 'utf8');
  if (!acts.includes('t-protect:')) {
    acts = acts.replace('lists:\n', `lists:
  t-protect:
    - type: head-protection
      charges: 1
      duration: cycle
  t-extra:
    - type: extra-heads
      amount: 2
      charges: 1
      duration: cycle
  t-bounty:
    - type: bounty
      points: 5
      claims: 1
      duration: cycle
`);
    fs.writeFileSync(HC + 'actions.yml', acts);
  }
  edit('trade.yml', [['  timeout: 30s', '  timeout: 3s'], ['trade.cooldown).\n  duration: 10s', 'trade.cooldown).\n  duration: 4s']]);
  await reload();
  await consoleOut('hca cycle end confirm', 4000);     // a new cycle: no kill claims yet
  const a = await makeBot('Alpha');
  let b = await makeBot('Bravo');
  const k = await makeBot('India');
  await sleep(3000);
  for (const bot of [a, b, k]) serverConsole(`minecraft:clear ${bot.username}`);
  await sleep(500);
  serverConsole('minecraft:give Alpha netherite_sword');
  serverConsole('minecraft:give India netherite_sword');
  await sleep(1000);
  for (const bot of [a, k]) {
    const sw = bot.inventory.items().find((i) => i.name === 'netherite_sword');
    if (sw) await bot.equip(sw, 'hand');
  }
  for (const bot of [a, b, k]) await setPvP(bot, true);

  // --- kills ----------------------------------------------------------------------------------------------
  serverConsole('hca run Bravo t-protect');
  await sleep(800);
  let ma = a.chatLog.length;
  await kill(a, b);
  record('kills: a protected victim drops no head ("head is protected"), audited as head-protected',
    await waitChat(a, /head is protected/, ma, 2000) && /victim=Bravo.*result=head-protected/.test(lastKill()), lastKill().slice(0, 140));
  const cyc = db(`SELECT id FROM cycles WHERE state='active'`)[0][0];
  record('kills: a protected kill does not use up the once-per-cycle claim', db(`SELECT COUNT(*) FROM kill_drops WHERE cycle=${cyc}`)[0][0] === 0);
  await respawn(b);
  await setPvP(b, true);

  serverConsole('hca run Bravo t-extra');
  await sleep(800);
  ma = a.chatLog.length;
  await kill(a, b);
  const extra = lastKill();
  const heads = ((/heads=(\S+)/.exec(extra) || [])[1] || '').split(',').filter(Boolean);
  record('kills: extra-heads adds to the drop (1 + 2 = 3 heads, each a new ID)', await waitChat(a, /took Bravo's head/, ma, 2000) && heads.length === 3 && new Set(heads).size === 3,
    `heads=${heads.length}`);
  record('kills: the protection charge was used up (it ended)', /ACTION_REVERT[^\n]*type=head-protection[^\n]*reason=used-up/.test(auditPlain()));
  await respawn(b);
  await setPvP(b, true);

  let mb = b.chatLog.length;
  serverConsole('hca run Bravo t-bounty');
  record('bounty: placing it is announced', await waitChat(b, /bounty of 5 points is on Bravo/, mb, 2000));
  const before = pts('Alpha');
  ma = a.chatLog.length;
  mb = b.chatLog.length;
  await kill(a, b);
  record('bounty: it pays the killer even when the head was already taken this cycle', await waitChat(a, /claimed the bounty on Bravo/, ma, 2000) &&
    pts('Alpha') === before + 5 && a.chatLog.slice(ma).some((m) => /already took/.test(m)), `points ${before}->${pts('Alpha')}`);
  record('bounty: the claim is announced to others', await waitChat(b, /Alpha claimed the bounty on Bravo/, mb, 2000));
  await respawn(b);
  await setPvP(b, true);

  let gr = await consoleOut('gamerule keep_inventory true');
  if (/Unknown|Incorrect|expected/i.test(gr)) gr = await consoleOut('gamerule keepInventory true');
  serverConsole('minecraft:give Bravo dirt 3');
  await sleep(800);
  ma = k.chatLog.length;
  await kill(k, b);
  record('kills: with keepInventory on, the kill still drops the head (and Bravo keeps his items)', await waitChat(k, /took Bravo's head/, ma, 2500) &&
    /killer=India/.test(lastKill()), `${gr.trim().split('\n').pop().slice(0, 80)}`);
  await respawn(b);
  record('kills: keepInventory kept the victim\'s own items', count(b, 'dirt') === 3, `dirt=${count(b, 'dirt')}`);
  serverConsole('gamerule keep_inventory false');
  serverConsole('gamerule keepInventory false');
  for (const bot of [a, b, k]) await setPvP(bot, false);
  serverConsole('minecraft:clear Bravo');
  await sleep(600);

  // --- trade requests: expiry and cooldown -------------------------------------------------------------
  ma = a.chatLog.length;
  mb = b.chatLog.length;
  a.chat('/trade Bravo');
  await sleep(4500);
  const exA = a.chatLog.slice(ma).some((m) => /request to Bravo expired/.test(m));
  const exB = b.chatLog.slice(mb).some((m) => /request from Alpha expired/.test(m));
  mb = b.chatLog.length;
  b.chat('/trade accept');
  record('trade: a request expires after request.timeout and both are told', exA && exB && await waitChat(b, /no trade request/, mb, 2000));

  serverConsole('minecraft:give Alpha dirt 2');
  await sleep(800);
  let [wa, wb] = await openTrade(a, b);
  await offer(a, (s) => s.name === 'dirt');
  await a.clickWindow(13, 0, 0);            // confirm
  await sleep(400);
  await b.clickWindow(13, 0, 0);
  await sleep(4500);                         // 3 s countdown
  record('trade: a gift (one side empty) completes', count(b, 'dirt') === 2, `Bravo dirt=${count(b, 'dirt')}`);
  ma = a.chatLog.length;
  a.chat('/trade Bravo');
  record('trade: the cooldown blocks a new request right after a trade', await waitChat(a, /can trade again in/, ma, 2000));
  await sleep(4500);

  // --- limits -------------------------------------------------------------------------------------------
  edit('trade.yml', [['  max-items: 24', '  max-items: 1'], ['  max-heads: 24', '  max-heads: 1'], ['  blocked-items: []', '  blocked-items: [DIAMOND]']]);
  await reload();
  serverConsole('minecraft:clear Alpha');           // drop the heads picked up from the kills
  await sleep(500);
  serverConsole('minecraft:give Alpha cobblestone 1');
  serverConsole('minecraft:give Alpha stone 1');
  serverConsole('minecraft:give Alpha diamond 1');
  serverConsole('hca giveheads Alpha 2 self current inventory');
  await sleep(1200);
  [wa, wb] = await openTrade(a, b);
  ma = a.chatLog.length;
  await offer(a, (s) => s.name === 'diamond');
  record('trade: a blocked item (blocked-items: [DIAMOND]) cannot be offered', await waitChat(a, /can't be traded/, ma, 1500) && count(a, 'diamond') === 1);
  await offer(a, (s) => s.name === 'cobblestone');
  ma = a.chatLog.length;
  await offer(a, (s) => s.name === 'stone');
  record('trade: max-items: 1 refuses a second item stack', await waitChat(a, /At most 1 item stacks/, ma, 1500) && count(a, 'stone') === 1);
  const ownHead = (s) => isEcon(s) && !/Bravo/.test(JSON.stringify(s.components || ''));
  await offer(a, ownHead);
  ma = a.chatLog.length;
  await offer(a, ownHead);
  record('trade: max-heads: 1 refuses a second head', await waitChat(a, /At most 1 heads/, ma, 1500));
  await closeAll(a, b);
  record('trade: closing returns the accepted part of the offer', count(a, 'cobblestone') === 1 && a.inventory.items().filter(ownHead).length === 2);
  edit('trade.yml', [['  max-items: 1', '  max-items: 24'], ['  max-heads: 1', '  max-heads: 24'], ['  blocked-items: [DIAMOND]', '  blocked-items: []'],
    ['  only-own-heads: false', '  only-own-heads: true'], ['  items-for-items: true', '  items-for-items: false']]);
  await reload();
  serverConsole('hca giveheads Alpha 1 Bravo current inventory');
  serverConsole('minecraft:give Bravo stone 1');
  await sleep(1200);
  [wa, wb] = await openTrade(a, b);
  ma = a.chatLog.length;
  const bravoHead = (s) => isEcon(s) && /Bravo/.test(JSON.stringify(s.components || ''));
  await offer(a, bravoHead);
  record('trade: only-own-heads refuses someone else\'s head', await waitChat(a, /only trade your own head/, ma, 1500) && a.inventory.items().filter(bravoHead).length === 1);
  await offer(a, (s) => s.name === 'cobblestone');
  await offer(b, (s) => s.name === 'stone');
  ma = a.chatLog.length;
  await a.clickWindow(13, 0, 0);
  await sleep(400);
  await b.clickWindow(13, 0, 0);
  await sleep(1200);
  record('trade: a disallowed trade type (items-for-items: false) cannot be confirmed', await waitChat(a, /Items for items trades are not allowed/, ma, 2000) &&
    count(b, 'cobblestone') === 0, a.chatLog.slice(ma).join(' | ').slice(0, 160));
  await closeAll(a, b);
  edit('trade.yml', [['  only-own-heads: true', '  only-own-heads: false'], ['  items-for-items: false', '  items-for-items: true'],
    ['  blocks: -1', '  blocks: 10'], ['  on-teleport: true', '  on-teleport: false']]);
  await reload();

  // --- range and world ------------------------------------------------------------------------------------
  serverConsole('minecraft:tp Bravo Alpha');
  await sleep(800);
  serverConsole('execute as Bravo at @s run minecraft:tp @s ~30 ~ ~');
  await sleep(1200);
  ma = a.chatLog.length;
  a.chat('/trade Bravo');
  record('trade: a request beyond range.blocks is refused', await waitChat(a, /too far away \(max 10 blocks\)/, ma, 2000));
  serverConsole('minecraft:tp Bravo Alpha');
  await sleep(1200);
  [wa, wb] = await openTrade(a, b);
  await offer(a, (s) => s.name === 'cobblestone');
  ma = a.chatLog.length;
  serverConsole('execute as Bravo at @s run minecraft:tp @s ~30 ~ ~');
  record('trade: moving out of range during a trade cancels it and returns the offer', await waitChat(a, /out of range/, ma, 3000) &&
    await waitFor(() => count(a, 'cobblestone') === 1, 2000));
  await closeAll(a, b);
  serverConsole('execute in minecraft:the_nether run minecraft:tp Bravo 0 100 0');
  await sleep(2500);
  ma = a.chatLog.length;
  a.chat('/trade Bravo');
  record('trade: a request to another world is refused', await waitChat(a, /is in another world/, ma, 2000));
  edit('trade.yml', [['  blocks: 10', '  blocks: -1']]);
  await reload();
  serverConsole('execute in minecraft:overworld run minecraft:tp Bravo Alpha');
  await sleep(2500);
  [wa, wb] = await openTrade(a, b);
  await offer(a, (s) => s.name === 'cobblestone');
  ma = a.chatLog.length;
  serverConsole('execute in minecraft:the_nether run minecraft:tp Bravo 0 100 0');
  record('trade: changing worlds during a trade cancels it', await waitChat(a, /changed worlds/, ma, 3000) && await waitFor(() => count(a, 'cobblestone') === 1, 2000));
  await closeAll(a, b);
  serverConsole('execute in minecraft:overworld run minecraft:tp Bravo Alpha');
  await sleep(2500);

  // --- cancel cases with offers on both sides --------------------------------------------------------------
  serverConsole('minecraft:give Bravo stone 1');
  await sleep(800);
  const both = async () => {
    const r = await openTrade(a, b);
    await offer(a, (s) => s.name === 'cobblestone');
    await offer(b, (s) => s.name === 'stone');
    return r;
  };
  await both();
  ma = a.chatLog.length;
  await consoleOut('gamemode creative Bravo', 1500);
  record('trade: a gamemode change cancels the trade; both offers come back', await waitChat(a, /gamemode changed|is creative/, ma, 2000) &&
    count(a, 'cobblestone') === 1 && count(b, 'stone') >= 1, a.chatLog.slice(ma).join(' | ').slice(0, 120));
  await closeAll(a, b);
  serverConsole('gamemode survival Bravo');
  await sleep(800);

  await both();
  ma = a.chatLog.length;
  serverConsole('minecraft:kick Bravo testing');   // AdvancedBan's /kick refuses to kick an operator
  record('trade: a kick cancels the trade and returns Alpha\'s offer', await waitChat(a, /someone was kicked|disconnected/, ma, 3000) && count(a, 'cobblestone') === 1);
  await closeAll(a);
  b = await makeBot('Bravo');
  await sleep(2500);
  record('trade: the kicked player has their offer back after rejoining', count(b, 'stone') >= 1, `stone=${count(b, 'stone')}`);
  serverConsole('minecraft:tp Bravo Alpha');
  await sleep(1000);

  await both();
  ma = a.chatLog.length;
  serverConsole('kill Bravo');
  await sleep(1500);
  const groundStone = Object.values(a.entities).filter((e) => e.name === 'item' && e.getDroppedItem && (e.getDroppedItem() || {}).name === 'stone').length;
  record('trade: a death cancels the trade; Alpha gets his offer back and Bravo\'s offer drops with his items', await waitChat(a, /someone died/, ma, 2000) &&
    count(a, 'cobblestone') === 1 && groundStone >= 1, `stone on ground=${groundStone}`);
  await closeAll(a);
  await respawn(b);
  serverConsole('minecraft:kill @e[type=item]');
  serverConsole('minecraft:give Bravo stone 1');
  serverConsole('minecraft:tp Bravo Alpha');
  await sleep(1200);

  await both();
  ma = a.chatLog.length;
  serverConsole('minecraft:ban Bravo testing');
  record('trade: a ban (which kicks) cancels the trade and returns Alpha\'s offer', await waitChat(a, /someone was kicked|disconnected/, ma, 3000) && count(a, 'cobblestone') === 1);
  await closeAll(a);
  serverConsole('minecraft:pardon Bravo');
  await sleep(1000);
  b = await makeBot('Bravo');
  await sleep(2500);
  record('trade: the banned player\'s offer was returned (seen after the ban is lifted)', count(b, 'stone') >= 1, `stone=${count(b, 'stone')}`);
  serverConsole('minecraft:tp Bravo Alpha');
  await sleep(1000);

  // --- overflow: returns that don't fit are dropped at the player's feet ---------------------------------
  [wa, wb] = await openTrade(a, b);
  await offer(a, (s) => s.name === 'cobblestone');
  const free = a.inventory.emptySlotCount();
  serverConsole(`minecraft:give Alpha stone ${64 * 36}`);   // fills every free slot; the rest drops
  serverConsole('execute as Bravo at @s run minecraft:tp @s ~6 ~ ~');   // so Bravo can't pick the drop up
  await sleep(1500);
  serverConsole('minecraft:kill @e[type=item]');            // the stone /give dropped
  await sleep(500);
  ma = a.chatLog.length;
  await closeAll(a, b);
  await sleep(800);
  const groundCob = Object.values(a.entities).filter((e) => e.name === 'item' && e.getDroppedItem && (e.getDroppedItem() || {}).name === 'cobblestone').length;
  record('trade: a returned item that no longer fits is dropped at the player\'s feet, with a message', await waitChat(a, /inventory was full: 1 stack/, ma, 2000) && groundCob === 1,
    `free slots before=${free} cobble on ground=${groundCob}`);
  serverConsole('minecraft:clear Alpha stone');
  serverConsole('minecraft:kill @e[type=item]');
  edit('trade.yml', [['  timeout: 3s', '  timeout: 30s'], ['trade.cooldown).\n  duration: 4s', 'trade.cooldown).\n  duration: 10s'], ['  on-teleport: false', '  on-teleport: true']]);
  await reload();

  a.quit(); b.quit(); k.quit();
  await sleep(800);
  process.exit(L.summary() ? 1 : 0);
})().catch((e) => { console.log('HARNESS ERROR', e); process.exit(2); });
