// The shop with a real player: tier locks by last cycle score, tier and page navigation, prices, the
// too-expensive / limit-reached / sold-out / cooldown / unavailable states, prerequisites and confirmation.
const L = require('./lib');
const WORK = L.S;
const fs = require('fs');
const { sleep, record, serverConsole, logSize, logSince, db, makeBot, waitChat, openWindow } = L;
const S = `${WORK}`;
const HC = S + '/botserver/plugins/Headcount/';
const consoleOut = async (cmd, wait = 900) => { const m = logSize(); serverConsole(cmd); await sleep(wait); return logSince(m); };
const N = process.argv[2] || 'Sierra';
const pts = () => db(`SELECT points FROM players WHERE name='${N}'`)[0][0];
const text = (s) => (s ? JSON.stringify(s.components || '') + (s.customName || '') : '');
const edit = (file, pairs) => {
  let s = fs.readFileSync(HC + file, 'utf8');
  for (const [a, b] of pairs) {
    if (!s.includes(a)) throw new Error(`${file}: "${a.slice(0, 40)}" not found`);
    s = s.replace(a, b);
  }
  fs.writeFileSync(HC + file, s);
};

(async () => {
  const backup = fs.readFileSync(HC + 'shop.yml', 'utf8');
  // test-only: confirmation at 20 (another suite lowers it), 2 diamond blocks in stock, and a page-2 entry
  let shop = backup.replace(/confirm-at-price: \d+/, 'confirm-at-price: 20').replace('        stock: 5\n', '        stock: 2\n');
  shop = shop.replace('      steak:\n', `      page-two:
        page: 2
        slot: 10
        price: 1
        display:
          material: APPLE
          name: "<white>Page two apple"
        actions:
          - type: give-item
            material: APPLE
      steak:
`);
  fs.writeFileSync(HC + 'shop.yml', shop);
  let acts = fs.readFileSync(HC + 'actions.yml', 'utf8');
  if (!acts.includes('ts-score:')) fs.writeFileSync(HC + 'actions.yml', acts.replace('lists:\n', 'lists:\n  ts-score:\n    - type: score-bonus\n      amount: 25\n'));
  await consoleOut('hca reload', 2500);
  const p = await makeBot(N);
  await sleep(3000);

  // --- a new player: only the first tier ------------------------------------------------------------------
  let w = await openWindow(p, '/hc shop');
  record('shop: opens on the Common tier', !!w && /Common/.test(JSON.stringify(w.title)), w && JSON.stringify(w.title).slice(0, 80));
  let o = new Promise((r) => p.once('windowOpen', r));
  await p.clickWindow(53, 0, 0);           // next tier
  w = await Promise.race([o, sleep(3000).then(() => null)]);
  await sleep(500);
  record('shop: the next-tier button opens Uncommon', !!w && /Uncommon/.test(JSON.stringify(w.title)));
  record('shop: a new player sees Uncommon locked (banner and locked entries)', !!w && /Needs a last cycle score of/.test(text(w.slots[4])) && w.slots[10] && w.slots[10].name === 'gray_dye',
    w && `${w.slots[10] && w.slots[10].name}`);
  let mp = p.chatLog.length;
  await p.clickWindow(10, 0, 0);
  record('shop: buying from a locked tier is refused', await waitChat(p, /tier needs a last cycle score of 3/, mp, 2000));
  p.closeWindow(p.currentWindow);
  await sleep(500);

  // --- a last cycle score of 25 unlocks every tier -------------------------------------------------------------
  serverConsole(`hca run ${N} ts-score`);
  await sleep(500);
  await consoleOut('hca cycle end confirm', 4500);
  // +25 from the score-bonus, minus the par for however many players are active this cycle
  record('setup: the last cycle score is at least 21', db(`SELECT last_score FROM players WHERE name='${N}'`)[0][0] >= 21);
  serverConsole(`hca points ${N} set 500`);
  await sleep(800);

  w = await openWindow(p, '/hc shop');
  record('shop: /hc shop now opens on the highest unlocked tier (Legendary)', !!w && /Legendary/.test(JSON.stringify(w.title)));

  // --- pages --------------------------------------------------------------------------------------------
  w = await openWindow(p, '/hc shop common');
  o = new Promise((r) => p.once('windowOpen', r));
  await p.clickWindow(50, 0, 0);           // next page
  w = await Promise.race([o, sleep(3000).then(() => null)]);
  await sleep(500);
  record('shop: the next-page button shows page 2 with its entry', !!w && w.slots[10] && w.slots[10].name === 'apple', w && w.slots[10] && w.slots[10].name);
  o = new Promise((r) => p.once('windowOpen', r));
  await p.clickWindow(48, 0, 0);           // previous page
  w = await Promise.race([o, sleep(3000).then(() => null)]);
  await sleep(500);
  record('shop: previous-page goes back', !!w && w.slots[10] && w.slots[10].name === 'cooked_beef');

  // --- limit per cycle (shield: limit 1, price 4) ------------------------------------------------------------
  const before = pts();
  mp = p.chatLog.length;
  await p.clickWindow(16, 0, 0);
  record('shop: a purchase takes exactly its price', await waitChat(p, /Bought .*\(-4 points/, mp, 2000) && pts() === before - 4, `${before} -> ${pts()}`);
  await sleep(1200);
  w = p.currentWindow;
  record('shop: the entry now shows limit-reached', !!w && /reached the limit of 1 /.test(text(w.slots[16])));
  mp = p.chatLog.length;
  await sleep(300);
  await p.clickWindow(16, 0, 0);
  record('shop: buying past limit-per-cycle is refused', await waitChat(p, /can't buy that again this cycle/, mp, 2000) && pts() === before - 4);
  p.closeWindow(p.currentWindow);
  await sleep(500);

  // --- tiers: Rare (stock and prerequisites) -------------------------------------------------------------------
  const toTier = (name) => openWindow(p, '/hc shop ' + name.toLowerCase());
  w = await toTier('Rare');
  record('shop: Rare is unlocked with a last score of 25', !!w && /Rare/.test(JSON.stringify(w.title)) && w.slots[16] && w.slots[16].name === 'diamond_block');
  for (let i = 0; i < 2; i++) {
    mp = p.chatLog.length;
    await p.clickWindow(16, 0, 0);
    await waitChat(p, /Bought Diamond block/, mp, 2000);
    await sleep(1200);
  }
  w = p.currentWindow;
  record('shop: global stock runs out (2 bought) and the entry shows sold out', !!w && w.slots[16] && w.slots[16].name === 'barrier' &&
    db(`SELECT * FROM shop_stock`).length >= 1, JSON.stringify(db('SELECT * FROM shop_stock')));
  mp = p.chatLog.length;
  await p.clickWindow(16, 0, 0);
  record('shop: buying a sold-out entry is refused', await waitChat(p, /sold out/, mp, 2000));
  p.closeWindow(p.currentWindow);
  await sleep(500);

  // --- Legendary: requires a title, cooldown, confirmation ------------------------------------------------------
  w = await toTier('Legendary');
  const legendSlot = w ? w.slots.findIndex((s, i) => i < 45 && s && /Legend/.test(text(s)) && !/Legendary/.test(text(s))) : -1;
  mp = p.chatLog.length;
  if (legendSlot >= 0) await p.clickWindow(legendSlot, 0, 0);
  record('shop: an entry whose requirements are not met (needs the collector title) is refused', legendSlot >= 0 && await waitChat(p, /don't meet the requirements/, mp, 2000),
    `slot=${legendSlot}`);
  p.closeWindow(p.currentWindow);
  await sleep(500);
  w = await toTier('Rare');
  mp = p.chatLog.length;
  await p.clickWindow(12, 0, 0);           // title-collector, price 8
  record('shop: buying the collector title', await waitChat(p, /Bought/, mp, 2000) && db(`SELECT COUNT(*) FROM titles t JOIN players p ON p.uuid=t.uuid WHERE p.name='${N}' AND t.title='collector'`)[0][0] === 1);
  p.closeWindow(p.currentWindow);
  await sleep(500);
  w = await toTier('Legendary');
  const before2 = pts();
  o = new Promise((r) => p.once('windowOpen', r));
  await p.clickWindow(legendSlot, 0, 0);   // price 25: above confirm-at-price 20
  const cw = await Promise.race([o, sleep(3000).then(() => null)]);
  await sleep(600);
  record('shop: now the requirement is met, and the price opens the confirmation', !!cw && cw.type === 'minecraft:generic_9x3');
  mp = p.chatLog.length;
  await p.clickWindow(11, 0, 0);           // confirm
  record('shop: confirming buys it', await waitChat(p, /Bought/, mp, 2500) && pts() === before2 - 25, `${before2} -> ${pts()}`);
  await sleep(800);
  if (p.currentWindow) p.closeWindow(p.currentWindow);
  await sleep(400);
  w = await toTier('Legendary');
  const flySlot = w.slots.findIndex((s, i) => i < 45 && s && /blessing/i.test(text(s)));   // Champion's blessing, 1-day cooldown
  mp = p.chatLog.length;
  await p.clickWindow(flySlot, 0, 0);
  await waitChat(p, /Bought/, mp, 2000);
  await sleep(1200);
  w = p.currentWindow;
  record('shop: an entry on cooldown says when it is available again', flySlot >= 0 && !!w && /Available again in/.test(text(w.slots[flySlot])), `slot=${flySlot}`);
  mp = p.chatLog.length;
  await p.clickWindow(flySlot, 0, 0);
  record('shop: buying during the cooldown is refused', await waitChat(p, /Available again in/, mp, 2000));
  p.closeWindow(p.currentWindow);
  await sleep(400);

  // --- too expensive ----------------------------------------------------------------------------------------
  serverConsole(`hca points ${N} set 1`);
  await sleep(800);
  w = await openWindow(p, '/hc shop common');
  record('shop: an entry above the balance shows too-expensive', !!w && /need|more points|Not enough|can't afford/i.test(text(w.slots[12])), text(w.slots[12]).slice(0, 120));
  mp = p.chatLog.length;
  await p.clickWindow(12, 0, 0);
  record('shop: buying it says how many points are missing', await waitChat(p, /need 1 more points/, mp, 2000));
  p.closeWindow(p.currentWindow);

  fs.writeFileSync(HC + 'shop.yml', backup);
  await consoleOut('hca reload', 2500);
  p.quit();
  await sleep(800);
  process.exit(L.summary() ? 1 : 0);
})().catch((e) => { console.log('HARNESS ERROR', e); process.exit(2); });
