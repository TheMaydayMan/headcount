// Phase 1: a take-item action (returned on revert) takes 5 cobblestone; the server is then killed.
const L = require('./lib');
const WORK = L.S;
const { sleep, serverConsole, makeBot } = L;
const S = `${WORK}`;
(async () => {
  const a = await makeBot('Alpha');
  await sleep(2500);
  serverConsole('minecraft:clear Alpha cobblestone');
  serverConsole('minecraft:give Alpha cobblestone 5');
  await sleep(1000);
  serverConsole('save-all');
  await sleep(2000);
  serverConsole('hca run Alpha t-take');
  await sleep(2500);
  console.log('cobble in inventory after take:', a.inventory.items().filter((i) => i.name === 'cobblestone').reduce((n, i) => n + i.count, 0));
  require('fs').writeFileSync(S + '/crash-ready', 'ready');
  a.on('end', () => process.exit(0));
  setTimeout(() => process.exit(3), 60000);
})();
