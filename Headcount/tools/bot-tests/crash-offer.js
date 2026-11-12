// Phase 1: Alpha offers 5 cobblestone in an open trade with Bravo, then the server is killed from outside.
const L = require('./lib');
const WORK = L.S;
const { sleep, serverConsole, makeBot, waitChat, db } = L;
(async () => {
  const a = await makeBot('Alpha');
  const b = await makeBot('Bravo');
  await sleep(2500);
  for (const n of ['Alpha', 'Bravo']) { await L.setPvp(n, false); serverConsole(`untag ${n}`); }   // PvP on or a combat tag would block the trade
  serverConsole('minecraft:clear Alpha cobblestone');
  serverConsole('minecraft:give Alpha cobblestone 5');
  await sleep(1000);
  serverConsole('save-all');                 // the saved inventory now holds the 5 cobblestone
  await sleep(2000);
  const mb = b.chatLog.length;
  a.chat('/trade Bravo');
  await waitChat(b, /wants to trade/, mb, 3000);
  const pa = new Promise((r) => a.once('windowOpen', r));
  b.chat('/trade accept');
  const w = await Promise.race([pa, sleep(4000).then(() => null)]);
  await sleep(600);
  const cob = w ? w.slots.findIndex((s, i) => i >= w.inventoryStart && s && s.name === 'cobblestone') : -1;
  if (cob >= 0) await a.clickWindow(cob, 0, 1);
  await sleep(2500);                          // past the 1 s save
  if (db('SELECT COUNT(*) FROM trade_items')[0][0] !== 1) console.log('SETUP FAILED: no offer stored');
  console.log('offer rows in trade_items:', db("SELECT COUNT(*) FROM trade_items")[0][0], '| cobble left in inventory:', a.inventory.items().filter((i) => i.name === 'cobblestone').reduce((n, i) => n + i.count, 0));
  require('fs').writeFileSync(`${WORK}/crash-ready`, 'ready');
  a.on('end', () => process.exit(0));         // stay connected, trade open, until the server is killed
  setTimeout(() => process.exit(3), 60000);
})();
