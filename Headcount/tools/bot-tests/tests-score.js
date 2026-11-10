// The score formula on a live player: variables, a numeric PlaceholderAPI placeholder, the rounding modes,
// score bonuses added after the formula, and playtime and activity tracking.
const L = require('./lib');
const WORK = L.S;
const fs = require('fs');
const { sleep, record, serverConsole, logSize, logSince, db, makeBot, waitChat } = L;
const HC = `${WORK}/botserver/plugins/Headcount/`;
const consoleOut = async (cmd, wait = 700) => { const m = logSize(); serverConsole(cmd); await sleep(wait); return logSince(m); };
const N = process.argv[2] || 'Formula';
(async () => {
  const sBackup = fs.readFileSync(HC + 'scoring.yml', 'utf8');
  const cBackup = fs.readFileSync(HC + 'config.yml', 'utf8');
  const setFormula = async (f, rounding = 'floor') => {
    fs.writeFileSync(HC + 'scoring.yml', sBackup.replace(/^formula: .*$/m, `formula: "${f}"`).replace(/^rounding: .*$/m, `rounding: ${rounding}`));
    await consoleOut('hca reload', 2500);
  };
  // activity: a 15 s minimum playtime for this test
  fs.writeFileSync(HC + 'config.yml', cBackup.replace(/^  min-playtime: .*$/m, '  min-playtime: 15s').replace(/^  check-interval: .*$/m, '  check-interval: 5s'));
  await consoleOut('hca reload', 2500);
  await consoleOut('hca cycle end confirm', 4500);
  const p = await makeBot(N);
  await sleep(2500);
  const cyc = db(`SELECT id FROM cycles WHERE state='active'`)[0][0];
  const uuid = db(`SELECT uuid FROM players WHERE name='${N}'`)[0][0];
  const stats = () => db(`SELECT active, playtime_ms FROM cycle_players WHERE cycle=${cyc} AND uuid='${uuid}'`)[0] || [null, null];
  record('activity: not active before min-playtime (15 s)', stats()[0] === 0);
  const mp = p.chatLog.length;
  await L.waitFor(() => stats()[0] === 1, 25000, 1000);
  record('activity: active after 15 s online (checked every 5 s), and the became-active hook ran', stats()[0] === 1 && p.chatLog.slice(mp).some((m) => /HOOK became-active/.test(m)), JSON.stringify(stats()));
  fs.writeFileSync(HC + 'config.yml', cBackup);
  await consoleOut('hca reload', 2500);

  const score = async () => { const m = p.chatLog.length; p.chat('/hc score'); await waitChat(p, /If the cycle ended now/, m, 2500); return Number((p.chatLog.slice(m).join(' ').match(/now: score (-?\d+)/) || [])[1]); };
  serverConsole(`hca points ${N} set 9`);
  await sleep(600);
  await setFormula('total * 1000 + own * 100 + points');
  record('formula: total, own and points variables (27 heads, all own, 9 points)', (await score()) === 27 * 1000 + 27 * 100 + 9);
  await setFormula('vault_size * 10 + starting_heads + is_active * 1000 + kills + deaths + trades');
  const sc = await score();
  record('formula: vault_size, starting_heads, is_active, kills, deaths, trades', sc === 270 + 27 + 1000, String(sc));
  await setFormula('%headcount_points% * 3');
  record('formula: a numeric PlaceholderAPI placeholder (%headcount_points% = 9)', (await score()) === 27);
  await setFormula('%no_such_placeholder% + 5');
  record('formula: a placeholder that isn\'t a number counts as 0', (await score()) === 5);
  for (const [mode, want9, want10] of [['floor', 2, 2], ['ceil', 3, 3], ['round', 2, 3]]) {
    await setFormula('points / 4', mode);
    serverConsole(`hca points ${N} set 9`);
    await sleep(500);
    const a9 = await score();
    serverConsole(`hca points ${N} set 10`);
    await sleep(500);
    const a10 = await score();
    record(`rounding: ${mode} (9/4 = 2.25 -> ${want9}, 10/4 = 2.5 -> ${want10})`, a9 === want9 && a10 === want10, `${a9}, ${a10}`);
  }
  await setFormula('points / 4');
  serverConsole(`hca run ${N} ts-score`);    // score-bonus +25, added after the formula
  await sleep(700);
  record('formula: a score-bonus is added after the formula', (await score()) === 2 + 25);
  const play = stats()[1];
  p.quit();
  await sleep(1500);
  const after = stats()[1];
  record('playtime: online time is stored when the player leaves', after >= 20000 && after > play, `${play} -> ${after} ms`);
  fs.writeFileSync(HC + 'scoring.yml', sBackup);
  await consoleOut('hca reload', 2500);
  await sleep(500);
  process.exit(L.summary() ? 1 : 0);
})().catch((e) => { console.log('HARNESS ERROR', e); process.exit(2); });
