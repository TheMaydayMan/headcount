// Phase 2 of crash-take: after the restart, Alpha rejoins; count the cobblestone.
const L = require('./lib');
const { sleep, makeBot } = L;
(async () => {
  const a = await makeBot('Alpha');
  await sleep(2500);
  const c = () => a.inventory.items().filter((i) => i.name === 'cobblestone').reduce((n, i) => n + i.count, 0);
  console.log('on rejoin: cobble =', c());
  await sleep(24000);   // past the 20 s action, which returns the 5
  console.log(c() === 5 ? 'PASS' : 'FAIL', ' crash: take-item gave the items back exactly once | after the action ended: cobble =', c());
  a.quit(); await sleep(500); process.exit(0);
})();
