// Phase 2 of crash-offer: after the restart, Alpha rejoins; the trade offer must come back exactly once.
const L = require('./lib');
const { sleep, makeBot, waitFor } = L;
(async () => {
  const a = await makeBot('Alpha');
  await waitFor(() => a.chatLog.some((m) => /returned|recovered/i.test(m)), 6000);
  await sleep(1500);
  const n = a.inventory.items().filter((i) => i.name === 'cobblestone').reduce((x, i) => x + i.count, 0);
  console.log(n === 5 && a.chatLog.some((m) => /interrupted trade were returned/.test(m)) ? 'PASS' : 'FAIL', ' crash: the offer came back exactly once | after restart: cobblestone =', n, '| chat:', a.chatLog.filter((m) => /trade|returned|recovered/i.test(m)).join(' | '));
  a.quit(); await sleep(500); process.exit(0);
})();
