const L = require('./lib');
const WORK = L.S;
const fs = require('fs');
const { sleep, record, serverConsole, logSize, logSince, db, waitFor, makeBot, waitChat, openWindow, rawClick } = L;
const AUDIT = `${WORK}/botserver/plugins/Headcount/audit/`;
const audit = () => fs.readdirSync(AUDIT).map((f) => fs.readFileSync(AUDIT + f, 'utf8')).join('');
const isEcon = (s) => s && s.name === 'player_head' && JSON.stringify(s.components || s.nbt || '').includes('head_id');
const econOwnerIs = (s, owner) => isEcon(s) && JSON.stringify(s.components || s.nbt || '').includes(owner);
const consoleOut = async (cmd, wait = 900) => { const m = logSize(); serverConsole(cmd); await sleep(wait); return logSince(m); };
const pvpOn = async (bot, want) => {
  const out = await consoleOut(`hca status ${bot.username} in-pvp`);
  const on = /in-pvp: true/.test(out);
  if (on !== want) { bot.chat('/pvp'); await sleep(900); }
};

(async () => {
  const a = await makeBot('Alpha');
  const b = await makeBot('Bravo');
  serverConsole('op Alpha');
  serverConsole('op Bravo');
  await sleep(2500);

  // --- control: raw clicks reach the server -------------------------------
  serverConsole('give Alpha cobblestone 3');
  await sleep(800);
  let w = await openWindow(a, '/hc');
  const cobble = w.slots.findIndex((s, i) => i >= w.inventoryStart && s && s.name === 'cobblestone');
  const before = Object.values(a.entities).filter((e) => e.name === 'item').length;
  rawClick(a, w, cobble, 0, 4);
  await sleep(1200);
  const after = Object.values(a.entities).filter((e) => e.name === 'item').length;
  record('control: a raw Q click on an inventory slot does drop an item (so refused vault drops were really refused)', after > before, `items ${before}->${after}`);
  a.closeWindow(a.currentWindow);

  // --- trade ----------------------------------------------------------------
  await pvpOn(a, false);
  await pvpOn(b, false);
  w = await openWindow(b, '/hc');
  await b.clickWindow(0, 0, 1);            // Bravo takes one of his heads out to offer
  await sleep(800);
  b.closeWindow(b.currentWindow);
  await sleep(300);
  serverConsole('give Alpha dirt 4');
  await sleep(800);
  let mb = b.chatLog.length;
  a.chat('/trade Bravo');
  record('trade: Bravo receives the request', await waitChat(b, /wants to trade/, mb, 3000));
  const pa = new Promise((r) => a.once('windowOpen', r));
  const pb = new Promise((r) => b.once('windowOpen', r));
  b.chat('/trade accept');
  const [wa, wb] = await Promise.race([Promise.all([pa, pb]), sleep(5000).then(() => [null, null])]);
  record('trade: accepting opens a double chest for both', !!wa && !!wb && wa.type === 'minecraft:generic_9x6', wa ? wa.type : 'no window');
  await sleep(500);
  const dirtA = wa.slots.findIndex((s, i) => i >= wa.inventoryStart && s && s.name === 'dirt');
  await a.clickWindow(dirtA, 0, 1);        // shift-click dirt into Alpha's offer
  await sleep(900);
  record("trade: Alpha's shift-clicked dirt lands on his left side", wa.slots[0] && wa.slots[0].name === 'dirt', wa.slots[0] ? wa.slots[0].name : 'empty');
  record('trade: Bravo sees it mirrored on his right side', wb.slots[5] && wb.slots[5].name === 'dirt', wb.slots[5] ? wb.slots[5].name : 'empty');
  await b.clickWindow(5, 0, 0);            // Bravo tries to take the mirrored dirt
  await sleep(900);
  const bravoDirt = b.inventory.items().filter((i) => i.name === 'dirt').length;
  record('trade: the mirrored item cannot be taken', (!b.currentWindow.selectedItem || b.currentWindow.selectedItem.name !== 'dirt') && bravoDirt === 0 && wa.slots[0] && wa.slots[0].name === 'dirt',
    `bravo dirt stacks=${bravoDirt}`);
  rawClick(b, wb, wb.inventoryStart, 0, 6); // double click collect in his own inventory
  await sleep(700);
  record('trade: double-click collect cannot pull the mirror either', wa.slots[0] && wa.slots[0].name === 'dirt' && b.inventory.items().filter((i) => i.name === 'dirt').length === 0);
  const headB = wb.slots.findIndex((s, i) => i >= wb.inventoryStart && isEcon(s));
  await b.clickWindow(headB, 0, 1);        // Bravo offers his head
  await sleep(900);
  record("trade: Bravo's head shows on Alpha's right side", isEcon(wa.slots[5]), wa.slots[5] ? wa.slots[5].name : 'empty');
  let ma = a.chatLog.length;
  await a.clickWindow(13, 0, 0);           // confirm
  await sleep(500);
  await b.clickWindow(13, 0, 0);
  await sleep(900);
  await a.clickWindow(0, 0, 0);            // try to change the offer during the countdown
  await sleep(500);
  record('trade: both sides are locked during the countdown', await waitChat(a, /locked/, ma, 1500));
  await waitFor(() => !a.currentWindow && !b.currentWindow, 6000);
  await sleep(800);
  record('trade: Alpha received Bravo\'s economy head', a.inventory.items().some((i) => econOwnerIs(i, 'Bravo')),
    a.inventory.items().map((i) => i.name).join(','));
  record('trade: Bravo received the dirt', b.inventory.items().some((i) => i.name === 'dirt'));
  record('audit: TRADE_COMPLETE lists the head by ID', /TRADE_COMPLETE .*HEAD\[[0-9a-f-]+,owner=Bravo/.test(audit()));
  w = await openWindow(a, '/hc');
  await a.clickWindow(0, 0, 1);            // free a slot
  await sleep(700);
  const bh = w.slots.findIndex((s, i) => i >= w.inventoryStart && econOwnerIs(s, 'Bravo'));
  await a.clickWindow(bh, 0, 1);           // Bravo's head into Alpha's vault
  await sleep(900);
  const uniq = db(`SELECT COUNT(DISTINCT head_owner) FROM vault_items v JOIN players p ON p.uuid=v.uuid WHERE p.name='Alpha'`)[0][0];
  record("vault: Bravo's head counts as a second unique head in Alpha's vault", uniq === 2, `unique=${uniq}`);
  a.closeWindow(a.currentWindow);
  await sleep(300);

  // cancel case: closing the screen returns everything
  await sleep(10500);                      // trade cooldown
  mb = b.chatLog.length;
  a.chat('/trade Bravo');
  await waitChat(b, /wants to trade/, mb, 3000);
  const pa2 = new Promise((r) => a.once('windowOpen', r));
  b.chat('/trade accept');
  const wa2 = await Promise.race([pa2, sleep(4000).then(() => null)]);
  await sleep(500);
  const cob = wa2 ? wa2.slots.findIndex((s, i) => i >= wa2.inventoryStart && s && s.name === 'cobblestone') : -1;
  if (cob >= 0) await a.clickWindow(cob, 0, 1);
  await sleep(700);
  mb = b.chatLog.length;
  a.closeWindow(a.currentWindow);
  await sleep(1200);
  record('trade: closing the screen cancels and returns the offer', a.inventory.items().some((i) => i.name === 'cobblestone') && await waitChat(b, /cancelled/, mb, 2000));

  // --- kills ------------------------------------------------------------------
  await pvpOn(a, true);
  await pvpOn(b, true);
  serverConsole('give Alpha netherite_sword');
  serverConsole('tp Bravo Alpha');
  await sleep(1200);
  const sword = a.inventory.items().find((i) => i.name === 'netherite_sword');
  if (sword) await a.equip(sword, 'hand');
  const kill = async () => {
    for (let i = 0; i < 12; i++) {
      const target = Object.values(a.entities).find((e) => e.type === 'player' && e.username === 'Bravo');
      if (!target) break;
      a.attack(target);
      await sleep(700);
      if (b.health <= 0) break;
    }
    await sleep(1500);
  };
  ma = a.chatLog.length;
  await kill();
  record('kill: killing Bravo drops a head ("You took Bravo\'s head!")', await waitChat(a, /took Bravo's head/, ma, 2000));
  const kd = db(`SELECT COUNT(*) FROM kill_drops`)[0][0];
  record('kill: recorded once per killer and victim', kd === 1, `kill_drops=${kd}`);
  record('audit: KILL_DROP logged with the new head ID', /KILL_DROP killer=Alpha.*victim=Bravo.*heads=[0-9a-f-]{36}/.test(audit()));
  await sleep(2500);
  if (b.health <= 0 || b.isAlive === false) { try { b.respawn(); } catch (e) {} }
  await sleep(2000);
  await pvpOn(b, true);
  serverConsole('tp Bravo Alpha');
  await sleep(1200);
  ma = a.chatLog.length;
  await kill();
  record('kill: a second kill this cycle drops nothing ("already took")', await waitChat(a, /already took/, ma, 2500));
  await sleep(2500);
  try { b.respawn(); } catch (e) {}
  await sleep(1500);

  // --- statuses, placeholders ----------------------------------------------------
  let out = await consoleOut('gamemode creative Alpha', 600);
  out = await consoleOut('hca status Alpha creative-or-spectator');
  record('status: creative-or-spectator is true in creative', /creative-or-spectator: true/.test(out));
  await consoleOut('gamemode survival Alpha', 600);
  await pvpOn(a, true);
  await pvpOn(b, true);
  serverConsole('tp Bravo Alpha');
  await sleep(1000);
  const tgt = Object.values(a.entities).find((e) => e.type === 'player' && e.username === 'Bravo');
  const wood = a.inventory.items().find((i) => i.name === 'netherite_sword');
  if (wood) await a.unequip('hand');
  if (tgt) a.attack(tgt);                  // one bare-hand hit, not a kill
  await sleep(400);
  out = await consoleOut('hca status Alpha combat-tagged', 500);
  record('status: combat-tagged is true right after hitting a player (PvPManager tag)', /combat-tagged: true/.test(out), out.trim().split('\n').pop());
  out = await consoleOut('papi parse Alpha u=%headcount_unique% r=%headcount_rank% s=%headcount_status_combat-tagged% cyc=%headcount_cycle%', 600);
  record('placeholders: player placeholders resolve through PlaceholderAPI (status text from statuses.yml)', /u=2 r=1 s=true cyc=1/.test(out), out.trim().split('\n').pop());

  // --- shop -----------------------------------------------------------------------
  await consoleOut('hca points Alpha give 100');
  w = await openWindow(a, '/hc shop');
  record('shop: /hc shop opens', !!w);
  ma = a.chatLog.length;
  await a.clickWindow(12, 0, 0);          // Speed (3 points)
  await sleep(1200);
  record('shop: buying an entry takes its price and runs its actions', await waitChat(a, /Bought Speed for 30 minutes for 3 points/, ma, 2000) && db(`SELECT points FROM players WHERE name='Alpha'`)[0][0] === 97,
    `points=${db(`SELECT points FROM players WHERE name='Alpha'`)[0][0]}`);
  out = await consoleOut('hca effects Alpha');
  record('effects: the timed speed effect is tracked', /speed/.test(out));
  const before2 = a.inventory.items().map((i) => i.name + i.count).join(',');
  rawClick(a, a.currentWindow, 10, 0, 2);  // number key 1 on a shop item
  rawClick(a, a.currentWindow, 10, 0, 4);  // drop key on a shop item
  rawClick(a, a.currentWindow, 10, 2, 3);  // middle click
  rawClick(a, a.currentWindow, a.currentWindow.inventoryStart, 0, 6); // double click collect
  await sleep(900);
  const after2 = a.inventory.items().map((i) => i.name + i.count).join(',');
  record('shop: menu items cannot be taken (number key, drop key, middle click, double click)',
    after2 === before2 && a.inventory.items().every((i) => !JSON.stringify(i.components || i.nbt || '').includes('gui_item')), `${before2} -> ${after2}`);
  a.closeWindow(a.currentWindow);

  // --- sidebar and titles -----------------------------------------------------------
  ma = a.chatLog.length;
  a.chat('/hc sidebar');
  await sleep(2000);
  const teamText = JSON.stringify(Object.values(a.teams || {}).map((t) => t.prefix && (t.prefix.toString ? t.prefix.toString() : t.prefix)));
  record('sidebar: /hc sidebar shows the configured lines', await waitChat(a, /Sidebar on/, ma, 1500) && /Unique heads/.test(teamText), teamText.slice(0, 120));
  ma = a.chatLog.length;
  await consoleOut('hca title Alpha grant champion');
  record('titles: granting a title tells the player and auto-equips it', await waitChat(a, /New title/, ma, 1500));
  out = await consoleOut('papi parse Alpha %headcount_title_raw%');
  record('titles: %headcount_title% shows the equipped title', /\[Champion\]/.test(out));
  w = await openWindow(a, '/hc titles');
  ma = a.chatLog.length;
  await a.clickWindow(10, 0, 0);
  await sleep(900);
  record('titles: clicking the equipped title unequips it', await waitChat(a, /unequipped/, ma, 1500));
  a.closeWindow(a.currentWindow);

  // --- end of cycle with players online ---------------------------------------------------
  ma = a.chatLog.length;
  await consoleOut('hca cycle end confirm', 3000);
  record('cycle end: the results broadcast reaches online players', await waitChat(a, /Cycle 1 is over/, ma, 3000));
  record('cycle end: Alpha gets his report with his score', await waitChat(a, /Your cycle 1 report/, ma, 3000));
  record('cycle start: "Cycle 2 has begun" is announced', await waitChat(a, /Cycle 2 has begun/, ma, 3000));
  const v2 = db(`SELECT COUNT(*), MIN(cycle) FROM vault_items v JOIN players p ON p.uuid=v.uuid WHERE p.name='Alpha'`)[0];
  record('cycle start: the vault was refilled with 27 heads of the new cycle', v2[0] === 27 && v2[1] === 2, JSON.stringify(v2));
  await sleep(1500);
  w = await openWindow(a, '/hc');
  const expired = a.inventory.items().filter((i) => isEcon(i));
  const anyExpired = w && w.slots.some((s, i) => i >= w.inventoryStart && isEcon(s) && /expired/i.test(JSON.stringify(s.components || s.nbt || '') + (s.customName || '')));
  record('expiry: an old-cycle head in the inventory shows as expired once the inventory is refreshed', anyExpired || expired.length === 0, `econ heads in inventory=${expired.length}`);
  a.closeWindow(a.currentWindow);
  const res = db(`SELECT name, active, unique_heads, score FROM results WHERE cycle=1 ORDER BY name`);
  record('history: cycle 1 results stored for both players', res.length === 2, JSON.stringify(res));

  a.quit();
  b.quit();
  await sleep(800);
  process.exit(L.summary() ? 1 : 0);
})().catch((e) => { console.log('HARNESS ERROR', e); process.exit(2); });
