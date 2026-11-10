// Outcomes at a live cycle end: most-extreme selection, a shield blocking a punishment, a rollover override,
// and the punishment's actions actually landing on the player.
const L = require('./lib');
const WORK = L.S;
const fs = require('fs');
const { sleep, record, serverConsole, logSize, logSince, db, makeBot, waitChat } = L;
const S = `${WORK}`;
const HC = S + '/botserver/plugins/Headcount/';
const consoleOut = async (cmd, wait = 900) => { const m = logSize(); serverConsole(cmd); await sleep(wait); return logSince(m); };
const X = process.argv[2] || 'Oscar', Y = process.argv[3] || 'Papa';

(async () => {
  let acts = fs.readFileSync(HC + 'actions.yml', 'utf8');
  if (!acts.includes('to-shield:')) {
    acts = acts.replace('lists:\n', `lists:
  to-shield:
    - type: shield
      charges: 1
      duration: cycle
  to-neg:
    - type: score-bonus
      amount: -10
  to-roll:
    - type: rollover
      percent: 50
`);
    fs.writeFileSync(HC + 'actions.yml', acts);
  }
  await consoleOut('hca reload', 2500);
  const x = await makeBot(X);
  const y = await makeBot(Y);
  await sleep(3000);
  for (const n of [X, Y]) {
    serverConsole(`minecraft:clear ${n}`);
    serverConsole(`hca points ${n} give 10`);
    serverConsole(`hca run ${n} to-neg`);
  }
  serverConsole(`hca run ${X} to-roll`);
  serverConsole(`hca run ${X} to-shield`);
  await sleep(1500);
  const mx = x.chatLog.length, my = y.chatLog.length;
  const cycle = db(`SELECT id FROM cycles WHERE state='active'`)[0][0];
  await consoleOut('hca cycle end confirm', 5000);
  const row = (n) => db(`SELECT score, outcomes, points_before, points_after, rolled_over, points_earned FROM results WHERE cycle=${cycle} AND name='${n}'`)[0];
  const rx = row(X), ry = row(Y);
  record('outcomes: both players scored -10 (score-bonus)', rx && ry && rx[0] === -10 && ry[0] === -10, JSON.stringify([rx, ry]));
  record('outcomes: most-extreme picks only penalty-10 for the unshielded player', ry && ry[1] === 'penalty-10', ry && ry[1]);
  record('outcomes: the punishment\'s actions ran (punish-severe message)', await waitChat(y, /Your score was -10\. You are marked/, my, 2000));
  await sleep(1500);
  const helmet = y.inventory.slots[5];
  record('outcomes: and its dunce cap was put on', !!helmet && helmet.name === 'carved_pumpkin', helmet ? helmet.name : 'nothing');
  const fx = await consoleOut(`hca effects ${Y}`);
  record('outcomes: and its bounty and glowing are tracked for the cycle', /bounty/.test(fx) && /glowing/.test(fx), fx.split('\n').filter((l) => /^\s*-|\[/.test(l)).join(' | ').slice(0, 160));
  record('shield: blocks the punishment, recorded as shielded:penalty-10', rx && rx[1] === 'shielded:penalty-10', rx && rx[1]);
  record('shield: the player is told', await waitChat(x, /Your shield blocked/, mx, 2000) && !x.chatLog.slice(mx).some((m) => /You are marked/.test(m)));
  const fxx = await consoleOut(`hca effects ${X}`);
  record('shield: its one charge is used up', !/shield/.test(fxx), fxx.split('\n').filter((l) => /shield/.test(l)).join(' | ').slice(0, 120));
  record('rollover: 50% of 10 unspent points rolled over for the player with the override', rx && rx[2] === 10 && rx[4] === 5 && rx[3] === 5, rx && `before ${rx[2]} rolled ${rx[4]} after ${rx[3]}`);
  record('rollover: the default 0% for the other player', ry && ry[4] === 0 && ry[3] === 0, ry && `rolled ${ry[4]} after ${ry[3]}`);
  record('points: the balance after the cycle end is the stored points_after', db(`SELECT points FROM players WHERE name='${X}'`)[0][0] === 5);
  // "for the whole cycle": the punishment lasts through the new cycle and ends at its end
  await consoleOut('hca cycle end confirm', 5000);
  const fx2 = await consoleOut(`hca effects ${Y}`);
  record('outcomes: the cycle-long punishment ends at the next cycle end', !/bounty|glowing/.test(fx2), fx2.split('\n').filter((l) => /^\s*-|\[/.test(l)).join(' | ').slice(0, 160));
  await sleep(1000);
  const h2 = y.inventory.slots[5];
  record('outcomes: and the dunce cap comes off', !h2 || h2.name !== 'carved_pumpkin', h2 ? h2.name : 'nothing');
  x.quit(); y.quit();
  await sleep(800);
  process.exit(L.summary() ? 1 : 0);
})().catch((e) => { console.log('HARNESS ERROR', e); process.exit(2); });
